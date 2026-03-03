import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:lin_player_server_api/network/lin_http_client.dart';

const String _kImdbGraphqlHost = 'caching.graphql.imdb.com';
final Uri _kImdbGraphqlUri = Uri.https(_kImdbGraphqlHost, '/');
const int _kImdbAdvancedSearchPageSize = 50;
const String _kImdbDefaultLocale = 'en-US';

// From MoviePilot IMDbSource plugin default hash (may change over time).
const String _kAdvancedTitleSearchSha256 =
    'd32303ed2711e4d03bd5e36cfe0e5304bcffd7e31d1898695f6b6919736ff2a8';

class ImdbApiException implements Exception {
  ImdbApiException(
    this.message, {
    this.statusCode,
    this.body,
  });

  final String message;
  final int? statusCode;
  final String? body;

  @override
  String toString() {
    final code = statusCode;
    if (code == null) return message;
    return '$message (HTTP $code)';
  }
}

class ImdbRankingItem {
  const ImdbRankingItem({
    required this.id,
    required this.title,
    required this.originalTitle,
    required this.year,
    required this.posterUrl,
    required this.rating,
    required this.voteCount,
  });

  final String id;
  final String title;
  final String originalTitle;
  final int? year;
  final String? posterUrl;
  final double? rating;
  final int? voteCount;

  String get displayTitle {
    final t = title.trim();
    if (t.isNotEmpty) return t;
    return originalTitle.trim();
  }
}

class ImdbApiClient {
  ImdbApiClient({
    http.Client? client,
    Duration cacheTtl = const Duration(minutes: 30),
  })  : _client = client ?? LinHttpClientFactory.createClient(),
        _cacheTtl = cacheTtl;

  final http.Client _client;
  final Duration _cacheTtl;
  final Map<String, _CacheEntry<List<ImdbRankingItem>>> _cache = {};

  void close() => _client.close();

  void clearCache() => _cache.clear();

  Future<List<ImdbRankingItem>> top250({
    bool forceRefresh = false,
    int first = 250,
  }) {
    final fixedFirst = first.clamp(1, 250);
    return _cached(
      'top250:first=$fixedFirst',
      forceRefresh: forceRefresh,
      loader: () => _advancedTitleSearchMany(
        total: fixedFirst,
        titleTypes: const ['movie'],
        sortBy: 'USER_RATING',
        sortOrder: 'DESC',
        ranked: const ['TOP_RATED_MOVIES-250'],
      ),
    );
  }

  Future<List<ImdbRankingItem>> movieRanking({
    bool forceRefresh = false,
    int first = 250,
  }) {
    final fixedFirst = first.clamp(1, 250);
    return _cached(
      'movieRanking:first=$fixedFirst',
      forceRefresh: forceRefresh,
      loader: () => _advancedTitleSearchMany(
        total: fixedFirst,
        titleTypes: const ['movie'],
        sortBy: 'USER_RATING',
        sortOrder: 'DESC',
      ),
    );
  }

  Future<List<ImdbRankingItem>> seriesRanking({
    bool forceRefresh = false,
    int first = 250,
  }) {
    final fixedFirst = first.clamp(1, 250);
    return _cached(
      'seriesRanking:first=$fixedFirst',
      forceRefresh: forceRefresh,
      loader: () => _advancedTitleSearchMany(
        total: fixedFirst,
        titleTypes: const ['tvSeries', 'tvMiniSeries'],
        sortBy: 'USER_RATING',
        sortOrder: 'DESC',
      ),
    );
  }

  Future<List<ImdbRankingItem>> animeRanking({
    bool forceRefresh = false,
    int first = 250,
  }) {
    final fixedFirst = first.clamp(1, 250);
    return _cached(
      'animeRanking:first=$fixedFirst',
      forceRefresh: forceRefresh,
      loader: () => _advancedTitleSearchMany(
        total: fixedFirst,
        titleTypes: const ['tvSeries', 'movie'],
        sortBy: 'USER_RATING',
        sortOrder: 'DESC',
        genres: const ['Animation'],
      ),
    );
  }

  Future<List<ImdbRankingItem>> _cached(
    String key, {
    required bool forceRefresh,
    required Future<List<ImdbRankingItem>> Function() loader,
  }) async {
    final now = DateTime.now();
    final hit = _cache[key];
    if (!forceRefresh && hit != null) {
      if (now.difference(hit.timestamp) < _cacheTtl) return hit.value;
    }

    final value = await loader();
    _cache[key] = _CacheEntry(value, now);
    return value;
  }

  Map<String, String> _headers() {
    return <String, String>{
      'Accept': 'application/json',
      'Content-Type': 'application/json',
      'User-Agent': LinHttpClientFactory.userAgent,
      // Best-effort to look like a normal web request.
      'Origin': 'https://www.imdb.com',
      'Referer': 'https://www.imdb.com/',
    };
  }

  Never _throwHttp(http.Response resp) {
    throw ImdbApiException(
      'IMDb GraphQL request failed',
      statusCode: resp.statusCode,
      body: resp.body,
    );
  }

  void _ensureOk(http.Response resp) {
    final code = resp.statusCode;
    if (code < 200 || code >= 300) _throwHttp(resp);
  }

  Future<Map<String, dynamic>> _postGraphql(
    Map<String, dynamic> payload,
  ) async {
    final resp = await _client.post(
      _kImdbGraphqlUri,
      headers: _headers(),
      body: jsonEncode(payload),
    );
    _ensureOk(resp);
    final decoded = jsonDecode(resp.body);
    if (decoded is! Map) {
      throw ImdbApiException('Unexpected IMDb GraphQL response');
    }
    return decoded.cast<String, dynamic>();
  }

  static String _normalizeTitleId(String raw) {
    final v = raw.trim();
    final m = RegExp(r'(tt\d{5,})').firstMatch(v);
    return m?.group(1) ?? v;
  }

  static Map<String, dynamic>? _rankedListToConstraint(String ranked) {
    final v = ranked.trim();
    final m = RegExp(r'^(TOP_RATED_MOVIES|LOWEST_RATED_MOVIES)-(\d+)$')
        .firstMatch(v);
    if (m == null) return null;
    final type = (m.group(1) ?? '').trim();
    final max = int.tryParse((m.group(2) ?? '').trim());
    if (type.isEmpty || max == null || max <= 0) return null;
    return <String, dynamic>{
      'rankRange': <String, dynamic>{'max': max},
      'rankedTitleListType': type,
    };
  }

  static String? _readString(dynamic v) {
    if (v is String) return v.trim();
    return null;
  }

  static int? _readInt(dynamic v) {
    if (v is int) return v;
    if (v is num) return v.toInt();
    if (v is String) return int.tryParse(v.trim());
    return null;
  }

  static double? _readDouble(dynamic v) {
    if (v is double) return v;
    if (v is num) return v.toDouble();
    if (v is String) return double.tryParse(v.trim());
    return null;
  }

  static ImdbRankingItem? _parseEdge(dynamic edge) {
    if (edge is! Map) return null;

    dynamic node = edge['node'];
    if (node is Map && node['title'] != null) node = node['title'];
    if (node is! Map) return null;

    final id = _normalizeTitleId(_readString(node['id']) ?? '');
    if (id.isEmpty) return null;

    String readText(String key) {
      final nested = node[key];
      if (nested is Map) return _readString(nested['text']) ?? '';
      return '';
    }

    final title = readText('titleText');
    final originalTitle = readText('originalTitleText');

    final releaseYear = node['releaseYear'];
    final year = (releaseYear is Map) ? _readInt(releaseYear['year']) : null;

    final primaryImage = node['primaryImage'];
    final posterUrl =
        (primaryImage is Map) ? _readString(primaryImage['url']) : null;

    final ratingSummary = node['ratingsSummary'];
    final rating = (ratingSummary is Map)
        ? _readDouble(ratingSummary['aggregateRating'])
        : null;
    final voteCount =
        (ratingSummary is Map) ? _readInt(ratingSummary['voteCount']) : null;

    return ImdbRankingItem(
      id: id,
      title: title,
      originalTitle: originalTitle,
      year: year,
      posterUrl: posterUrl,
      rating: rating,
      voteCount: voteCount,
    );
  }

  static _ImdbAdvancedTitleSearchPage _parseAdvancedTitleSearchPage(
    Map<String, dynamic> data,
  ) {
    final search = data['advancedTitleSearch'];
    if (search is! Map) {
      return const _ImdbAdvancedTitleSearchPage(
        items: <ImdbRankingItem>[],
        endCursor: null,
        hasNextPage: false,
      );
    }

    final edges = search['edges'];
    final items = (edges is! List)
        ? const <ImdbRankingItem>[]
        : edges
            .map(_parseEdge)
            .whereType<ImdbRankingItem>()
            .where((e) => e.id.isNotEmpty)
            .toList(growable: false);

    final pageInfo = search['pageInfo'];
    final endCursor =
        (pageInfo is Map) ? _readString(pageInfo['endCursor']) : null;
    final hasNextPage = (pageInfo is Map) ? pageInfo['hasNextPage'] == true : false;

    return _ImdbAdvancedTitleSearchPage(
      items: items,
      endCursor: endCursor,
      hasNextPage: hasNextPage,
    );
  }

  Future<List<ImdbRankingItem>> _advancedTitleSearchMany({
    required int total,
    required List<String> titleTypes,
    required String sortBy,
    required String sortOrder,
    List<String>? ranked,
    List<String>? genres,
    String locale = _kImdbDefaultLocale,
  }) async {
    final want = total.clamp(1, 250);
    final out = <ImdbRankingItem>[];
    final seen = <String>{};

    String? after;
    while (out.length < want) {
      final pageSize =
          (want - out.length).clamp(1, _kImdbAdvancedSearchPageSize);
      final page = await _advancedTitleSearchPage(
        first: pageSize,
        after: after,
        locale: locale,
        titleTypes: titleTypes,
        sortBy: sortBy,
        sortOrder: sortOrder,
        ranked: ranked,
        genres: genres,
      );
      if (page.items.isEmpty) break;
      for (final item in page.items) {
        if (!seen.add(item.id)) continue;
        out.add(item);
        if (out.length >= want) break;
      }
      final nextCursor = (page.endCursor ?? '').trim();
      if (!page.hasNextPage || nextCursor.isEmpty) break;
      after = nextCursor;
    }

    return out;
  }

  Future<_ImdbAdvancedTitleSearchPage> _advancedTitleSearchPage({
    required List<String> titleTypes,
    required String sortBy,
    required String sortOrder,
    List<String>? ranked,
    List<String>? genres,
    required String locale,
    int first = _kImdbAdvancedSearchPageSize,
    String? after,
  }) async {
    final fixedFirst = first.clamp(1, _kImdbAdvancedSearchPageSize);
    final fixedAfter = (after ?? '').trim();
    final fixedLocale = locale.trim().isEmpty ? _kImdbDefaultLocale : locale.trim();

    final rankedConstraints = <Map<String, dynamic>>[];
    for (final r in ranked ?? const <String>[]) {
      final c = _rankedListToConstraint(r);
      if (c != null) rankedConstraints.add(c);
    }

    Map<String, dynamic> buildVars() {
      return <String, dynamic>{
        'first': fixedFirst,
        'locale': fixedLocale,
        'sortBy': sortBy.trim(),
        'sortOrder': sortOrder.trim(),
        'titleTypeConstraint': <String, dynamic>{
          'anyTitleTypeIds': titleTypes.map((e) => e.trim()).toList(),
        },
        if (genres != null && genres.isNotEmpty)
          'genreConstraint': <String, dynamic>{
            'allGenreIds': genres.map((e) => e.trim()).toList(),
            'excludeGenreIds': <String>[],
          },
        if (rankedConstraints.isNotEmpty)
          'rankedTitleListConstraint': <String, dynamic>{
            'allRankedTitleLists': rankedConstraints,
            'excludeRankedTitleLists': <Map<String, dynamic>>[],
          },
        if (fixedAfter.isNotEmpty) 'after': fixedAfter,
      };
    }

    Future<_ImdbAdvancedTitleSearchPage> runPersisted() async {
      final payload = <String, dynamic>{
        'operationName': 'AdvancedTitleSearch',
        'variables': buildVars(),
        'extensions': <String, dynamic>{
          'persistedQuery': <String, dynamic>{
            'sha256Hash': _kAdvancedTitleSearchSha256,
            'version': 1,
          },
        },
      };
      final decoded = await _postGraphql(payload);
      final errors = decoded['errors'];
      if (errors is List && errors.isNotEmpty) {
        final firstErr = errors.first;
        final msg = (firstErr is Map) ? _readString(firstErr['message']) : null;
        if (msg == 'PersistedQueryNotFound') {
          throw ImdbApiException('PersistedQueryNotFound');
        }
        throw ImdbApiException(msg ?? 'IMDb GraphQL error');
      }
      final data = decoded['data'];
      if (data is! Map) {
        throw ImdbApiException('Unexpected IMDb GraphQL data');
      }
      return _parseAdvancedTitleSearchPage(data.cast<String, dynamic>());
    }

    Future<_ImdbAdvancedTitleSearchPage> runInlineQuery() async {
      final query = _buildAdvancedTitleSearchInlineQuery(
        first: fixedFirst,
        after: fixedAfter.isEmpty ? null : fixedAfter,
        locale: fixedLocale,
        sortBy: sortBy.trim(),
        sortOrder: sortOrder.trim(),
        titleTypes: titleTypes,
        genres: genres,
        ranked: ranked,
      );
      final decoded = await _postGraphql(<String, dynamic>{'query': query});
      final errors = decoded['errors'];
      if (errors is List && errors.isNotEmpty) {
        final firstErr = errors.first;
        final msg = (firstErr is Map) ? _readString(firstErr['message']) : null;
        throw ImdbApiException(msg ?? 'IMDb GraphQL error');
      }
      final data = decoded['data'];
      if (data is! Map) {
        throw ImdbApiException('Unexpected IMDb GraphQL data');
      }
      return _parseAdvancedTitleSearchPage(data.cast<String, dynamic>());
    }

    try {
      return await runPersisted();
    } on ImdbApiException catch (e) {
      if (e.message == 'PersistedQueryNotFound') {
        return runInlineQuery();
      }
      rethrow;
    }
  }

  static String _gqlString(String v) => jsonEncode(v);

  static String _gqlStringList(Iterable<String> values) {
    return values.map((e) => _gqlString(e.trim())).join(', ');
  }

  static String _buildAdvancedTitleSearchInlineQuery({
    required int first,
    required String locale,
    required String sortBy,
    required String sortOrder,
    required List<String> titleTypes,
    List<String>? ranked,
    List<String>? genres,
    String? after,
  }) {
    final anyTypes = _gqlStringList(titleTypes);

    final rankedParts = <String>[];
    for (final r in ranked ?? const <String>[]) {
      final m =
          RegExp(r'^(TOP_RATED_MOVIES|LOWEST_RATED_MOVIES)-(\d+)$').firstMatch(
        r.trim(),
      );
      if (m == null) continue;
      final type = (m.group(1) ?? '').trim();
      final max = int.tryParse((m.group(2) ?? '').trim());
      if (type.isEmpty || max == null || max <= 0) continue;
      rankedParts.add(
        '{ rankRange: { max: $max }, rankedTitleListType: $type }',
      );
    }

    final genrePart = (genres == null || genres.isEmpty)
        ? ''
        : '''
      genreConstraint: { allGenreIds: [${_gqlStringList(genres)}], excludeGenreIds: [] }
''';

    final rankedPart = rankedParts.isEmpty
        ? ''
        : '''
      rankedTitleListConstraint: {
        allRankedTitleLists: [${rankedParts.join(', ')}],
        excludeRankedTitleLists: []
      }
''';

    final localeArg = _gqlString(locale.trim().isEmpty ? _kImdbDefaultLocale : locale.trim());
    final afterPart = (after == null || after.trim().isEmpty)
        ? ''
        : 'after: ${_gqlString(after.trim())}';

    return '''
query {
  advancedTitleSearch(
    first: $first
    $afterPart
    locale: $localeArg
    sortBy: $sortBy
    sortOrder: $sortOrder
    titleTypeConstraint: { anyTitleTypeIds: [$anyTypes] }
$genrePart$rankedPart
  ) {
    edges {
      node {
        title {
          id
          titleText { text }
          originalTitleText { text }
          releaseYear { year }
          primaryImage { url }
          ratingsSummary { aggregateRating voteCount }
        }
      }
    }
    pageInfo { endCursor hasNextPage }
  }
}
''';
  }
}

class _CacheEntry<T> {
  const _CacheEntry(this.value, this.timestamp);

  final T value;
  final DateTime timestamp;
}

class _ImdbAdvancedTitleSearchPage {
  const _ImdbAdvancedTitleSearchPage({
    required this.items,
    required this.endCursor,
    required this.hasNextPage,
  });

  final List<ImdbRankingItem> items;
  final String? endCursor;
  final bool hasNextPage;
}

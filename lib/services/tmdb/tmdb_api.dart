import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:lin_player_server_api/network/lin_http_client.dart';

const String _kTmdbHost = 'api.themoviedb.org';
const String _kTmdbImageBase = 'https://image.tmdb.org/t/p/';

enum TmdbMediaType { tv, movie }

class TmdbApiException implements Exception {
  TmdbApiException(
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

class TmdbMedia {
  const TmdbMedia({
    required this.id,
    required this.mediaType,
    required this.title,
    required this.originalTitle,
    required this.posterPath,
    required this.voteAverage,
    required this.voteCount,
    required this.popularity,
    required this.adult,
    required this.date,
  });

  final int id;
  final TmdbMediaType mediaType;
  final String title;
  final String originalTitle;
  final String? posterPath;
  final double? voteAverage;
  final int? voteCount;
  final double? popularity;
  final bool adult;
  final String date;

  String get displayTitle {
    final t = title.trim();
    if (t.isNotEmpty) return t;
    return originalTitle.trim();
  }

  String? get posterUrl => TmdbImageUrl.posterW500(posterPath);

  double? get effectiveVoteAverage {
    final v = voteAverage;
    if (v == null || v <= 0) return null;
    return v;
  }

  factory TmdbMedia.fromJson(
    Map<String, dynamic> json, {
    required TmdbMediaType mediaType,
  }) {
    String readString(String key) => (json[key] as String?)?.trim() ?? '';

    final id = (json['id'] as num?)?.toInt() ?? 0;
    final posterPath = (json['poster_path'] as String?)?.trim();
    final fixedPosterPath =
        (posterPath == null || posterPath.isEmpty) ? null : posterPath;
    final voteAverage = (json['vote_average'] as num?)?.toDouble();
    final voteCount = (json['vote_count'] as num?)?.toInt();
    final popularity = (json['popularity'] as num?)?.toDouble();
    final adult = json['adult'] == true;

    String title;
    String originalTitle;
    String date;
    switch (mediaType) {
      case TmdbMediaType.tv:
        title = readString('name');
        originalTitle = readString('original_name');
        date = readString('first_air_date');
        break;
      case TmdbMediaType.movie:
        title = readString('title');
        originalTitle = readString('original_title');
        date = readString('release_date');
        break;
    }

    return TmdbMedia(
      id: id,
      mediaType: mediaType,
      title: title,
      originalTitle: originalTitle,
      posterPath: fixedPosterPath,
      voteAverage: voteAverage,
      voteCount: voteCount,
      popularity: popularity,
      adult: adult,
      date: date,
    );
  }
}

class TmdbImageUrl {
  static String? posterW500(String? posterPath) {
    final raw = (posterPath ?? '').trim();
    if (raw.isEmpty) return null;
    if (raw.startsWith('http://') || raw.startsWith('https://')) return raw;
    final fixed = raw.startsWith('/') ? raw : '/$raw';
    return '${_kTmdbImageBase}w500$fixed';
  }
}

class TmdbApiClient {
  TmdbApiClient({http.Client? client})
      : _client = client ?? LinHttpClientFactory.createClient();

  final http.Client _client;

  void close() => _client.close();

  static String? _extractBearerToken(String raw) {
    final v = raw.trim();
    if (v.isEmpty) return null;
    if (v.toLowerCase().startsWith('bearer ')) return v.substring(7).trim();
    final parts = v.split('.');
    if (parts.length >= 3 && v.length > 60) return v;
    return null;
  }

  Map<String, String> _headers({String? bearerToken}) {
    final headers = <String, String>{
      'Accept': 'application/json',
      'User-Agent': LinHttpClientFactory.userAgent,
    };
    final token = (bearerToken ?? '').trim();
    if (token.isNotEmpty) headers['Authorization'] = 'Bearer $token';
    return headers;
  }

  Never _throwHttp(http.Response resp) {
    throw TmdbApiException(
      'TMDB API request failed',
      statusCode: resp.statusCode,
      body: resp.body,
    );
  }

  void _ensureOk(http.Response resp) {
    final code = resp.statusCode;
    if (code < 200 || code >= 300) _throwHttp(resp);
  }

  Future<List<TmdbMedia>> topRatedTv({
    required String apiKey,
    String language = 'zh-CN',
    int page = 1,
  }) {
    return _getMediaList(
      '/3/tv/top_rated',
      mediaType: TmdbMediaType.tv,
      apiKey: apiKey,
      language: language,
      page: page,
    );
  }

  Future<List<TmdbMedia>> topRatedMovies({
    required String apiKey,
    String language = 'zh-CN',
    int page = 1,
  }) {
    return _getMediaList(
      '/3/movie/top_rated',
      mediaType: TmdbMediaType.movie,
      apiKey: apiKey,
      language: language,
      page: page,
    );
  }

  Future<List<TmdbMedia>> popularTv({
    required String apiKey,
    String language = 'zh-CN',
    int page = 1,
  }) {
    return _getMediaList(
      '/3/tv/popular',
      mediaType: TmdbMediaType.tv,
      apiKey: apiKey,
      language: language,
      page: page,
    );
  }

  Future<List<TmdbMedia>> popularMovies({
    required String apiKey,
    String language = 'zh-CN',
    int page = 1,
  }) {
    return _getMediaList(
      '/3/movie/popular',
      mediaType: TmdbMediaType.movie,
      apiKey: apiKey,
      language: language,
      page: page,
    );
  }

  Future<List<TmdbMedia>> _getMediaList(
    String path, {
    required TmdbMediaType mediaType,
    required String apiKey,
    required String language,
    required int page,
  }) async {
    final key = apiKey.trim();
    if (key.isEmpty) {
      throw TmdbApiException('TMDB API key is not set');
    }

    final bearer = _extractBearerToken(key);
    final query = <String, String>{
      'language': language.trim().isEmpty ? 'zh-CN' : language.trim(),
      'page': page.clamp(1, 500).toString(),
      if (bearer == null) 'api_key': key,
    };
    final uri = Uri.https(_kTmdbHost, path, query);

    final resp = await _client.get(uri, headers: _headers(bearerToken: bearer));
    _ensureOk(resp);

    final decoded = jsonDecode(resp.body);
    if (decoded is! Map) {
      throw TmdbApiException('Unexpected TMDB list response');
    }
    final results = (decoded['results'] as List?) ?? const [];
    return results
        .whereType<Map>()
        .map(
          (e) => TmdbMedia.fromJson(
            e.cast<String, dynamic>(),
            mediaType: mediaType,
          ),
        )
        .where((e) => e.id > 0)
        .where((e) => !e.adult)
        .toList(growable: false);
  }
}


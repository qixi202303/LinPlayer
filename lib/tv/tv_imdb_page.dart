import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:lin_player_server_api/network/lin_http_client.dart';
import 'package:lin_player_state/lin_player_state.dart';
import 'package:lin_player_ui/lin_player_ui.dart';

import '../aggregate_service_page.dart';
import '../services/imdb/imdb_api.dart';
import 'tv_focusable.dart';

void _pushAggregateSearch(
  BuildContext context, {
  required AppState appState,
  required String query,
}) {
  final q = query.trim();
  if (q.isEmpty) return;
  Navigator.of(context).push(
    MaterialPageRoute(
      builder: (_) => AggregateServicePage(
        appState: appState,
        initialTabIndex: 1,
        initialQuery: q,
      ),
    ),
  );
}

class TvImdbPage extends StatefulWidget {
  const TvImdbPage({super.key, required this.appState});

  final AppState appState;

  @override
  State<TvImdbPage> createState() => _TvImdbPageState();
}

class _TvImdbPageState extends State<TvImdbPage> {
  final ImdbApiClient _api = ImdbApiClient();

  late Future<List<ImdbRankingItem>> _top250Future;
  late Future<List<ImdbRankingItem>> _movieRankingFuture;
  late Future<List<ImdbRankingItem>> _seriesRankingFuture;
  late Future<List<ImdbRankingItem>> _animeRankingFuture;

  @override
  void initState() {
    super.initState();
    _reload(forceRefresh: false);
  }

  @override
  void dispose() {
    _api.close();
    super.dispose();
  }

  void _reload({required bool forceRefresh}) {
    const previewCount = 10;
    setState(() {
      _top250Future =
          _api.top250(forceRefresh: forceRefresh, first: previewCount);
      _movieRankingFuture =
          _api.movieRanking(forceRefresh: forceRefresh, first: previewCount);
      _seriesRankingFuture =
          _api.seriesRanking(forceRefresh: forceRefresh, first: previewCount);
      _animeRankingFuture =
          _api.animeRanking(forceRefresh: forceRefresh, first: previewCount);
    });
  }

  void _openRanking(
    BuildContext context, {
    required String title,
    required String subtitle,
    required Future<List<ImdbRankingItem>> Function({bool forceRefresh}) fetch,
  }) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => TvImdbRankingPage(
          appState: widget.appState,
          title: title,
          subtitle: subtitle,
          fetch: fetch,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final uiScale = context.uiScale;

    final header = Row(
      children: [
        Text(
          'IMDb',
          style: theme.textTheme.titleLarge?.copyWith(
            fontWeight: FontWeight.w800,
          ),
        ),
        const Spacer(),
        TvFocusable(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
          borderRadius: const BorderRadius.all(Radius.circular(999)),
          onPressed: () => _reload(forceRefresh: true),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                Icons.refresh_rounded,
                size: (18 * uiScale).clamp(16.0, 20.0),
                color: scheme.onSurface,
              ),
              const SizedBox(width: 8),
              Text(
                '刷新',
                style: theme.textTheme.labelLarge?.copyWith(
                  fontWeight: FontWeight.w800,
                ),
              ),
            ],
          ),
        ),
      ],
    );

    return ListView(
      padding: const EdgeInsets.fromLTRB(18, 10, 18, 28),
      children: [
        header,
        const SizedBox(height: 16),
        _ImdbSection(
          title: 'IMDb Top 250',
          subtitle: 'Top Rated Movies',
          future: _top250Future,
          onRetry: () => _reload(forceRefresh: true),
          onOpen: () => _openRanking(
            context,
            title: 'IMDb Top 250',
            subtitle: 'Top Rated Movies',
            fetch: ({forceRefresh = false}) =>
                _api.top250(forceRefresh: forceRefresh),
          ),
          onTapItem: (m) => _pushAggregateSearch(
            context,
            appState: widget.appState,
            query: m.displayTitle,
          ),
        ),
        const SizedBox(height: 18),
        _ImdbSection(
          title: '电影排行榜',
          subtitle: '按评分从高到低',
          future: _movieRankingFuture,
          onRetry: () => _reload(forceRefresh: true),
          onOpen: () => _openRanking(
            context,
            title: '电影排行榜',
            subtitle: '按评分从高到低',
            fetch: ({forceRefresh = false}) =>
                _api.movieRanking(forceRefresh: forceRefresh),
          ),
          onTapItem: (m) => _pushAggregateSearch(
            context,
            appState: widget.appState,
            query: m.displayTitle,
          ),
        ),
        const SizedBox(height: 18),
        _ImdbSection(
          title: '剧集排行榜',
          subtitle: 'TV Series / Mini Series',
          future: _seriesRankingFuture,
          onRetry: () => _reload(forceRefresh: true),
          onOpen: () => _openRanking(
            context,
            title: '剧集排行榜',
            subtitle: 'TV Series / Mini Series',
            fetch: ({forceRefresh = false}) =>
                _api.seriesRanking(forceRefresh: forceRefresh),
          ),
          onTapItem: (m) => _pushAggregateSearch(
            context,
            appState: widget.appState,
            query: m.displayTitle,
          ),
        ),
        const SizedBox(height: 18),
        _ImdbSection(
          title: '动漫排行榜',
          subtitle: 'Animation',
          future: _animeRankingFuture,
          onRetry: () => _reload(forceRefresh: true),
          onOpen: () => _openRanking(
            context,
            title: '动漫排行榜',
            subtitle: 'Animation',
            fetch: ({forceRefresh = false}) =>
                _api.animeRanking(forceRefresh: forceRefresh),
          ),
          onTapItem: (m) => _pushAggregateSearch(
            context,
            appState: widget.appState,
            query: m.displayTitle,
          ),
        ),
      ],
    );
  }
}

class _ImdbSection extends StatelessWidget {
  const _ImdbSection({
    required this.title,
    required this.subtitle,
    required this.future,
    required this.onRetry,
    required this.onTapItem,
    this.onOpen,
  });

  final String title;
  final String subtitle;
  final Future<List<ImdbRankingItem>> future;
  final VoidCallback onRetry;
  final ValueChanged<ImdbRankingItem> onTapItem;
  final VoidCallback? onOpen;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final uiScale = context.uiScale;

    final titleStyle = theme.textTheme.titleMedium?.copyWith(
      fontWeight: FontWeight.w800,
    );
    final subtitleStyle = theme.textTheme.bodySmall?.copyWith(
      color: scheme.onSurfaceVariant,
      fontWeight: FontWeight.w600,
    );

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title, style: titleStyle),
                  if (subtitle.trim().isNotEmpty) ...[
                    const SizedBox(height: 2),
                    Text(subtitle, style: subtitleStyle),
                  ],
                ],
              ),
            ),
            if (onOpen != null)
              TvFocusable(
                padding:
                    const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                borderRadius: const BorderRadius.all(Radius.circular(999)),
                onPressed: onOpen,
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      '详情',
                      style: theme.textTheme.labelLarge?.copyWith(
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(width: 4),
                    Icon(
                      Icons.chevron_right_rounded,
                      size: (18 * uiScale).clamp(16.0, 20.0),
                      color: scheme.onSurface,
                    ),
                  ],
                ),
              ),
          ],
        ),
        const SizedBox(height: 10),
        FutureBuilder<List<ImdbRankingItem>>(
          future: future,
          builder: (context, snap) {
            if (snap.connectionState != ConnectionState.done) {
              return const _ImdbLoadingRow();
            }
            if (snap.hasError) {
              return _ImdbErrorRow(onRetry: onRetry);
            }
            final items = snap.data ?? const [];
            if (items.isEmpty) {
              return Text(
                '暂无数据',
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: scheme.onSurfaceVariant,
                ),
              );
            }

            final count = items.length > 10 ? 10 : items.length;
            final height = (260 * uiScale).clamp(220.0, 300.0);
            final cardWidth = (150 * uiScale).clamp(120.0, 190.0);

            return SizedBox(
              height: height,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                clipBehavior: Clip.none,
                itemCount: count,
                separatorBuilder: (_, __) => const SizedBox(width: 12),
                itemBuilder: (context, index) => _ImdbPosterCard(
                  item: items[index],
                  rank: index + 1,
                  onPressed: () => onTapItem(items[index]),
                  width: cardWidth,
                  autofocus: index == 0,
                ),
              ),
            );
          },
        ),
      ],
    );
  }
}

class _ImdbLoadingRow extends StatelessWidget {
  const _ImdbLoadingRow();

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return SizedBox(
      height: (260 * context.uiScale).clamp(220.0, 300.0),
      child: Center(
        child: CircularProgressIndicator(color: scheme.primary),
      ),
    );
  }
}

class _ImdbErrorRow extends StatelessWidget {
  const _ImdbErrorRow({required this.onRetry});

  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return SizedBox(
      height: (260 * context.uiScale).clamp(220.0, 300.0),
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              '加载失败',
              style: theme.textTheme.bodyMedium?.copyWith(
                color: scheme.onSurfaceVariant,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: 10),
            TvFocusable(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              borderRadius: const BorderRadius.all(Radius.circular(999)),
              onPressed: onRetry,
              child: Text(
                '重试',
                style: theme.textTheme.labelLarge?.copyWith(
                  fontWeight: FontWeight.w800,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ImdbPosterCard extends StatelessWidget {
  const _ImdbPosterCard({
    required this.item,
    required this.rank,
    required this.onPressed,
    this.width,
    this.autofocus = false,
  });

  final ImdbRankingItem item;
  final int rank;
  final VoidCallback onPressed;
  final double? width;
  final bool autofocus;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final uiScale = context.uiScale;

    final radius = (14 * uiScale).clamp(10.0, 16.0);

    final imageUrl = item.posterUrl;
    final imageWidget = imageUrl == null || imageUrl.trim().isEmpty
        ? const ColoredBox(
            color: Colors.black26,
            child: Center(child: Icon(Icons.image_outlined)),
          )
        : CachedNetworkImage(
            imageUrl: imageUrl,
            cacheManager: CoverCacheManager.instance,
            httpHeaders: {'User-Agent': LinHttpClientFactory.userAgent},
            fit: BoxFit.cover,
            placeholder: (_, __) => const ColoredBox(
              color: Colors.black12,
              child: Center(child: Icon(Icons.image_outlined)),
            ),
            errorWidget: (_, __, ___) => const ColoredBox(
              color: Colors.black26,
              child: Center(child: Icon(Icons.broken_image_outlined)),
            ),
            useOldImageOnUrlChange: true,
            fadeInDuration: Duration.zero,
            fadeOutDuration: Duration.zero,
            placeholderFadeInDuration: Duration.zero,
          );

    final score = item.rating;
    final year = item.year;

    final metaStyle = theme.textTheme.labelSmall?.copyWith(
      color: scheme.onSurfaceVariant,
      fontWeight: FontWeight.w700,
    );

    final card = TvFocusable(
      autofocus: autofocus,
      borderRadius: BorderRadius.circular(radius + 2),
      onPressed: onPressed,
      child: Padding(
        padding: const EdgeInsets.all(8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            AspectRatio(
              aspectRatio: 2 / 3,
              child: ClipRRect(
                borderRadius: BorderRadius.circular(radius),
                child: imageWidget,
              ),
            ),
            SizedBox(height: (8 * uiScale).clamp(6.0, 10.0)),
            Text(
              item.displayTitle,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: theme.textTheme.bodyMedium?.copyWith(
                fontWeight: FontWeight.w800,
              ),
            ),
            const SizedBox(height: 3),
            Row(
              children: [
                if (score != null && score > 0) ...[
                  const Icon(
                    Icons.star_rounded,
                    size: 16,
                    color: Colors.amber,
                  ),
                  const SizedBox(width: 2),
                  Text(score.toStringAsFixed(1), style: metaStyle),
                  const SizedBox(width: 8),
                ],
                if (year != null && year > 0) ...[
                  Text('$year', style: metaStyle),
                  const SizedBox(width: 8),
                ],
                Text('#$rank', style: metaStyle),
              ],
            ),
          ],
        ),
      ),
    );

    final fixedWidth = width;
    if (fixedWidth == null) return card;
    return SizedBox(width: fixedWidth, child: card);
  }
}

class TvImdbRankingPage extends StatefulWidget {
  const TvImdbRankingPage({
    super.key,
    required this.appState,
    required this.title,
    required this.subtitle,
    required this.fetch,
  });

  final AppState appState;
  final String title;
  final String subtitle;
  final Future<List<ImdbRankingItem>> Function({bool forceRefresh}) fetch;

  @override
  State<TvImdbRankingPage> createState() => _TvImdbRankingPageState();
}

class _TvImdbRankingPageState extends State<TvImdbRankingPage> {
  late Future<List<ImdbRankingItem>> _future;

  @override
  void initState() {
    super.initState();
    _future = widget.fetch(forceRefresh: false);
  }

  void _reload() {
    setState(() {
      _future = widget.fetch(forceRefresh: true);
    });
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final uiScale = context.uiScale;

    final maxCrossAxisExtent = (190 * uiScale).clamp(160.0, 220.0);

    return Scaffold(
      appBar: AppBar(
        title: Text(widget.title),
        actions: [
          IconButton(
            tooltip: '刷新',
            onPressed: _reload,
            icon: const Icon(Icons.refresh_rounded),
          ),
        ],
      ),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: FutureBuilder<List<ImdbRankingItem>>(
          future: _future,
          builder: (context, snap) {
            if (snap.connectionState != ConnectionState.done) {
              return Center(
                child: CircularProgressIndicator(color: scheme.primary),
              );
            }
            if (snap.hasError) {
              return Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      '加载失败',
                      style: theme.textTheme.bodyMedium?.copyWith(
                        color: scheme.onSurfaceVariant,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(height: 10),
                    TvFocusable(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 18,
                        vertical: 12,
                      ),
                      borderRadius: const BorderRadius.all(Radius.circular(999)),
                      onPressed: _reload,
                      child: Text(
                        '重试',
                        style: theme.textTheme.labelLarge?.copyWith(
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                    ),
                  ],
                ),
              );
            }

            final items = snap.data ?? const [];
            if (items.isEmpty) {
              return Center(
                child: Text(
                  '暂无数据',
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: scheme.onSurfaceVariant,
                  ),
                ),
              );
            }

            return GridView.builder(
              gridDelegate: SliverGridDelegateWithMaxCrossAxisExtent(
                maxCrossAxisExtent: maxCrossAxisExtent,
                mainAxisSpacing: 10,
                crossAxisSpacing: 10,
                childAspectRatio: 0.52,
              ),
              clipBehavior: Clip.none,
              itemCount: items.length,
              itemBuilder: (context, index) {
                final item = items[index];
                final rank = index + 1;
                return _ImdbPosterCard(
                  item: item,
                  rank: rank,
                  autofocus: index == 0,
                  onPressed: () => _pushAggregateSearch(
                    context,
                    appState: widget.appState,
                    query: item.displayTitle,
                  ),
                );
              },
            );
          },
        ),
      ),
    );
  }
}

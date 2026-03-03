import 'dart:async';

import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:lin_player_server_api/network/lin_http_client.dart';
import 'package:lin_player_state/lin_player_state.dart';
import 'package:lin_player_ui/lin_player_ui.dart';

import '../aggregate_service_page.dart';
import '../services/tmdb/tmdb_api.dart';
import '../settings_page.dart';
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

class TvTmdbPage extends StatefulWidget {
  const TvTmdbPage({super.key, required this.appState});

  final AppState appState;

  @override
  State<TvTmdbPage> createState() => _TvTmdbPageState();
}

class _TvTmdbPageState extends State<TvTmdbPage> {
  final TmdbApiClient _api = TmdbApiClient();

  late Future<List<TmdbMedia>> _topRatedTvFuture;
  late Future<List<TmdbMedia>> _topRatedMovieFuture;
  late Future<List<TmdbMedia>> _popularTvFuture;
  late Future<List<TmdbMedia>> _popularMovieFuture;

  String _lastApiKey = '';

  @override
  void initState() {
    super.initState();
    _lastApiKey = widget.appState.tmdbApiKey.trim();
    _reload();
  }

  @override
  void didUpdateWidget(covariant TvTmdbPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    final nextKey = widget.appState.tmdbApiKey.trim();
    if (nextKey != _lastApiKey) {
      _lastApiKey = nextKey;
      _reload();
    }
  }

  @override
  void dispose() {
    _api.close();
    super.dispose();
  }

  void _reload() {
    final key = widget.appState.tmdbApiKey.trim();
    if (key.isEmpty) {
      setState(() {
        _topRatedTvFuture = Future.value(const []);
        _topRatedMovieFuture = Future.value(const []);
        _popularTvFuture = Future.value(const []);
        _popularMovieFuture = Future.value(const []);
      });
      return;
    }

    setState(() {
      _topRatedTvFuture = _api.topRatedTv(apiKey: key);
      _topRatedMovieFuture = _api.topRatedMovies(apiKey: key);
      _popularTvFuture = _api.popularTv(apiKey: key);
      _popularMovieFuture = _api.popularMovies(apiKey: key);
    });
  }

  void _openSettings() {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => SettingsPage(appState: widget.appState),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final uiScale = context.uiScale;

    final apiKey = widget.appState.tmdbApiKey.trim();
    final missingKey = apiKey.isEmpty;

    final header = Row(
      children: [
        Text(
          'TMDB',
          style: theme.textTheme.titleLarge?.copyWith(
            fontWeight: FontWeight.w800,
          ),
        ),
        const Spacer(),
        TvFocusable(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
          borderRadius: const BorderRadius.all(Radius.circular(999)),
          onPressed: missingKey ? _openSettings : _reload,
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                missingKey ? Icons.settings_rounded : Icons.refresh_rounded,
                size: (18 * uiScale).clamp(16.0, 20.0),
                color: scheme.onSurface,
              ),
              const SizedBox(width: 8),
              Text(
                missingKey ? '设置' : '刷新',
                style: theme.textTheme.labelLarge?.copyWith(
                  fontWeight: FontWeight.w800,
                ),
              ),
            ],
          ),
        ),
      ],
    );

    if (missingKey) {
      return ListView(
        padding: const EdgeInsets.fromLTRB(18, 10, 18, 28),
        children: [
          header,
          const SizedBox(height: 16),
          Text(
            '未配置 TMDB API Key / Token',
            style: theme.textTheme.titleMedium?.copyWith(
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            '路径：设置 → TV 专区 → TMDB API Key / Token',
            style: theme.textTheme.bodyMedium?.copyWith(
              color: scheme.onSurfaceVariant,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      );
    }

    return ListView(
      padding: const EdgeInsets.fromLTRB(18, 10, 18, 28),
      children: [
        header,
        const SizedBox(height: 16),
        _TmdbSection(
          title: '高分节目',
          subtitle: '按评分从高到低',
          future: _topRatedTvFuture,
          onRetry: _reload,
          onTapItem: (m) => _pushAggregateSearch(
            context,
            appState: widget.appState,
            query: m.displayTitle,
          ),
        ),
        const SizedBox(height: 20),
        _TmdbSection(
          title: '高分电影',
          subtitle: '按评分从高到低',
          future: _topRatedMovieFuture,
          onRetry: _reload,
          onTapItem: (m) => _pushAggregateSearch(
            context,
            appState: widget.appState,
            query: m.displayTitle,
          ),
        ),
        const SizedBox(height: 20),
        _TmdbSection(
          title: '热门节目',
          subtitle: '按热度从高到低',
          future: _popularTvFuture,
          onRetry: _reload,
          onTapItem: (m) => _pushAggregateSearch(
            context,
            appState: widget.appState,
            query: m.displayTitle,
          ),
        ),
        const SizedBox(height: 20),
        _TmdbSection(
          title: '热门电影',
          subtitle: '按热度从高到低',
          future: _popularMovieFuture,
          onRetry: _reload,
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

class _TmdbSection extends StatelessWidget {
  const _TmdbSection({
    required this.title,
    required this.subtitle,
    required this.future,
    required this.onRetry,
    required this.onTapItem,
  });

  final String title;
  final String subtitle;
  final Future<List<TmdbMedia>> future;
  final VoidCallback onRetry;
  final ValueChanged<TmdbMedia> onTapItem;

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
          ],
        ),
        const SizedBox(height: 10),
        FutureBuilder<List<TmdbMedia>>(
          future: future,
          builder: (context, snap) {
            if (snap.connectionState != ConnectionState.done) {
              return const _TmdbLoadingRow();
            }
            if (snap.hasError) {
              return _TmdbErrorRow(onRetry: onRetry);
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
                itemBuilder: (context, index) => _TmdbPosterCard(
                  media: items[index],
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

class _TmdbLoadingRow extends StatelessWidget {
  const _TmdbLoadingRow();

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

class _TmdbErrorRow extends StatelessWidget {
  const _TmdbErrorRow({required this.onRetry});

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

class _TmdbPosterCard extends StatelessWidget {
  const _TmdbPosterCard({
    required this.media,
    required this.rank,
    required this.onPressed,
    this.width,
    this.autofocus = false,
  });

  final TmdbMedia media;
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

    final imageUrl = media.posterUrl;
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

    final score = media.effectiveVoteAverage;

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
              media.displayTitle,
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


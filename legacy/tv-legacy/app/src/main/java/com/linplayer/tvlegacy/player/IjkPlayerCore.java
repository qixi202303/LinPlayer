package com.linplayer.tvlegacy.player;

import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.linplayer.tvlegacy.BuildConfig;
import java.io.IOException;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

final class IjkPlayerCore implements PlayerCore {
    private static volatile boolean libsLoaded = false;

    private final Context appContext;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SurfaceView surfaceView;

    @Nullable private Listener listener;
    @Nullable private IjkMediaPlayer player;
    private boolean playWhenReady = false;
    private long pendingSeekMs = 0L;

    IjkPlayerCore(@NonNull Context context) {
        appContext = context.getApplicationContext();
        surfaceView = new SurfaceView(context);
        surfaceView.getHolder()
                .addCallback(
                        new SurfaceHolder.Callback() {
                            @Override
                            public void surfaceCreated(SurfaceHolder holder) {
                                IjkMediaPlayer p = player;
                                if (p == null) return;
                                try {
                                    p.setDisplay(holder);
                                } catch (Exception ignored) {
                                }
                            }

                            @Override
                            public void surfaceChanged(
                                    SurfaceHolder holder, int format, int width, int height) {}

                            @Override
                            public void surfaceDestroyed(SurfaceHolder holder) {
                                IjkMediaPlayer p = player;
                                if (p == null) return;
                                try {
                                    p.setDisplay(null);
                                } catch (Exception ignored) {
                                }
                            }
                        });
        loadLibsOnce();
    }

    @Override
    @NonNull
    public PlayerCoreType getType() {
        return PlayerCoreType.IJK;
    }

    @Override
    @NonNull
    public String getDisplayName() {
        return getType().displayName;
    }

    @Override
    @NonNull
    public View getView() {
        return surfaceView;
    }

    @Override
    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    @Override
    public void open(
            @NonNull String url,
            @Nullable Map<String, String> headers,
            long startPositionMs,
            boolean playWhenReady) {
        releaseInternal();

        this.playWhenReady = playWhenReady;
        pendingSeekMs = Math.max(0L, startPositionMs);

        IjkMediaPlayer p = new IjkMediaPlayer();
        player = p;
        p.setAudioStreamType(AudioManager.STREAM_MUSIC);
        applyDefaultOptions(p);

        p.setOnPreparedListener(
                mp -> {
                    long seek = pendingSeekMs;
                    if (seek > 0L) {
                        try {
                            mp.seekTo(seek);
                        } catch (Exception ignored) {
                        }
                    }
                    if (this.playWhenReady) {
                        try {
                            mp.start();
                        } catch (Exception ignored) {
                        }
                    }
                    notifyTracksChanged();
                });

        p.setOnErrorListener(
                (mp, what, extra) -> {
                    postReleaseAndFatalError(p, buildIjkErrorMessage(what, extra));
                    return true;
                });

        p.setOnCompletionListener(mp -> notifyTracksChanged());

        try {
            Uri uri = Uri.parse(url);
            Map<String, String> effectiveHeaders = buildHeaders(headers);
            if (effectiveHeaders != null && !effectiveHeaders.isEmpty()) {
                p.setDataSource(appContext, uri, effectiveHeaders);
            } else {
                p.setDataSource(appContext, uri);
            }
            try {
                p.setDisplay(surfaceView.getHolder());
            } catch (Exception ignored) {
            }
            p.prepareAsync();
        } catch (IOException e) {
            postReleaseAndFatalError(p, buildExceptionMessage("打开媒体失败", e));
        } catch (Exception e) {
            postReleaseAndFatalError(p, buildExceptionMessage("打开媒体失败", e));
        }
    }

    @Override
    public void play() {
        playWhenReady = true;
        IjkMediaPlayer p = player;
        if (p == null) return;
        try {
            p.start();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void pause() {
        playWhenReady = false;
        IjkMediaPlayer p = player;
        if (p == null) return;
        try {
            p.pause();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void stop() {
        playWhenReady = false;
        IjkMediaPlayer p = player;
        if (p == null) return;
        try {
            p.stop();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void seekTo(long positionMs) {
        long ms = Math.max(0L, positionMs);
        pendingSeekMs = ms;

        IjkMediaPlayer p = player;
        if (p == null) return;
        try {
            p.seekTo(ms);
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean isPlaying() {
        IjkMediaPlayer p = player;
        if (p == null) return false;
        try {
            return p.isPlaying();
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public long getDurationMs() {
        IjkMediaPlayer p = player;
        if (p == null) return 0L;
        try {
            long d = p.getDuration();
            return d > 0L ? d : 0L;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    @Override
    public long getPositionMs() {
        IjkMediaPlayer p = player;
        if (p == null) return 0L;
        try {
            long v = p.getCurrentPosition();
            return v > 0L ? v : 0L;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    @Override
    public long getBufferedPositionMs() {
        // ijkplayer buffering APIs vary across builds/artifacts; keep it simple.
        return getPositionMs();
    }

    @Override
    @NonNull
    public List<PlayerTrack> getAudioTracks() {
        return Collections.emptyList();
    }

    @Override
    @NonNull
    public List<PlayerTrack> getSubtitleTracks() {
        return Collections.emptyList();
    }

    @Override
    public void selectAudioTrack(@Nullable Integer trackId) {}

    @Override
    public void setSubtitlesEnabled(boolean enabled) {}

    @Override
    public void selectSubtitleTrack(@Nullable Integer trackId) {}

    @Override
    public void release() {
        releaseInternal();
    }

    private void releaseInternal() {
        IjkMediaPlayer p = player;
        player = null;
        if (p == null) return;
        try {
            p.setDisplay(null);
        } catch (Exception ignored) {
        }
        try {
            p.stop();
        } catch (Exception ignored) {
        }
        try {
            p.release();
        } catch (Exception ignored) {
        }
    }

    private void notifyTracksChanged() {
        Listener l = listener;
        if (l == null) return;
        main.post(
                () -> {
                    Listener l2 = listener;
                    if (l2 != null) l2.onTracksChanged();
                });
    }

    private void postReleaseAndFatalError(@NonNull IjkMediaPlayer p, @NonNull String message) {
        main.post(
                () -> {
                    releasePlayer(p);
                    Listener l2 = listener;
                    if (l2 != null) l2.onFatalError(message);
                });
    }

    private void releasePlayer(@NonNull IjkMediaPlayer p) {
        if (player == p) player = null;
        try {
            p.setDisplay(null);
        } catch (Exception ignored) {
        }
        try {
            p.stop();
        } catch (Exception ignored) {
        }
        try {
            p.release();
        } catch (Exception ignored) {
        }
    }

    private static void applyDefaultOptions(@NonNull IjkMediaPlayer p) {
        // Best-effort: some servers reject unknown User-Agent, resulting in "open input" errors.
        try {
            p.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "user_agent", userAgent());
        } catch (Throwable ignored) {
        }
        // Prefer TCP for RTSP to reduce packet loss on unstable networks.
        try {
            p.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "rtsp_transport", "tcp");
        } catch (Throwable ignored) {
        }
        // Keep going on transient network issues (if supported by the build).
        try {
            p.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1);
        } catch (Throwable ignored) {
        }
    }

    @NonNull
    private static Map<String, String> buildHeaders(@Nullable Map<String, String> headers) {
        Map<String, String> out = new HashMap<>();
        if (headers != null && !headers.isEmpty()) out.putAll(headers);

        // Add a default User-Agent (don't override an explicit one).
        boolean hasUa = false;
        for (String k : out.keySet()) {
            if (k == null) continue;
            if ("user-agent".equalsIgnoreCase(k.trim())) {
                hasUa = true;
                break;
            }
        }
        if (!hasUa) out.put("User-Agent", userAgent());

        return out;
    }

    @NonNull
    private static String buildExceptionMessage(@NonNull String prefix, @NonNull Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.trim().isEmpty()) msg = e.toString();
        return prefix + "：" + msg;
    }

    @NonNull
    private static String buildIjkErrorMessage(int what, int extra) {
        String human = ijkWhatToHuman(what);
        if (human.isEmpty()) human = "未知错误";

        String extraHuman = ijkExtraToHuman(extra);
        if (!extraHuman.isEmpty()) {
            return "播放失败：" + human + "（" + extraHuman + "，what=" + what + " extra=" + extra + "）。可尝试切换到 libVLC。";
        }
        return "播放失败：" + human + "（what=" + what + " extra=" + extra + "）。可尝试切换到 libVLC。";
    }

    @NonNull
    private static String ijkWhatToHuman(int what) {
        // Ijkplayer/ffmpeg error mappings (common ones).
        // See also: IjkMediaPlayer's internal error codes in different builds.
        switch (what) {
            case -10000:
                return "无法打开媒体源";
            case -10001:
                return "解析媒体信息失败";
            case -10002:
                return "打开输出失败";
            case -10003:
                return "网络/IO 错误";
            case -10004:
                return "媒体数据异常";
            case -10005:
                return "不支持的媒体格式";
            case -10006:
                return "操作超时";
            case 100: // MEDIA_ERROR_SERVER_DIED
                return "媒体服务异常";
            case 1: // MEDIA_ERROR_UNKNOWN
                return "媒体播放异常";
            default:
                return "";
        }
    }

    @NonNull
    private static String ijkExtraToHuman(int extra) {
        if (extra == 0) return "";
        // Negative errno values are common for network errors.
        switch (extra) {
            case -110:
                return "连接超时";
            case -104:
                return "连接被重置";
            case -111:
                return "连接被拒绝";
            case -2:
                return "域名解析失败";
            default:
                return "";
        }
    }

    @NonNull
    private static String userAgent() {
        String v = BuildConfig.VERSION_NAME != null ? BuildConfig.VERSION_NAME.trim() : "";
        if (v.isEmpty()) v = "dev";
        return "LinPlayer/" + v;
    }

    private static void loadLibsOnce() {
        if (libsLoaded) return;
        synchronized (IjkPlayerCore.class) {
            if (libsLoaded) return;
            try {
                IjkMediaPlayer.loadLibrariesOnce(null);
            } catch (Throwable ignored) {
            }
            try {
                IjkMediaPlayer.native_profileBegin("libijkplayer.so");
            } catch (Throwable ignored) {
            }
            libsLoaded = true;
        }
    }
}

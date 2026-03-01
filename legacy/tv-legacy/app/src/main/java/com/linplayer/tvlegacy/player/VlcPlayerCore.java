package com.linplayer.tvlegacy.player;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.linplayer.tvlegacy.BuildConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

final class VlcPlayerCore implements PlayerCore {
    private final Context appContext;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final VLCVideoLayout videoLayout;

    @Nullable private Listener listener;
    @Nullable private LibVLC libVlc;
    @Nullable private MediaPlayer player;

    private boolean subtitlesEnabled = true;
    @Nullable private Integer selectedAudioId;
    @Nullable private Integer selectedSubtitleId;
    private long pendingSeekMs = 0L;
    private boolean playWhenReady = false;

    @NonNull private List<PlayerTrack> audioTracks = Collections.emptyList();
    @NonNull private List<PlayerTrack> subtitleTracks = Collections.emptyList();

    VlcPlayerCore(@NonNull Context context) {
        appContext = context.getApplicationContext();
        videoLayout = new VLCVideoLayout(context);
    }

    @Override
    @NonNull
    public PlayerCoreType getType() {
        return PlayerCoreType.VLC;
    }

    @Override
    @NonNull
    public String getDisplayName() {
        return getType().displayName;
    }

    @Override
    @NonNull
    public View getView() {
        return videoLayout;
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
        ensurePlayer();

        audioTracks = Collections.emptyList();
        subtitleTracks = Collections.emptyList();
        notifyTracksChanged();

        this.playWhenReady = playWhenReady;
        pendingSeekMs = Math.max(0L, startPositionMs);

        MediaPlayer p = player;
        LibVLC vlc = libVlc;
        if (p == null || vlc == null) {
            notifyFatalError("libVLC not available");
            return;
        }

        try {
            p.stop();
        } catch (Exception ignored) {
        }

        try {
            Media media = new Media(vlc, Uri.parse(url));
            media.addOption(":http-user-agent=" + userAgent());
            if (headers != null && !headers.isEmpty()) {
                // Best-effort: libVLC doesn't expose a first-class HTTP headers API on Android.
                // Some builds accept :http-header=Name: Value.
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    String k = e.getKey();
                    String v = e.getValue();
                    if (k == null || k.trim().isEmpty() || v == null || v.trim().isEmpty()) continue;
                    media.addOption(":http-header=" + k.trim() + ": " + v.trim());
                }
            }
            p.setMedia(media);
            media.release();
        } catch (Exception e) {
            notifyFatalError(String.valueOf(e.getMessage()));
            return;
        }

        if (this.playWhenReady) {
            try {
                p.play();
            } catch (Exception e) {
                notifyFatalError(String.valueOf(e.getMessage()));
            }
        }
    }

    @Override
    public void play() {
        playWhenReady = true;
        MediaPlayer p = player;
        if (p == null) return;
        try {
            p.play();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void pause() {
        playWhenReady = false;
        MediaPlayer p = player;
        if (p == null) return;
        try {
            p.pause();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void stop() {
        playWhenReady = false;
        MediaPlayer p = player;
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
        MediaPlayer p = player;
        if (p == null) return;
        try {
            p.setTime(ms);
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean isPlaying() {
        MediaPlayer p = player;
        if (p == null) return false;
        try {
            return p.isPlaying();
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public long getDurationMs() {
        MediaPlayer p = player;
        if (p == null) return 0L;
        try {
            long d = p.getLength();
            return d > 0L ? d : 0L;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    @Override
    public long getPositionMs() {
        MediaPlayer p = player;
        if (p == null) return 0L;
        try {
            long t = p.getTime();
            return t > 0L ? t : 0L;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    @Override
    public long getBufferedPositionMs() {
        // libVLC doesn't expose buffered position consistently on Android.
        return getPositionMs();
    }

    @Override
    @NonNull
    public List<PlayerTrack> getAudioTracks() {
        return audioTracks;
    }

    @Override
    @NonNull
    public List<PlayerTrack> getSubtitleTracks() {
        return subtitleTracks;
    }

    @Override
    public void selectAudioTrack(@Nullable Integer trackId) {
        selectedAudioId = trackId;
        applyTrackSelection();
    }

    @Override
    public void setSubtitlesEnabled(boolean enabled) {
        subtitlesEnabled = enabled;
        applyTrackSelection();
    }

    @Override
    public void selectSubtitleTrack(@Nullable Integer trackId) {
        selectedSubtitleId = trackId;
        applyTrackSelection();
    }

    @Override
    public void release() {
        MediaPlayer p = player;
        player = null;
        if (p != null) {
            try {
                p.setEventListener(null);
            } catch (Exception ignored) {
            }
            try {
                p.stop();
            } catch (Exception ignored) {
            }
            try {
                p.detachViews();
            } catch (Exception ignored) {
            }
            try {
                p.release();
            } catch (Exception ignored) {
            }
        }

        LibVLC vlc = libVlc;
        libVlc = null;
        if (vlc != null) {
            try {
                vlc.release();
            } catch (Exception ignored) {
            }
        }

        audioTracks = Collections.emptyList();
        subtitleTracks = Collections.emptyList();
    }

    private void ensurePlayer() {
        if (libVlc != null && player != null) return;

        try {
            LibVLC vlc = new LibVLC(appContext, new ArrayList<>());
            MediaPlayer mp = new MediaPlayer(vlc);
            mp.attachViews(videoLayout, null, false, false);
            mp.setEventListener(
                    event -> {
                        if (event == null) return;
                        int type = event.type;
                        if (type == MediaPlayer.Event.Playing) {
                            applyPendingSeek();
                            refreshTracks();
                            applyTrackSelection();
                        } else if (type == MediaPlayer.Event.EncounteredError) {
                            notifyFatalError("libVLC encountered error");
                        } else if (type == MediaPlayer.Event.EndReached) {
                            refreshTracks();
                        }
                    });
            libVlc = vlc;
            player = mp;
        } catch (Exception e) {
            notifyFatalError(String.valueOf(e.getMessage()));
        }
    }

    private void applyPendingSeek() {
        long seek = pendingSeekMs;
        if (seek <= 0L) return;
        MediaPlayer p = player;
        if (p == null) return;
        try {
            p.setTime(seek);
        } catch (Exception ignored) {
        }
    }

    private void applyTrackSelection() {
        MediaPlayer p = player;
        if (p == null) return;

        if (!subtitlesEnabled) {
            try {
                p.setSpuTrack(-1);
            } catch (Exception ignored) {
            }
        } else if (selectedSubtitleId != null) {
            try {
                p.setSpuTrack(selectedSubtitleId);
            } catch (Exception ignored) {
            }
        }

        if (selectedAudioId != null) {
            try {
                p.setAudioTrack(selectedAudioId);
            } catch (Exception ignored) {
            }
        }
    }

    private void refreshTracks() {
        MediaPlayer p = player;
        if (p == null) return;

        List<PlayerTrack> nextAudio = toTracks(p.getAudioTracks(), false);
        List<PlayerTrack> nextSubs = toTracks(p.getSpuTracks(), true);

        audioTracks = nextAudio;
        subtitleTracks = nextSubs;
        notifyTracksChanged();
    }

    @NonNull
    private static List<PlayerTrack> toTracks(
            @Nullable MediaPlayer.TrackDescription[] list, boolean allowDisabledTrack) {
        if (list == null || list.length == 0) return Collections.emptyList();
        List<PlayerTrack> out = new ArrayList<>();
        for (MediaPlayer.TrackDescription d : list) {
            if (d == null) continue;
            int id = d.id;
            if (!allowDisabledTrack && id < 0) continue;
            if (id == -1) continue; // "Disable" is handled by the UI.
            String name = d.name != null ? d.name.trim() : "";
            if (name.isEmpty()) name = "Track " + id;
            out.add(new PlayerTrack(id, name));
        }
        return out;
    }

    @NonNull
    private static String userAgent() {
        return "LinPlayer/" + BuildConfig.VERSION_NAME;
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

    private void notifyFatalError(@NonNull String message) {
        Listener l = listener;
        if (l == null) return;
        main.post(
                () -> {
                    Listener l2 = listener;
                    if (l2 != null) l2.onFatalError(message);
                });
    }
}

package com.linplayer.tvlegacy.player;

import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.List;
import java.util.Map;

public interface PlayerCore {
    interface Listener {
        void onTracksChanged();

        void onFatalError(@NonNull String message);
    }

    @NonNull
    PlayerCoreType getType();

    @NonNull
    String getDisplayName();

    @NonNull
    View getView();

    void setListener(@Nullable Listener listener);

    void open(
            @NonNull String url,
            @Nullable Map<String, String> headers,
            long startPositionMs,
            boolean playWhenReady);

    void play();

    void pause();

    void stop();

    void seekTo(long positionMs);

    boolean isPlaying();

    long getDurationMs();

    long getPositionMs();

    long getBufferedPositionMs();

    @NonNull
    List<PlayerTrack> getAudioTracks();

    @NonNull
    List<PlayerTrack> getSubtitleTracks();

    void selectAudioTrack(@Nullable Integer trackId);

    void setSubtitlesEnabled(boolean enabled);

    void selectSubtitleTrack(@Nullable Integer trackId);

    void release();
}


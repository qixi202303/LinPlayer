package com.linplayer.tvlegacy.player;

import androidx.annotation.NonNull;

public final class PlayerTrack {
    public final int id;
    @NonNull public final String label;

    public PlayerTrack(int id, @NonNull String label) {
        this.id = id;
        this.label = label != null ? label : "";
    }
}


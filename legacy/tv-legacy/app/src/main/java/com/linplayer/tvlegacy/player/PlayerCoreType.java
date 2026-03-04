package com.linplayer.tvlegacy.player;

import androidx.annotation.NonNull;

public enum PlayerCoreType {
    VLC("vlc", "libVLC");

    @NonNull public final String id;
    @NonNull public final String displayName;

    PlayerCoreType(@NonNull String id, @NonNull String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    @NonNull
    public static PlayerCoreType fromId(@NonNull String id, @NonNull PlayerCoreType fallback) {
        String v = id != null ? id.trim().toLowerCase() : "";
        for (PlayerCoreType t : values()) {
            if (t.id.equals(v)) return t;
        }
        return fallback;
    }
}

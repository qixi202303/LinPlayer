package com.linplayer.tvlegacy.player;

import android.content.Context;
import androidx.annotation.NonNull;

public final class PlayerCores {
    private PlayerCores() {}

    @NonNull
    public static PlayerCore create(@NonNull Context context, @NonNull PlayerCoreType type) {
        return new VlcPlayerCore(context);
    }
}

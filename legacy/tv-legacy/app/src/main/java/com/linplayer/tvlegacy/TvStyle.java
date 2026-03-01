package com.linplayer.tvlegacy;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;

final class TvStyle {
    private TvStyle() {}

    static int panelAlpha255(Context context) {
        int percent = AppPrefs.getUiPanelAlphaPercent(context);
        return Math.round(255f * (percent / 100f));
    }

    static int backgroundBlurRadius(Context context) {
        return AppPrefs.getUiBackgroundBlur(context);
    }

    static void applyPanelAlpha(View view, int alpha255) {
        if (view == null) return;
        Drawable bg = view.getBackground();
        if (bg == null) return;
        try {
            bg = bg.mutate();
            bg.setAlpha(clamp(alpha255, 0, 255));
            view.setBackground(bg);
        } catch (Exception ignored) {
            // ignore
        }
    }

    private static int clamp(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }
}


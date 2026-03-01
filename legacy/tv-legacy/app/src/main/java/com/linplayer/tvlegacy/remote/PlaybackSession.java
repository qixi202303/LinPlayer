package com.linplayer.tvlegacy.remote;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import com.linplayer.tvlegacy.player.PlayerCore;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONException;
import org.json.JSONObject;

public final class PlaybackSession {
    private static final Object LOCK = new Object();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    @Nullable private static PlayerCore player;
    private static String title = "";

    private PlaybackSession() {}

    public static void attach(@Nullable PlayerCore p, String titleText) {
        if (p == null) return;
        synchronized (LOCK) {
            player = p;
            title = titleText != null ? titleText : "";
        }
    }

    public static void detach(@Nullable PlayerCore p) {
        synchronized (LOCK) {
            if (player == p) {
                player = null;
                title = "";
            }
        }
    }

    public static JSONObject status() {
        final JSONObject[] out = new JSONObject[1];
        final CountDownLatch latch = new CountDownLatch(1);
        MAIN.post(
                () -> {
                    out[0] = buildStatusLocked();
                    latch.countDown();
                });
        try {
            // Best-effort: remote calls should respond quickly.
            latch.await(250, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
        }
        JSONObject v = out[0];
        return v != null ? v : jsonError("timeout");
    }

    public static JSONObject control(String action, long value) {
        final JSONObject[] out = new JSONObject[1];
        final CountDownLatch latch = new CountDownLatch(1);
        final String a = action != null ? action.trim().toLowerCase() : "";
        MAIN.post(
                () -> {
                    out[0] = applyControlLocked(a, value);
                    latch.countDown();
                });
        try {
            latch.await(600, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
        }
        JSONObject v = out[0];
        return v != null ? v : jsonError("timeout");
    }

    private static JSONObject applyControlLocked(String action, long value) {
        PlayerCore p;
        synchronized (LOCK) {
            p = player;
        }
        if (p == null) return inactive();

        try {
            if ("toggle".equals(action)) {
                if (p.isPlaying()) {
                    p.pause();
                } else {
                    p.play();
                }
            } else if ("play".equals(action)) {
                p.play();
            } else if ("pause".equals(action)) {
                p.pause();
            } else if ("stop".equals(action)) {
                p.stop();
            } else if ("seekbyms".equals(action) || "seek_by_ms".equals(action) || "seekby".equals(action)) {
                long pos = p.getPositionMs();
                long dur = p.getDurationMs();
                long next = pos + value;
                if (next < 0) next = 0;
                if (dur > 0 && next > dur) next = dur;
                p.seekTo(next);
            } else if ("seektoms".equals(action) || "seek_to_ms".equals(action)) {
                long dur = p.getDurationMs();
                long next = value;
                if (next < 0) next = 0;
                if (dur > 0 && next > dur) next = dur;
                p.seekTo(next);
            } else {
                return jsonError("unknown action");
            }
            return buildStatusLocked();
        } catch (Exception e) {
            return jsonError(String.valueOf(e.getMessage()));
        }
    }

    private static JSONObject buildStatusLocked() {
        PlayerCore p;
        String t;
        synchronized (LOCK) {
            p = player;
            t = title;
        }
        if (p == null) return inactive();

        long pos = 0;
        long dur = 0;
        boolean playing = false;
        try {
            pos = p.getPositionMs();
            dur = Math.max(0, p.getDurationMs());
            playing = p.isPlaying();
        } catch (Exception ignored) {
        }

        try {
            JSONObject o = new JSONObject();
            o.put("ok", true);
            o.put("active", true);
            o.put("title", t != null ? t : "");
            o.put("playing", playing);
            o.put("positionMs", pos);
            o.put("durationMs", dur);
            return o;
        } catch (JSONException e) {
            return jsonError("json error");
        }
    }

    private static JSONObject inactive() {
        try {
            JSONObject o = new JSONObject();
            o.put("ok", true);
            o.put("active", false);
            return o;
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private static JSONObject jsonError(String msg) {
        try {
            JSONObject o = new JSONObject();
            o.put("ok", false);
            o.put("error", msg != null ? msg : "error");
            return o;
        } catch (JSONException e) {
            return new JSONObject();
        }
    }
}

package com.linplayer.tvlegacy.backend;

import android.content.Context;
import com.linplayer.tvlegacy.servers.ServerConfig;
import com.linplayer.tvlegacy.servers.ServerStore;

public final class Backends {
    private static final Object LOCK = new Object();
    private static MediaBackend media;
    private static String mediaKey;

    private Backends() {}

    public static MediaBackend media(Context context) {
        if (context == null) throw new IllegalArgumentException("context == null");
        Context appContext = context.getApplicationContext();
        ServerConfig active = ServerStore.getActive(appContext);
        String type = active != null ? safe(active.type).toLowerCase() : "demo";
        String baseUrl = active != null ? safe(active.baseUrl) : "";
        String apiPrefix = active != null ? safe(active.apiPrefix) : "";
        String apiKey = active != null ? safe(active.apiKey) : "";
        String userId = active != null ? safe(active.userId) : "";
        String username = active != null ? safe(active.username) : "";
        String password = active != null ? safe(active.password) : "";

        String key =
                type
                        + "|"
                        + baseUrl
                        + "|"
                        + apiPrefix
                        + "|"
                        + Integer.toHexString(apiKey.hashCode())
                        + "|"
                        + safe(userId)
                        + "|"
                        + Integer.toHexString((username + ":" + password).hashCode());
        synchronized (LOCK) {
            if (media != null && key.equals(mediaKey)) {
                return media;
            }
            mediaKey = key;

            if ("emby".equals(type)) {
                media = new EmbyLikeMediaBackend(appContext, baseUrl, apiPrefix, apiKey, userId, "Emby");
            } else if ("jellyfin".equals(type)) {
                media =
                        new EmbyLikeMediaBackend(appContext, baseUrl, apiPrefix, apiKey, userId, "Jellyfin");
            } else if ("plex".equals(type)) {
                media = new PlexMediaBackend(appContext, baseUrl, apiKey);
            } else if ("webdav".equals(type)) {
                media = new WebDavMediaBackend(appContext, baseUrl, username, password);
            } else {
                media = new DemoMediaBackend();
            }

            return media;
        }
    }

    private static String safe(String s) {
        return s != null ? s.trim() : "";
    }
}

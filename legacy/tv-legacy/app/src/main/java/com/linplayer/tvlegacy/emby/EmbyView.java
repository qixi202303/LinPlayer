package com.linplayer.tvlegacy.emby;

public final class EmbyView {
    public final String id;
    public final String name;
    public final String imageUrl;

    public EmbyView(String id, String name, String imageUrl) {
        this.id = safeTrim(id);
        this.name = safeTrim(name);
        this.imageUrl = safeTrim(imageUrl);
    }

    private static String safeTrim(String s) {
        return s != null ? s.trim() : "";
    }
}


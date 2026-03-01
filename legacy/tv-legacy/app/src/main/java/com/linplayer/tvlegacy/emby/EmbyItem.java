package com.linplayer.tvlegacy.emby;

public final class EmbyItem {
    public final String id;
    public final String type;
    public final String name;

    public final String seriesId;
    public final String seriesName;
    public final int seasonNumber;
    public final int episodeNumber;

    public final String imageUrl;
    public final String rating;
    public final String yearOrDate;

    public final long playbackPositionMs;

    public EmbyItem(
            String id,
            String type,
            String name,
            String seriesId,
            String seriesName,
            int seasonNumber,
            int episodeNumber,
            String imageUrl,
            String rating,
            String yearOrDate,
            long playbackPositionMs) {
        this.id = safeTrim(id);
        this.type = safeTrim(type);
        this.name = safeTrim(name);
        this.seriesId = safeTrim(seriesId);
        this.seriesName = safeTrim(seriesName);
        this.seasonNumber = Math.max(0, seasonNumber);
        this.episodeNumber = Math.max(0, episodeNumber);
        this.imageUrl = safeTrim(imageUrl);
        this.rating = safeTrim(rating);
        this.yearOrDate = safeTrim(yearOrDate);
        this.playbackPositionMs = Math.max(0L, playbackPositionMs);
    }

    public boolean isType(String t) {
        if (t == null) return false;
        return type.equalsIgnoreCase(t.trim());
    }

    private static String safeTrim(String s) {
        return s != null ? s.trim() : "";
    }
}


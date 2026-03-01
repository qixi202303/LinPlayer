package com.linplayer.tvlegacy;

import com.linplayer.tvlegacy.emby.EmbyItem;
import com.linplayer.tvlegacy.emby.EmbyView;
import java.util.Collections;
import java.util.List;

final class HomeSection {
    static final int TYPE_RESUME = 1;
    static final int TYPE_VIEWS = 2;
    static final int TYPE_VIEW_ITEMS = 3;

    final int type;
    final String title;
    final List<EmbyItem> items;
    final List<EmbyView> views;

    private HomeSection(int type, String title, List<EmbyItem> items, List<EmbyView> views) {
        this.type = type;
        this.title = safeTrim(title);
        this.items = items != null ? items : Collections.emptyList();
        this.views = views != null ? views : Collections.emptyList();
    }

    static HomeSection resume(String title, List<EmbyItem> items) {
        return new HomeSection(TYPE_RESUME, title, items, null);
    }

    static HomeSection views(String title, List<EmbyView> views) {
        return new HomeSection(TYPE_VIEWS, title, null, views);
    }

    static HomeSection viewItems(String title, List<EmbyItem> items) {
        return new HomeSection(TYPE_VIEW_ITEMS, title, items, null);
    }

    private static String safeTrim(String s) {
        return s != null ? s.trim() : "";
    }
}


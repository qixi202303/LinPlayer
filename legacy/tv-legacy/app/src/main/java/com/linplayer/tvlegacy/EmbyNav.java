package com.linplayer.tvlegacy;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;
import com.linplayer.tvlegacy.emby.EmbyClient;
import com.linplayer.tvlegacy.emby.EmbyItem;
import com.linplayer.tvlegacy.emby.EmbyView;
import com.linplayer.tvlegacy.servers.ServerConfig;
import com.linplayer.tvlegacy.servers.ServerStore;
import java.util.Locale;

final class EmbyNav {
    private EmbyNav() {}

    static void openItem(Activity activity, EmbyItem item) {
        if (activity == null || item == null) return;
        ServerConfig active = ServerStore.getActive(activity);
        if (active == null || safe(active.baseUrl).isEmpty() || safe(active.apiKey).isEmpty()) {
            Toast.makeText(activity, "Missing server config", Toast.LENGTH_LONG).show();
            return;
        }

        EmbyClient client =
                new EmbyClient(activity, active.baseUrl, active.apiPrefix, active.apiKey, active.userId);

        if (item.isType("Series")) {
            Intent i = new Intent(activity, ShowDetailActivity.class);
            i.putExtra(ShowDetailActivity.EXTRA_SHOW_ID, item.id);
            activity.startActivity(i);
            return;
        }

        if (item.isType("Movie") || item.isType("Episode")) {
            String url = client.streamUrl(item.id);
            if (url.isEmpty()) {
                Toast.makeText(activity, "Missing media url", Toast.LENGTH_LONG).show();
                return;
            }
            String title = buildPlayTitle(item);
            Intent i = new Intent(activity, PlayerActivity.class);
            i.putExtra(PlayerActivity.EXTRA_TITLE, title);
            i.putExtra(PlayerActivity.EXTRA_URL, url);
            if (item.playbackPositionMs > 0L) {
                i.putExtra(PlayerActivity.EXTRA_POSITION_MS, item.playbackPositionMs);
            }
            activity.startActivity(i);
            return;
        }

        Toast.makeText(activity, "Unsupported item type: " + safe(item.type), Toast.LENGTH_SHORT)
                .show();
    }

    static void openView(Activity activity, EmbyView view) {
        if (activity == null || view == null) return;
        if (safe(view.id).isEmpty()) return;
        Intent i = new Intent(activity, LibraryDetailActivity.class);
        i.putExtra(LibraryDetailActivity.EXTRA_VIEW_ID, view.id);
        i.putExtra(LibraryDetailActivity.EXTRA_VIEW_NAME, view.name);
        activity.startActivity(i);
    }

    private static String buildPlayTitle(EmbyItem item) {
        if (item == null) return "";
        if (item.isType("Episode")) {
            String show = safe(item.seriesName);
            String se = formatSeasonEpisode(item.seasonNumber, item.episodeNumber);
            if (!show.isEmpty() && !se.isEmpty()) return show + " " + se;
            if (!show.isEmpty()) return show;
        }
        return safe(item.name);
    }

    private static String formatSeasonEpisode(int season, int episode) {
        if (season <= 0 && episode <= 0) return "";
        if (season <= 0) return "E" + episode;
        if (episode <= 0) return "S" + season;
        return String.format(Locale.US, "S%02dE%02d", season, episode);
    }

    private static String safe(String s) {
        return s != null ? s.trim() : "";
    }
}

package com.linplayer.tvlegacy;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.linplayer.tvlegacy.emby.EmbyItem;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class EmbyCardAdapter extends RecyclerView.Adapter<EmbyCardAdapter.Vh> {
    static final int MODE_NORMAL = 0;
    static final int MODE_RESUME = 1;

    interface Listener {
        void onItemClicked(EmbyItem item);
    }

    private final Listener listener;
    private List<EmbyItem> items = Collections.emptyList();
    private int mode = MODE_NORMAL;
    private int itemWidthDp = 0;
    private int panelAlpha255 = 255;

    EmbyCardAdapter(Listener listener) {
        this.listener = listener;
    }

    void setData(List<EmbyItem> items, int mode, int itemWidthDp, int panelAlpha255) {
        this.items = items != null ? items : Collections.emptyList();
        this.mode = mode;
        this.itemWidthDp = Math.max(0, itemWidthDp);
        this.panelAlpha255 = clamp(panelAlpha255, 0, 255);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Vh onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v =
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_media_card, parent, false);
        if (itemWidthDp > 0) {
            int px = dpToPx(parent.getContext(), itemWidthDp);
            RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) v.getLayoutParams();
            lp.width = px;
            v.setLayoutParams(lp);
        }
        return new Vh(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Vh holder, int position) {
        EmbyItem item = items.get(position);

        TvStyle.applyPanelAlpha(holder.itemView, panelAlpha255);

        holder.title.setText(buildTitle(item, mode));
        String subtitle = buildSubtitle(item, mode);
        if (subtitle.isEmpty()) {
            holder.subtitle.setVisibility(View.GONE);
        } else {
            holder.subtitle.setVisibility(View.VISIBLE);
            holder.subtitle.setText(subtitle);
        }

        ImageLoader.load(holder.image, item != null ? item.imageUrl : "", 640);

        holder.itemView.setOnClickListener(v -> listener.onItemClicked(item));
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    static final class Vh extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView title;
        final TextView subtitle;

        Vh(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.card_image);
            title = itemView.findViewById(R.id.card_title);
            subtitle = itemView.findViewById(R.id.card_subtitle);
        }
    }

    private static String buildTitle(EmbyItem item, int mode) {
        if (item == null) return "";
        if (mode == MODE_RESUME) {
            if (item.isType("Episode")) {
                String show = safe(item.seriesName);
                if (show.isEmpty()) show = safe(item.name);
                String se = formatSeasonEpisode(item.seasonNumber, item.episodeNumber);
                return se.isEmpty() ? show : (show + " " + se);
            }
            return safe(item.name);
        }

        if (item.isType("Episode")) {
            String show = safe(item.seriesName);
            String se = formatSeasonEpisode(item.seasonNumber, item.episodeNumber);
            if (!show.isEmpty() && !se.isEmpty()) return show + " " + se;
            if (!show.isEmpty()) return show;
        }
        return safe(item.name);
    }

    private static String buildSubtitle(EmbyItem item, int mode) {
        if (item == null) return "";
        if (mode == MODE_RESUME) {
            if (item.playbackPositionMs <= 0L) return "";
            return "观看至 " + formatTimeMs(item.playbackPositionMs);
        }

        String rating = safe(item.rating);
        String year = safe(item.yearOrDate);
        StringBuilder sb = new StringBuilder();
        if (!rating.isEmpty()) sb.append("★").append(rating);
        if (!year.isEmpty()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(year);
        }
        return sb.toString();
    }

    private static String formatSeasonEpisode(int season, int episode) {
        if (season <= 0 && episode <= 0) return "";
        if (season <= 0) return "E" + episode;
        if (episode <= 0) return "S" + season;
        return String.format(Locale.US, "S%02dE%02d", season, episode);
    }

    private static String formatTimeMs(long ms) {
        long totalSec = Math.max(0L, ms) / 1000L;
        long s = totalSec % 60L;
        long m = (totalSec / 60L) % 60L;
        long h = totalSec / 3600L;
        if (h > 0L) {
            return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        }
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    private static int dpToPx(Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private static int clamp(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }

    private static String safe(String s) {
        return s != null ? s.trim() : "";
    }
}


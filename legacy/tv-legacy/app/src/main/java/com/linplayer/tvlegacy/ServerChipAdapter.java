package com.linplayer.tvlegacy;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.linplayer.tvlegacy.servers.ServerConfig;
import java.util.ArrayList;
import java.util.List;

final class ServerChipAdapter extends RecyclerView.Adapter<ServerChipAdapter.Vh> {
    interface Listener {
        void onServerClicked(ServerConfig server);
    }

    private final List<ServerConfig> servers = new ArrayList<>();
    private final Listener listener;
    private int panelAlpha255 = 255;

    ServerChipAdapter(Listener listener) {
        this.listener = listener;
    }

    void setData(List<ServerConfig> list, int panelAlpha255) {
        servers.clear();
        if (list != null) servers.addAll(list);
        this.panelAlpha255 = clamp(panelAlpha255, 0, 255);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Vh onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v =
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_server_chip, parent, false);
        return new Vh(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Vh holder, int position) {
        ServerConfig c = servers.get(position);
        String name = c != null ? c.effectiveName() : "Server";
        holder.name.setText(name);

        TvStyle.applyPanelAlpha(holder.itemView, panelAlpha255);

        String iconUrl = c != null ? safe(c.iconUrl) : "";
        if (!iconUrl.isEmpty()) {
            holder.avatarText.setVisibility(View.GONE);
            holder.avatarImg.setVisibility(View.VISIBLE);
            ImageLoader.load(holder.avatarImg, iconUrl, 96);
        } else {
            holder.avatarImg.setVisibility(View.GONE);
            holder.avatarImg.setImageDrawable(null);
            holder.avatarText.setVisibility(View.VISIBLE);
            holder.avatarText.setText(avatarLetter(name));
        }

        holder.itemView.setOnClickListener(v -> listener.onServerClicked(c));
    }

    @Override
    public int getItemCount() {
        return servers.size();
    }

    static final class Vh extends RecyclerView.ViewHolder {
        final ImageView avatarImg;
        final TextView avatarText;
        final TextView name;

        Vh(@NonNull View itemView) {
            super(itemView);
            avatarImg = itemView.findViewById(R.id.server_chip_avatar_img);
            avatarText = itemView.findViewById(R.id.server_chip_avatar_text);
            name = itemView.findViewById(R.id.server_chip_name);
        }
    }

    private static String safe(String s) {
        return s != null ? s.trim() : "";
    }

    private static String avatarLetter(String name) {
        String n = safe(name);
        if (n.isEmpty()) return "?";
        String first = n.substring(0, 1);
        return first.toUpperCase();
    }

    private static int clamp(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }
}


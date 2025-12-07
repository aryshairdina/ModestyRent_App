package com.example.modestyrent_app;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProductMediaAdapter extends RecyclerView.Adapter<ProductMediaAdapter.MediaViewHolder> {

    private static final String TAG = "ProductMediaAdapter";

    private final Context context;
    private final List<String> mediaUrls;   // images + videos
    private final Map<Integer, ExoPlayer> playerMap = new HashMap<>();

    public ProductMediaAdapter(Context context, List<String> mediaUrls) {
        this.context = context;
        this.mediaUrls = mediaUrls != null ? mediaUrls : new ArrayList<>();
    }

    @NonNull
    @Override
    public MediaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_image_slider, parent, false);
        return new MediaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MediaViewHolder holder, int position) {
        String url = mediaUrls.get(position);

        // Clean up any old player on this view
        ExoPlayer existingPlayer = (ExoPlayer) holder.videoPlayerView.getPlayer();
        if (existingPlayer != null) {
            existingPlayer.setPlayWhenReady(false);
            existingPlayer.release();
            removePlayerFromMap(existingPlayer);
            holder.videoPlayerView.setPlayer(null);
        }

        if (isVideoUrl(url)) {
            Log.d(TAG, "Binding VIDEO at position " + position + " url=" + url);

            holder.imageView.setVisibility(View.GONE);
            holder.videoPlayerView.setVisibility(View.VISIBLE);

            ExoPlayer player = new ExoPlayer.Builder(context).build();
            holder.videoPlayerView.setPlayer(player);

            MediaItem mediaItem = MediaItem.fromUri(url);
            player.setMediaItem(mediaItem);
            player.setRepeatMode(Player.REPEAT_MODE_ONE); // loop
            player.setVolume(1f);                         // sound ON (set 0f if you want mute)
            player.prepare();
            player.setPlayWhenReady(false);               // will be started by handlePageSelected

            playerMap.put(position, player);

        } else {
            Log.d(TAG, "Binding IMAGE at position " + position + " url=" + url);

            holder.videoPlayerView.setVisibility(View.GONE);
            holder.imageView.setVisibility(View.VISIBLE);

            Glide.with(context)
                    .load(url)
                    .centerCrop()
                    .placeholder(R.drawable.ic_person)
                    .into(holder.imageView);
        }
    }

    @Override
    public void onViewRecycled(@NonNull MediaViewHolder holder) {
        super.onViewRecycled(holder);
        ExoPlayer player = (ExoPlayer) holder.videoPlayerView.getPlayer();
        if (player != null) {
            player.setPlayWhenReady(false);
            player.release();
            removePlayerFromMap(player);
            holder.videoPlayerView.setPlayer(null);
        }
    }

    @Override
    public int getItemCount() {
        return mediaUrls != null ? mediaUrls.size() : 0;
    }

    private void removePlayerFromMap(ExoPlayer player) {
        Integer keyToRemove = null;
        for (Map.Entry<Integer, ExoPlayer> entry : playerMap.entrySet()) {
            if (entry.getValue() == player) {
                keyToRemove = entry.getKey();
                break;
            }
        }
        if (keyToRemove != null) {
            playerMap.remove(keyToRemove);
        }
    }

    /**
     * Page in ViewPager2 selected: only that page's video should play.
     */
    public void handlePageSelected(int selectedPosition) {
        for (Map.Entry<Integer, ExoPlayer> entry : playerMap.entrySet()) {
            int pos = entry.getKey();
            ExoPlayer player = entry.getValue();
            if (pos == selectedPosition) {
                player.setPlayWhenReady(true);
                player.setVolume(1f);
            } else {
                player.setPlayWhenReady(false);
                player.setVolume(0f);
            }
        }
    }

    public void pauseAllPlayers() {
        for (ExoPlayer player : playerMap.values()) {
            player.setPlayWhenReady(false);
            player.setVolume(0f);
        }
    }

    public void releaseAllPlayers() {
        for (ExoPlayer player : playerMap.values()) {
            player.setPlayWhenReady(false);
            player.release();
        }
        playerMap.clear();
    }

    private boolean isVideoUrl(String url) {
        if (url == null) return false;
        String clean = url;
        int qIndex = url.indexOf("?");
        if (qIndex >= 0) {
            clean = url.substring(0, qIndex);
        }
        clean = clean.toLowerCase();
        return clean.endsWith(".mp4")
                || clean.endsWith(".mkv")
                || clean.endsWith(".webm")
                || clean.endsWith(".3gp");
    }

    // --- ViewHolder ---
    public static class MediaViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        PlayerView videoPlayerView;

        public MediaViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.ivSlider);
            videoPlayerView = itemView.findViewById(R.id.video_player_view);
        }
    }
}

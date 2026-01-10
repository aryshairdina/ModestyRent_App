package com.example.modestyrent_app;

import android.app.Dialog;
import android.content.Context;
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

    private final Context context;
    private final List<String> mediaUrls;
    private final Map<Integer, ExoPlayer> playerMap = new HashMap<>();

    public ProductMediaAdapter(Context context, List<String> mediaUrls) {
        this.context = context;
        this.mediaUrls = mediaUrls != null ? mediaUrls : new ArrayList<>();
    }

    // ---------------- VIEW HOLDER ----------------

    @NonNull
    @Override
    public MediaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_image_slider, parent, false);
        return new MediaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MediaViewHolder holder, int position) {
        String url = mediaUrls.get(position);

        // Clear old listeners (IMPORTANT)
        holder.imageView.setOnClickListener(null);
        holder.videoPlayerView.setOnClickListener(null);

        // Clean up old player on recycled view
        ExoPlayer oldPlayer = (ExoPlayer) holder.videoPlayerView.getPlayer();
        if (oldPlayer != null) {
            oldPlayer.setPlayWhenReady(false);
            oldPlayer.release();
            removePlayerFromMap(oldPlayer);
            holder.videoPlayerView.setPlayer(null);
        }

        if (isVideoUrl(url)) {
            // -------- VIDEO --------
            holder.imageView.setVisibility(View.GONE);
            holder.videoPlayerView.setVisibility(View.VISIBLE);

            ExoPlayer player = new ExoPlayer.Builder(context).build();
            holder.videoPlayerView.setPlayer(player);

            player.setMediaItem(MediaItem.fromUri(url));
            player.setRepeatMode(Player.REPEAT_MODE_ONE);
            player.prepare();
            player.setPlayWhenReady(false);

            playerMap.put(position, player);

            // Fullscreen video
            holder.videoPlayerView.setOnClickListener(v -> {
                pauseAllPlayers();
                showFullscreenMedia(url, true);
            });

        } else {
            // -------- IMAGE --------
            holder.videoPlayerView.setVisibility(View.GONE);
            holder.imageView.setVisibility(View.VISIBLE);

            Glide.with(context)
                    .load(url)
                    .fitCenter()
                    .into(holder.imageView);

            // Fullscreen image
            holder.imageView.setOnClickListener(v ->
                    showFullscreenMedia(url, false)
            );
        }
    }

    @Override
    public int getItemCount() {
        return mediaUrls.size();
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

    // ---------------- FULLSCREEN DIALOG ----------------

    private void showFullscreenMedia(String mediaUrl, boolean isVideo) {

        Dialog dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_fullscreen_media);

        ImageView fullscreenImage = dialog.findViewById(R.id.fullscreenImage);
        PlayerView fullscreenVideo = dialog.findViewById(R.id.fullscreenVideo);
        ImageView btnClose = dialog.findViewById(R.id.btnClose);

        ExoPlayer fullscreenPlayer = null;

        if (isVideo) {
            fullscreenImage.setVisibility(View.GONE);
            fullscreenVideo.setVisibility(View.VISIBLE);

            fullscreenPlayer = new ExoPlayer.Builder(context).build();
            fullscreenVideo.setPlayer(fullscreenPlayer);

            fullscreenPlayer.setMediaItem(MediaItem.fromUri(mediaUrl));
            fullscreenPlayer.prepare();
            fullscreenPlayer.setPlayWhenReady(true);

        } else {
            fullscreenVideo.setVisibility(View.GONE);
            fullscreenImage.setVisibility(View.VISIBLE);

            Glide.with(context)
                    .load(mediaUrl)
                    .fitCenter()
                    .into(fullscreenImage);
        }

        ExoPlayer finalPlayer = fullscreenPlayer;

        btnClose.setOnClickListener(v -> {
            if (finalPlayer != null) {
                finalPlayer.stop();
                finalPlayer.release();
            }
            dialog.dismiss();
        });

        dialog.setOnDismissListener(d -> {
            if (finalPlayer != null) {
                finalPlayer.stop();
                finalPlayer.release();
            }
        });

        dialog.show();
    }

    // ---------------- PLAYER CONTROL ----------------

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

    // ---------------- UTILS ----------------

    private boolean isVideoUrl(String url) {
        if (url == null) return false;
        String clean = url.split("\\?")[0].toLowerCase();
        return clean.endsWith(".mp4")
                || clean.endsWith(".mkv")
                || clean.endsWith(".webm")
                || clean.endsWith(".3gp");
    }

    // ---------------- VIEW HOLDER ----------------

    static class MediaViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        PlayerView videoPlayerView;

        MediaViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.ivSlider);
            videoPlayerView = itemView.findViewById(R.id.video_player_view);
        }
    }
}

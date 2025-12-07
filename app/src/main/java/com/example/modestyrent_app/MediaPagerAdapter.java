package com.example.modestyrent_app;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.ui.PlayerView;

import java.util.ArrayList;
import java.util.List;

public class MediaPagerAdapter extends RecyclerView.Adapter<MediaPagerAdapter.MediaViewHolder> {

    private List<Uri> mediaUris;
    private final Context context;
    private final List<ExoPlayer> activePlayers = new ArrayList<>();

    public MediaPagerAdapter(Context context, List<Uri> mediaUris) {
        this.context = context;
        this.mediaUris = new ArrayList<>(mediaUris);
    }

    @NonNull
    @Override
    public MediaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_media, parent, false);
        return new MediaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MediaViewHolder holder, int position) {
        Uri uri = mediaUris.get(position);
        String mimeType = context.getContentResolver().getType(uri);

        if (mimeType != null && mimeType.startsWith("video")) {
            // Show video player
            holder.videoPlayerView.setVisibility(View.VISIBLE);
            holder.mediaImageView.setVisibility(View.GONE);

            ExoPlayer player = new ExoPlayer.Builder(context).build();
            holder.videoPlayerView.setPlayer(player);

            MediaItem mediaItem = MediaItem.fromUri(uri);
            player.setMediaItem(mediaItem);
            player.prepare();
            player.setPlayWhenReady(true); // AUTOPLAY

            activePlayers.add(player);

        } else {
            // Show image
            holder.mediaImageView.setVisibility(View.VISIBLE);
            holder.videoPlayerView.setVisibility(View.GONE);

            Glide.with(context)
                    .load(uri)
                    .centerCrop()
                    .into(holder.mediaImageView);
        }
    }

    @Override
    public void onViewRecycled(@NonNull MediaViewHolder holder) {
        super.onViewRecycled(holder);

        ExoPlayer player = (ExoPlayer) holder.videoPlayerView.getPlayer();
        if (player != null) {
            player.setPlayWhenReady(false);
            player.release();
            activePlayers.remove(player);
        }
    }

    @Override
    public int getItemCount() {
        return mediaUris.size();
    }

    public void updateMedia(List<Uri> newUris) {
        this.mediaUris = new ArrayList<>(newUris);
        notifyDataSetChanged();
    }

    public void releasePlayers() {
        for (ExoPlayer player : activePlayers) {
            player.setPlayWhenReady(false);
            player.release();
        }
        activePlayers.clear();
    }

    static class MediaViewHolder extends RecyclerView.ViewHolder {
        ImageView mediaImageView;
        PlayerView videoPlayerView;

        public MediaViewHolder(@NonNull View itemView) {
            super(itemView);
            mediaImageView = itemView.findViewById(R.id.mediaImageView);
            videoPlayerView = itemView.findViewById(R.id.videoPlayerView);
        }
    }
}

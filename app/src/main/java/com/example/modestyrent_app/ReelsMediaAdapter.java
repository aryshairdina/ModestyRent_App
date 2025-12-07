package com.example.modestyrent_app;

import android.content.Context;
// Removed: import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

// Adapter for the horizontal scrolling media (images/videos) within a single Reel item.
public class ReelsMediaAdapter extends RecyclerView.Adapter<ReelsMediaAdapter.MediaViewHolder> {

    private final Context context;
    private final List<String> mediaUrls;

    public ReelsMediaAdapter(Context context, List<String> mediaUrls) {
        this.context = context;
        this.mediaUrls = mediaUrls;
    }

    @NonNull
    @Override
    public MediaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.media_page_item, parent, false);
        return new MediaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MediaViewHolder holder, int position) {
        String url = mediaUrls.get(position);

        // Use Glide (or Picasso) to load the image into the ImageView
        Glide.with(context)
                .load(url) // FIXED: Load the String URL directly
                .placeholder(android.R.drawable.stat_sys_download) // Placeholder icon while loading
                .error(android.R.drawable.ic_menu_close_clear_cancel) // Error icon
                .centerCrop()
                .into(holder.mediaImage);

        // Placeholder for a simple click action
        holder.mediaImage.setOnClickListener(v ->
                Toast.makeText(context, "Media " + (position + 1) + " clicked!", Toast.LENGTH_SHORT).show()
        );
    }

    @Override
    public int getItemCount() {
        return mediaUrls.size();
    }

    // --- ViewHolder Class ---
    public static class MediaViewHolder extends RecyclerView.ViewHolder {
        ImageView mediaImage;

        public MediaViewHolder(@NonNull View itemView) {
            super(itemView);
            // media_page_item.xml will contain this ImageView
            mediaImage = itemView.findViewById(R.id.image_media);
        }
    }
}
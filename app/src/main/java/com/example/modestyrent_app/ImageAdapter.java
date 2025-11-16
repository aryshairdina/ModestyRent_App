package com.example.modestyrent_app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ImageViewHolder> {
    private final Context ctx;
    private final List<String> images;

    public ImageAdapter(Context ctx, List<String> images) {
        this.ctx = ctx;
        this.images = images;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_image_slider, parent, false);
        return new ImageViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        String url = images.get(position);
        if (url != null && !url.isEmpty()) {
            Glide.with(ctx).load(url).centerCrop().into(holder.iv);
        } else {
            holder.iv.setImageResource(R.drawable.ic_person); // fallback
        }
    }

    @Override
    public int getItemCount() {
        return images == null ? 0 : images.size();
    }

    static class ImageViewHolder extends RecyclerView.ViewHolder {
        final ImageView iv;
        ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            // explicit cast avoids older-tooling "incompatible types" error
            iv = (ImageView) itemView.findViewById(R.id.ivSlider);
        }
    }
}

package com.example.modestyrent_app;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import java.util.List;

public class ReelsFeedAdapter extends RecyclerView.Adapter<ReelsFeedAdapter.ReelsViewHolder> {

    private final activity_product_reels context;
    private final List<ReelItem> reelsList;

    public ReelsFeedAdapter(activity_product_reels context, List<ReelItem> reelsList) {
        this.context = context;
        this.reelsList = reelsList;
    }

    @NonNull
    @Override
    public ReelsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.video_feed_item, parent, false);
        return new ReelsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReelsViewHolder holder, int position) {
        ReelItem item = reelsList.get(position);

        // Bind text data
        holder.titleText.setText(item.title != null ? item.title : "Untitled");
        holder.priceText.setText(item.rentalPrice != null ? item.rentalPrice : "RENT NOW");
        holder.descriptionText.setText(item.description != null ? item.description : "");

        // --- Setup Horizontal Media Carousel (ViewPager2) ---
        ReelsMediaAdapter mediaAdapter = new ReelsMediaAdapter(context, item.mediaUrls);
        holder.mediaViewPager.setAdapter(mediaAdapter);

        holder.mediaViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int pos) {
                super.onPageSelected(pos);
                mediaAdapter.handlePageSelected(pos);
            }
        });

        holder.mediaViewPager.setCurrentItem(0, false);
        mediaAdapter.handlePageSelected(0);

        // --- Interaction Listeners ---

        // Owner profile button
        holder.profileButton.setOnClickListener(v -> {
            ReelsMediaAdapter.pauseAllPlayers(); // stop all videos
            if (item.ownerId == null || item.ownerId.isEmpty()) {
                Toast.makeText(context, "Owner info not available", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(context, activity_owner_profile.class);
            intent.putExtra("ownerId", item.ownerId);
            context.startActivity(intent);
        });

        // Price button (RENT NOW)
        holder.priceText.setOnClickListener(v -> {
            ReelsMediaAdapter.pauseAllPlayers(); // stop all videos
            if (item.productId == null || item.productId.isEmpty()) {
                Toast.makeText(context, "Product ID not available", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(context, activity_product_details.class);
            intent.putExtra("productId", item.productId);
            context.startActivity(intent);
        });

        // Like button (placeholder)
        holder.likeButton.setOnClickListener(v ->
                Toast.makeText(context, "Liked " + (item.title != null ? item.title : "item"), Toast.LENGTH_SHORT).show()
        );

        // Comment button (placeholder)
        holder.commentButton.setOnClickListener(v ->
                Toast.makeText(context, "Opening comments for " + (item.title != null ? item.title : "item"), Toast.LENGTH_SHORT).show()
        );
    }

    @Override
    public int getItemCount() {
        return reelsList != null ? reelsList.size() : 0;
    }

    // --- ViewHolder Class ---
    public static class ReelsViewHolder extends RecyclerView.ViewHolder {
        ViewPager2 mediaViewPager;
        TextView titleText;
        TextView priceText;
        TextView descriptionText;
        View profileButton;
        View likeButton;
        View commentButton;

        public ReelsViewHolder(@NonNull View itemView) {
            super(itemView);
            mediaViewPager = itemView.findViewById(R.id.media_viewpager);
            titleText = itemView.findViewById(R.id.text_video_title);
            priceText = itemView.findViewById(R.id.text_price);
            descriptionText = itemView.findViewById(R.id.text_video_description);
            profileButton = itemView.findViewById(R.id.btn_owner_profile);
            likeButton = itemView.findViewById(R.id.btn_like);
            commentButton = itemView.findViewById(R.id.btn_comment);
        }
    }
}

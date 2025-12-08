package com.example.modestyrent_app;

import android.app.Activity;
import android.content.Context;
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

    private final Activity activity;
    private final Context ctx;
    private final List<ReelItem> reelsList;

    // Two constructors - both set activity & ctx so we can branch behavior later
    public ReelsFeedAdapter(activity_product_reels activity, List<ReelItem> reelsList) {
        this.activity = activity;
        this.ctx = activity;
        this.reelsList = reelsList;
    }

    public ReelsFeedAdapter(activity_product_reels_onboarding activityOnboarding, List<ReelItem> reelsList) {
        this.activity = activityOnboarding;
        this.ctx = activityOnboarding;
        this.reelsList = reelsList;
    }

    @NonNull
    @Override
    public ReelsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(ctx).inflate(R.layout.video_feed_item, parent, false);
        return new ReelsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReelsViewHolder holder, int position) {
        if (reelsList == null || position < 0 || position >= reelsList.size()) return;

        ReelItem item = reelsList.get(position);

        // Bind text data (safe null handling)
        holder.titleText.setText(item.title != null ? item.title : "Untitled");
        holder.priceText.setText(item.rentalPrice != null ? item.rentalPrice : "RENT NOW");
        holder.descriptionText.setText(item.description != null ? item.description : "");

        // --- Setup Horizontal Media Carousel (ViewPager2) ---
        ReelsMediaAdapter mediaAdapter = new ReelsMediaAdapter(ctx, item.mediaUrls);
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
        holder.profileButton.setOnClickListener(v -> {
            ReelsMediaAdapter.pauseAllPlayers(); // stop all videos (keeps your existing behaviour)
            // Branch based on which Activity created this adapter
            if (activity instanceof activity_product_reels) {
                // Open owner's profile
                if (item.ownerId == null || item.ownerId.isEmpty()) {
                    Toast.makeText(ctx, "Owner info not available", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = new Intent(activity, activity_owner_profile.class);
                intent.putExtra("ownerId", item.ownerId);
                activity.startActivity(intent);

            } else if (activity instanceof activity_product_reels_onboarding) {
                // In onboarding, profile goes to sign-in
                Intent intent = new Intent(activity, activity_signin.class);
                activity.startActivity(intent);
            } else {
                // Fallback: try sign-in
                Intent intent = new Intent(activity, activity_signin.class);
                activity.startActivity(intent);
            }
        });

        holder.priceText.setOnClickListener(v -> {
            ReelsMediaAdapter.pauseAllPlayers();
            if (item.productId == null || item.productId.isEmpty()) {
                Toast.makeText(ctx, "Product ID not available", Toast.LENGTH_SHORT).show();
                return;
            }

            if (activity instanceof activity_product_reels) {
                Intent intent = new Intent(activity, activity_product_details.class);
                intent.putExtra("productId", item.productId);
                activity.startActivity(intent);

            } else if (activity instanceof activity_product_reels_onboarding) {
                Intent intent = new Intent(activity, activity_product_details_onboarding.class);
                intent.putExtra("productId", item.productId);
                activity.startActivity(intent);

            } else {
                // Fallback to main details
                Intent intent = new Intent(activity, activity_product_details.class);
                intent.putExtra("productId", item.productId);
                activity.startActivity(intent);
            }
        });

        // Like button (placeholder)
        holder.likeButton.setOnClickListener(v ->
                Toast.makeText(ctx, "Liked " + (item.title != null ? item.title : "item"), Toast.LENGTH_SHORT).show()
        );

        // Comment button (placeholder)
        holder.commentButton.setOnClickListener(v ->
                Toast.makeText(ctx, "Opening comments for " + (item.title != null ? item.title : "item"), Toast.LENGTH_SHORT).show()
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

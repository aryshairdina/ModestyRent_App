package com.example.modestyrent_app;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class activity_product_reels_onboarding extends AppCompatActivity {

    private static final String TAG = "ProductReelsActivity";

    private RecyclerView recyclerView;

    private FirebaseAuth mAuth;
    private DatabaseReference productsRef;
    private String currentUserId;

    private ReelsFeedAdapter reelsFeedAdapter;
    private LinearLayoutManager layoutManager;
    private PagerSnapHelper snapHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_reels_onboarding);

        // 1. Firebase Initialization
        mAuth = FirebaseAuth.getInstance();
        currentUserId = (mAuth.getCurrentUser() != null) ? mAuth.getCurrentUser().getUid() : null;
        productsRef = FirebaseDatabase.getInstance().getReference().child("products");

        // 2. Initialize views
        recyclerView = findViewById(R.id.recycler_video_feed);

        // 3. Setup RecyclerView (vertical reels + snapping)
        layoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        recyclerView.setLayoutManager(layoutManager);

        snapHelper = new PagerSnapHelper();
        snapHelper.attachToRecyclerView(recyclerView);

        // When vertical scroll stops, decide which reel is "active"
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                super.onScrollStateChanged(rv, newState);

                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    // Scroll has stopped -> find snapped reel
                    if (snapHelper != null && layoutManager != null) {
                        android.view.View snappedView = snapHelper.findSnapView(layoutManager);
                        if (snappedView != null && reelsFeedAdapter != null) {
                            int pos = layoutManager.getPosition(snappedView);
                            if (pos != RecyclerView.NO_POSITION) {
                                handleReelSelected(pos);
                            }
                        }
                    }
                }
            }
        });

        // 4. Fetch Data
        fetchProductReels();
    }

    // Called when a reel becomes centered after vertical scroll
    private void handleReelSelected(int position) {
        // Pause ALL videos first
        ReelsMediaAdapter.pauseAllPlayers();

        // Find the ViewHolder for this reel
        RecyclerView.ViewHolder vh = recyclerView.findViewHolderForAdapterPosition(position);
        if (vh instanceof ReelsFeedAdapter.ReelsViewHolder) {
            ReelsFeedAdapter.ReelsViewHolder rVH = (ReelsFeedAdapter.ReelsViewHolder) vh;

            // Get its horizontal ViewPager2 and adapter
            androidx.viewpager2.widget.ViewPager2 vp = rVH.mediaViewPager;
            if (vp != null && vp.getAdapter() instanceof ReelsMediaAdapter) {
                ReelsMediaAdapter mediaAdapter = (ReelsMediaAdapter) vp.getAdapter();
                int currentPage = vp.getCurrentItem();
                mediaAdapter.handlePageSelected(currentPage); // will play only that page's video
            }
        }
    }

    // --- Data Fetching and Adapter Setup ---
    private void fetchProductReels() {
        productsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<ReelItem> reelsList = new ArrayList<>();

                for (DataSnapshot productSnapshot : snapshot.getChildren()) {
                    Product product = productSnapshot.getValue(Product.class);
                    if (product == null) continue;

                    // Ensure product ID is set from Firebase key
                    if (product.getId() == null || product.getId().isEmpty()) {
                        product.setId(productSnapshot.getKey());
                    }

                    // ✅ SKIP products that belong to the current owner
                    String ownerId = product.getUserId();
                    if (currentUserId != null && ownerId != null
                            && currentUserId.equals(ownerId)) {
                        continue;
                    }

                    // Only show available products with media
                    String status = product.getStatus() != null ? product.getStatus().trim() : "";
                    if (!"available".equalsIgnoreCase(status)) {
                        continue;
                    }

                    if (product.getImageUrls() == null || product.getImageUrls().isEmpty()) {
                        continue;
                    }

                    double price = product.getPrice();
                    String formattedPrice = (price > 0)
                            ? "RENT NOW: RM " + String.format(java.util.Locale.getDefault(), "%.2f", price) + " / DAY"
                            : "RENT NOW";

                    reelsList.add(new ReelItem(
                            product.getId(),
                            product.getName(),
                            product.getUserId(),
                            formattedPrice,
                            product.getDescription(),
                            product.getImageUrls()
                    ));
                }

                reelsFeedAdapter = new ReelsFeedAdapter(activity_product_reels_onboarding.this, reelsList);
                recyclerView.setAdapter(reelsFeedAdapter);

                // After first load, mark first reel as selected so its video plays
                if (!reelsList.isEmpty()) {
                    recyclerView.post(() -> handleReelSelected(0));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load product reels.", error.toException());
                Toast.makeText(activity_product_reels_onboarding.this, "Failed to load reels.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        ReelsMediaAdapter.pauseAllPlayers(); // stop sound when leaving screen
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ReelsMediaAdapter.pauseAllPlayers(); // extra safety
    }
}

package com.example.modestyrent_app;

import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class activity_product_reels extends AppCompatActivity {

    private static final String TAG = "ProductReelsActivity";

    private RecyclerView recyclerView;

    private FirebaseAuth mAuth;
    private DatabaseReference productsRef;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_reels);

        // 1. Firebase Initialization
        mAuth = FirebaseAuth.getInstance();
        currentUserId = (mAuth.getCurrentUser() != null) ? mAuth.getCurrentUser().getUid() : null;
        productsRef = FirebaseDatabase.getInstance().getReference().child("products");

        // 2. Initialize views
        recyclerView = findViewById(R.id.recycler_video_feed);

        // 3. Setup RecyclerView (vertical reels + snapping)
        recyclerView.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        );
        new PagerSnapHelper().attachToRecyclerView(recyclerView);

        // 4. Fetch Data
        fetchProductReels();
    }

    // --- Data Fetching and Adapter Setup ---
    private void fetchProductReels() {
        productsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<ReelItem> reelsList = new ArrayList<>();
                boolean hasOwnedProduct = false;

                for (DataSnapshot productSnapshot : snapshot.getChildren()) {
                    Product product = productSnapshot.getValue(Product.class);
                    if (product == null) continue;

                    // Ensure product ID is set from Firebase key
                    if (product.getId() == null || product.getId().isEmpty()) {
                        product.setId(productSnapshot.getKey());
                    }

                    // Only show available products with media
                    if (!"available".equalsIgnoreCase(
                            product.getStatus() != null ? product.getStatus().trim() : "")
                    ) {
                        continue;
                    }

                    if (product.getImageUrls() == null || product.getImageUrls().isEmpty()) {
                        continue;
                    }

                    // Check if current user owns at least one product (to show + button)
                    if (currentUserId != null && currentUserId.equals(product.getUserId())) {
                        hasOwnedProduct = true;
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

                ReelsFeedAdapter reelsFeedAdapter = new ReelsFeedAdapter(activity_product_reels.this, reelsList);
                recyclerView.setAdapter(reelsFeedAdapter);

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load product reels.", error.toException());
                Toast.makeText(activity_product_reels.this, "Failed to load reels.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}

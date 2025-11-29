package com.example.modestyrent_app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class activity_leave_review extends AppCompatActivity {

    private String bookingId, productId, ownerId, currentUserId;
    private int currentRating = 0;

    private ImageView backButton, ivProductImage;
    private TextView tvProductName, tvOwnerName, tvRatingText, tvCharacterCount;
    private ImageView star1, star2, star3, star4, star5;
    private ImageView starPreview1, starPreview2, starPreview3, starPreview4, starPreview5;
    private EditText etReviewDescription;
    private LinearLayout photosContainer;
    private MaterialCardView btnAddPhoto;
    private SwitchMaterial switchAnonymous;
    private MaterialButton btnSubmitReview;
    private TextView tvRatingPreview;

    private DatabaseReference bookingsRef, usersRef, productsRef, reviewsRef;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leave_review);

        // Get intent data
        Intent intent = getIntent();
        bookingId = intent.getStringExtra("bookingId");
        productId = intent.getStringExtra("productId");
        ownerId = intent.getStringExtra("ownerId");

        if (bookingId == null) {
            Toast.makeText(this, "Booking ID not provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        mAuth = FirebaseAuth.getInstance();
        currentUserId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;

        initializeViews();
        setupFirebase();
        setupListeners();
        loadBookingDetails();
    }

    private void initializeViews() {
        backButton = findViewById(R.id.backButton);
        ivProductImage = findViewById(R.id.ivProductImage);
        tvProductName = findViewById(R.id.tvProductName);
        tvOwnerName = findViewById(R.id.tvOwnerName);
        tvRatingText = findViewById(R.id.tvRatingText);
        tvCharacterCount = findViewById(R.id.tvCharacterCount);

        // Star rating views
        star1 = findViewById(R.id.star1);
        star2 = findViewById(R.id.star2);
        star3 = findViewById(R.id.star3);
        star4 = findViewById(R.id.star4);
        star5 = findViewById(R.id.star5);


        etReviewDescription = findViewById(R.id.etReviewDescription);
        photosContainer = findViewById(R.id.photosContainer);
        btnAddPhoto = findViewById(R.id.btnAddPhoto);
        switchAnonymous = findViewById(R.id.switchAnonymous);
        btnSubmitReview = findViewById(R.id.btnSubmitReview);

        backButton.setOnClickListener(v -> finish());

        // Set initial star colors
        updateStarColors();
        updatePreviewStars();
    }

    private void setupFirebase() {
        bookingsRef = FirebaseDatabase.getInstance().getReference("bookings");
        usersRef = FirebaseDatabase.getInstance().getReference("users");
        productsRef = FirebaseDatabase.getInstance().getReference("products");
        reviewsRef = FirebaseDatabase.getInstance().getReference("reviews");
    }

    private void setupListeners() {
        // Star rating click listeners
        star1.setOnClickListener(v -> setRating(1));
        star2.setOnClickListener(v -> setRating(2));
        star3.setOnClickListener(v -> setRating(3));
        star4.setOnClickListener(v -> setRating(4));
        star5.setOnClickListener(v -> setRating(5));

        // Review description character counter
        etReviewDescription.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                tvCharacterCount.setText(s.length() + "/500 characters");
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Add photo button
        btnAddPhoto.setOnClickListener(v -> addPhoto());

        // Submit review button
        btnSubmitReview.setOnClickListener(v -> submitReview());
    }

    private void loadBookingDetails() {
        bookingsRef.child(bookingId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    try {
                        updateBookingUI(snapshot);
                        loadProductDetails();
                        loadOwnerInfo();
                    } catch (Exception e) {
                        Toast.makeText(activity_leave_review.this, "Error loading booking details", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(activity_leave_review.this, "Booking not found", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(activity_leave_review.this, "Failed to load booking", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateBookingUI(DataSnapshot bookingSnapshot) {
        // Basic booking info
        tvProductName.setText(getStringValue(bookingSnapshot, "productName"));

        // Load product image would be handled in loadProductDetails()
    }

    private void loadProductDetails() {
        if (productId != null) {
            productsRef.child(productId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        // Load product image if available
                        // This is a placeholder - implement actual image loading
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    // Handle error
                }
            });
        }
    }

    private void loadOwnerInfo() {
        if (ownerId != null) {
            usersRef.child(ownerId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String ownerName = getStringValue(snapshot, "fullName");
                        if (ownerName != null) {
                            tvOwnerName.setText("Owner: " + ownerName);
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    // Handle error
                }
            });
        }
    }

    private void setRating(int rating) {
        currentRating = rating;
        updateStarColors();
        updatePreviewStars();
        updateRatingText();
    }

    private void updateStarColors() {
        // Update main stars
        updateStarColor(star1, 1);
        updateStarColor(star2, 2);
        updateStarColor(star3, 3);
        updateStarColor(star4, 4);
        updateStarColor(star5, 5);
    }

    private void updateStarColor(ImageView star, int starNumber) {
        if (starNumber <= currentRating) {
            // Filled star (gold)
            star.setColorFilter(Color.parseColor("#FFD700"), PorterDuff.Mode.SRC_IN);
        } else {
            // Empty star (secondary color)
            star.setColorFilter(Color.parseColor("#D6D3CF"), PorterDuff.Mode.SRC_IN);
        }
    }

    private void updatePreviewStars() {
        // Update preview stars in bottom bar
        updatePreviewStarColor(starPreview1, 1);
        updatePreviewStarColor(starPreview2, 2);
        updatePreviewStarColor(starPreview3, 3);
        updatePreviewStarColor(starPreview4, 4);
        updatePreviewStarColor(starPreview5, 5);

        tvRatingPreview.setText(String.valueOf(currentRating));
    }

    private void updatePreviewStarColor(ImageView star, int starNumber) {
        if (starNumber <= currentRating) {
            star.setColorFilter(Color.parseColor("#FFD700"), PorterDuff.Mode.SRC_IN);
        } else {
            star.setColorFilter(Color.parseColor("#D6D3CF"), PorterDuff.Mode.SRC_IN);
        }
    }

    private void updateRatingText() {
        switch (currentRating) {
            case 1:
                tvRatingText.setText("Poor");
                break;
            case 2:
                tvRatingText.setText("Fair");
                break;
            case 3:
                tvRatingText.setText("Good");
                break;
            case 4:
                tvRatingText.setText("Very Good");
                break;
            case 5:
                tvRatingText.setText("Excellent");
                break;
            default:
                tvRatingText.setText("Tap to rate");
                break;
        }
    }

    private void addPhoto() {
        // Implement photo upload functionality
        Toast.makeText(this, "Add photo feature", Toast.LENGTH_SHORT).show();
    }

    private void submitReview() {
        if (currentRating == 0) {
            Toast.makeText(this, "Please select a rating", Toast.LENGTH_SHORT).show();
            return;
        }

        String reviewDescription = etReviewDescription.getText().toString().trim();
        boolean isAnonymous = switchAnonymous.isChecked();

        if (reviewDescription.isEmpty()) {
            Toast.makeText(this, "Please write a review", Toast.LENGTH_SHORT).show();
            return;
        }

        String reviewId = reviewsRef.push().getKey();
        Map<String, Object> reviewData = new HashMap<>();
        reviewData.put("reviewId", reviewId);
        reviewData.put("bookingId", bookingId);
        reviewData.put("productId", productId);
        reviewData.put("ownerId", ownerId);
        reviewData.put("renterId", currentUserId);
        reviewData.put("rating", currentRating);
        reviewData.put("reviewText", reviewDescription);
        reviewData.put("isAnonymous", isAnonymous);
        reviewData.put("reviewDate", System.currentTimeMillis());
        reviewData.put("helpfulCount", 0);
        reviewData.put("reviewStatus", "active");

        reviewsRef.child(reviewId).setValue(reviewData)
                .addOnSuccessListener(aVoid -> {
                    // Also update booking to mark as reviewed
                    Map<String, Object> bookingUpdates = new HashMap<>();
                    bookingUpdates.put("reviewed", true);
                    bookingUpdates.put("reviewDate", System.currentTimeMillis());

                    bookingsRef.child(bookingId).updateChildren(bookingUpdates)
                            .addOnSuccessListener(aVoid1 -> {
                                Toast.makeText(activity_leave_review.this, "Review submitted successfully", Toast.LENGTH_SHORT).show();

                                // Navigate back to rental details
                                Intent intent = new Intent(this, activity_rentals_details_borrower.class);
                                intent.putExtra("bookingId", bookingId);
                                intent.putExtra("productId", productId);
                                intent.putExtra("ownerId", ownerId);
                                startActivity(intent);
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(activity_leave_review.this, "Review submitted but failed to update booking", Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(activity_leave_review.this, "Failed to submit review", Toast.LENGTH_SHORT).show();
                });
    }

    // Helper methods
    private String getStringValue(DataSnapshot snapshot, String key) {
        DataSnapshot child = snapshot.child(key);
        return child.exists() && child.getValue() != null ? child.getValue().toString() : null;
    }
}
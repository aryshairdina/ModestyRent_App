package com.example.modestyrent_app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.*;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

public class activity_leave_review extends AppCompatActivity {

    private static final int REQUEST_CODE_PICK_PHOTO = 101;

    private String bookingId, productId, ownerId, currentUserId;
    private int currentRating = 0;

    private ImageView backButton, ivProductImage;
    private TextView tvProductName, tvOwnerName, tvRatingText, tvCharacterCount;
    private ImageView star1, star2, star3, star4, star5;
    private EditText etReviewDescription;
    private LinearLayout photosContainer;
    private MaterialCardView btnAddPhoto;
    private SwitchMaterial switchAnonymous;
    private MaterialButton btnSubmitReview;

    private DatabaseReference bookingsRef, usersRef, productsRef, reviewsRef;
    private FirebaseAuth mAuth;

    // For photo selection
    private Uri selectedPhotoUri;
    private ImageView selectedPhotoPreview;

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

        // Initial star state
        updateStarColors();
        tvRatingText.setText("Tap to rate");
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
                        loadProductDetails();   // load product image
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
        // Product name
        tvProductName.setText(getStringValue(bookingSnapshot, "productName"));

        // Try to load product image directly from booking if available
        String bookingImageUrl = getStringValue(bookingSnapshot, "productImageUrl");
        if (bookingImageUrl == null || bookingImageUrl.isEmpty()) {
            bookingImageUrl = getStringValue(bookingSnapshot, "productImage");
        }

        if (bookingImageUrl != null && !bookingImageUrl.isEmpty()) {
            Glide.with(this)
                    .load(bookingImageUrl)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .into(ivProductImage);
        }
    }

    private void loadProductDetails() {
        if (productId != null) {
            productsRef.child(productId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        // Try multiple possible keys for image URL
                        String imageUrl = null;

                        if (snapshot.child("imageUrl").exists()) {
                            imageUrl = snapshot.child("imageUrl").getValue(String.class);
                        } else if (snapshot.child("image").exists()) {
                            imageUrl = snapshot.child("image").getValue(String.class);
                        } else if (snapshot.child("productImage").exists()) {
                            imageUrl = snapshot.child("productImage").getValue(String.class);
                        }

                        if (imageUrl != null && !imageUrl.isEmpty()) {
                            Glide.with(activity_leave_review.this)
                                    .load(imageUrl)
                                    .placeholder(R.drawable.ic_image_placeholder)
                                    .into(ivProductImage);
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    // ignore
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
                    // ignore
                }
            });
        }
    }

    private void setRating(int rating) {
        currentRating = rating;
        updateStarColors();
        updateRatingText();
    }

    private void updateStarColors() {
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

    private void updateRatingText() {
        int score = currentRating * 20; // ⭐ each star = 20/100

        switch (currentRating) {
            case 1:
                tvRatingText.setText("Poor (" + score + "/100)");
                break;
            case 2:
                tvRatingText.setText("Fair (" + score + "/100)");
                break;
            case 3:
                tvRatingText.setText("Good (" + score + "/100)");
                break;
            case 4:
                tvRatingText.setText("Very Good (" + score + "/100)");
                break;
            case 5:
                tvRatingText.setText("Excellent (" + score + "/100)");
                break;
            default:
                tvRatingText.setText("Tap to rate");
                break;
        }
    }

    // -------- PHOTO PICKER (Add Photo) --------
    private void addPhoto() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, "Select Photo"), REQUEST_CODE_PICK_PHOTO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_PICK_PHOTO && resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedPhotoUri = data.getData();

            // Remove previous preview if any
            photosContainer.removeAllViews();
            selectedPhotoPreview = new ImageView(this);

            // size = 80dp
            int sizeInDp = 80;
            float density = getResources().getDisplayMetrics().density;
            int sizeInPx = (int) (sizeInDp * density);

            // marginRight = 16dp
            int marginRightInDp = 16;
            int marginRightInPx = (int) (marginRightInDp * density);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    sizeInPx,
                    sizeInPx
            );
            params.setMargins(0, 0, marginRightInPx, 0);
            selectedPhotoPreview.setLayoutParams(params);
            selectedPhotoPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);

            Glide.with(this)
                    .load(selectedPhotoUri)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .into(selectedPhotoPreview);

            photosContainer.addView(selectedPhotoPreview);
        }
    }

    // -------- SUBMIT REVIEW --------
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

        // ⭐ Score out of 100
        int ratingScore = currentRating * 20;

        // reviews table grouped by bookingId: /reviews/{bookingId}/{reviewId}
        String reviewId = reviewsRef.child(bookingId).push().getKey();
        if (reviewId == null) {
            Toast.makeText(this, "Failed to generate review ID", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedPhotoUri != null) {
            // Upload photo to Firebase Storage first
            StorageReference photoRef = FirebaseStorage.getInstance()
                    .getReference()
                    .child("review_photos")
                    .child(bookingId)
                    .child(reviewId + ".jpg");

            photoRef.putFile(selectedPhotoUri)
                    .addOnSuccessListener(taskSnapshot ->
                            photoRef.getDownloadUrl()
                                    .addOnSuccessListener(uri -> {
                                        String photoUrl = uri.toString();
                                        saveReviewToDatabase(reviewId, reviewDescription, isAnonymous, ratingScore, photoUrl);
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(activity_leave_review.this, "Failed to get photo URL", Toast.LENGTH_SHORT).show();
                                    })
                    )
                    .addOnFailureListener(e -> {
                        Toast.makeText(activity_leave_review.this, "Failed to upload photo", Toast.LENGTH_SHORT).show();
                    });

        } else {
            // No photo, save review directly
            saveReviewToDatabase(reviewId, reviewDescription, isAnonymous, ratingScore, null);
        }
    }

    private void saveReviewToDatabase(String reviewId,
                                      String reviewDescription,
                                      boolean isAnonymous,
                                      int ratingScore,
                                      String photoUrl) {

        Map<String, Object> reviewData = new HashMap<>();
        reviewData.put("reviewId", reviewId);
        reviewData.put("bookingId", bookingId);
        reviewData.put("productId", productId);
        reviewData.put("ownerId", ownerId);
        reviewData.put("renterId", currentUserId);
        reviewData.put("rating", currentRating);        // 1–5 stars
        reviewData.put("ratingScore", ratingScore);     // 20/40/60/80/100
        reviewData.put("reviewText", reviewDescription);
        reviewData.put("isAnonymous", isAnonymous);
        reviewData.put("reviewDate", System.currentTimeMillis());
        reviewData.put("helpfulCount", 0);
        reviewData.put("reviewStatus", "active");

        if (photoUrl != null) {
            reviewData.put("photoUrl", photoUrl);
        }

        // Save under /reviews/{bookingId}/{reviewId}
        reviewsRef.child(bookingId).child(reviewId).setValue(reviewData)
                .addOnSuccessListener(aVoid -> {
                    // Also update booking to mark as reviewed
                    Map<String, Object> bookingUpdates = new HashMap<>();
                    bookingUpdates.put("reviewed", true);
                    bookingUpdates.put("reviewDate", System.currentTimeMillis());

                    bookingsRef.child(bookingId).updateChildren(bookingUpdates)
                            .addOnSuccessListener(aVoid1 -> {
                                Toast.makeText(activity_leave_review.this, "Review submitted successfully", Toast.LENGTH_SHORT).show();

                                // Navigate back to rental details (borrower side)
                                Intent intent = new Intent(this, activity_rentals_details_borrower.class);
                                intent.putExtra("bookingId", bookingId);
                                intent.putExtra("productId", productId);
                                intent.putExtra("ownerId", ownerId);
                                startActivity(intent);
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(activity_leave_review.this, "Review saved but failed to update booking", Toast.LENGTH_SHORT).show();
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

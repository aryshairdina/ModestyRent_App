package com.example.modestyrent_app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Locale;

public class activity_profile extends AppCompatActivity {

    private MaterialButton signOutButton, btnEditProfile;
    private View btnMyListings, btnMyLikes, btnMyRentals, btnBookingRequest;
    private FirebaseAuth mAuth;
    private DatabaseReference userRef, productsRef, bookingsRef, reviewsRef;

    private TextView fullNameText, initialsText, userEmailText;
    private TextView statListing, statRentals, statRating;

    private ValueEventListener userListener, productsListener, bookingsListener;
    private String currentUserId;

    private static final String TAG = "ProfileActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_profile);

        // Initialize Firebase Auth and Database
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        currentUserId = currentUser.getUid();
        FirebaseDatabase db = FirebaseDatabase.getInstance();
        userRef = db.getReference("users").child(currentUserId);
        productsRef = db.getReference("products");
        bookingsRef = db.getReference("bookings");
        reviewsRef = db.getReference("reviews");   // ⭐ reviews root

        Log.d(TAG, "Current User ID: " + currentUserId);

        // Initialize views
        initializeViews();
        setupBottomNavigation();
        setupFirebaseListeners();
        setupClickListeners();

        // Load stats immediately
        loadUserStats();
    }

    private void initializeViews() {
        signOutButton = findViewById(R.id.signOutButton);
        btnEditProfile = findViewById(R.id.btnEditProfile);
        btnMyLikes = findViewById(R.id.btnMyLikes);
        btnMyListings = findViewById(R.id.btnMyListings);
        btnMyRentals = findViewById(R.id.btnMyRentals);
        btnBookingRequest = findViewById(R.id.btnBookingRequest);

        fullNameText = findViewById(R.id.fullName);
        initialsText = findViewById(R.id.initialsText);
        userEmailText = findViewById(R.id.userEmail);

        statListing = findViewById(R.id.statListing);
        statRentals = findViewById(R.id.statRentals);
        statRating = findViewById(R.id.statRating);

        // Set initial values
        statListing.setText("0");
        statRentals.setText("0");
        statRating.setText("0.0");   // default rating
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        if (bottomNav == null) {
            Toast.makeText(this, "BottomNavigationView not found", Toast.LENGTH_LONG).show();
            return;
        }

        // Set default selected item (profile)
        Menu menu = bottomNav.getMenu();
        MenuItem profileItem = menu.findItem(R.id.nav_profile);
        if (profileItem != null) {
            bottomNav.setSelectedItemId(R.id.nav_profile);
        }

        // Handle navigation item selection
        bottomNav.setOnItemSelectedListener(item -> handleNavItemSelected(item));
    }

    private void setupFirebaseListeners() {
        // User data listener
        userListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Load user profile data
                    String fullName = snapshot.child("fullName").getValue(String.class);
                    String email = snapshot.child("email").getValue(String.class);

                    // Try alternative field names
                    if (fullName == null) {
                        fullName = snapshot.child("name").getValue(String.class);
                    }
                    if (fullName == null) {
                        fullName = snapshot.child("username").getValue(String.class);
                    }

                    if (fullName != null && !fullName.isEmpty()) {
                        fullNameText.setText(fullName);
                        String initials = getInitials(fullName);
                        initialsText.setText(initials);
                    } else {
                        fullNameText.setText("User");
                        initialsText.setText("U");
                    }

                    if (email != null && !email.isEmpty()) {
                        userEmailText.setText(email);
                    } else {
                        // Try to get email from Firebase Auth
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null && user.getEmail() != null) {
                            userEmailText.setText(user.getEmail());
                        } else {
                            userEmailText.setText("No email provided");
                        }
                    }

                    Log.d(TAG, "User data loaded successfully");
                } else {
                    Log.d(TAG, "User data not found in database");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load user data: " + error.getMessage());
                Toast.makeText(activity_profile.this, "Failed to load profile", Toast.LENGTH_SHORT).show();
            }
        };

        // Products listener for listings count
        productsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                loadListingsCount();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Products listener cancelled: " + error.getMessage());
            }
        };

        // Bookings listener for rentals count
        bookingsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                loadRentalsCount();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Bookings listener cancelled: " + error.getMessage());
            }
        };

        // Attach listeners
        userRef.addValueEventListener(userListener);
        productsRef.addValueEventListener(productsListener);
        bookingsRef.addValueEventListener(bookingsListener);
    }

    private void loadUserStats() {
        loadListingsCount();
        loadRentalsCount();
        loadRatingSummary();   // ⭐ NEW: rating summary
    }

    private void loadListingsCount() {
        Log.d(TAG, "Loading listings count for user: " + currentUserId);

        productsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long count = 0;

                // Method 1: Try ownerId field
                for (DataSnapshot productSnapshot : snapshot.getChildren()) {
                    String ownerId = productSnapshot.child("ownerId").getValue(String.class);
                    if (ownerId != null && ownerId.equals(currentUserId)) {
                        count++;
                        Log.d(TAG, "Found product with ownerId: " + productSnapshot.getKey());
                    }
                }

                // If no products found with ownerId, try alternative field names
                if (count == 0) {
                    Log.d(TAG, "No products found with ownerId, trying alternative fields");

                    for (DataSnapshot productSnapshot : snapshot.getChildren()) {
                        // Try different possible field names
                        String userId = productSnapshot.child("userId").getValue(String.class);
                        String owner = productSnapshot.child("owner").getValue(String.class);
                        String uploadedBy = productSnapshot.child("uploadedBy").getValue(String.class);
                        String creatorId = productSnapshot.child("creatorId").getValue(String.class);

                        if ((userId != null && userId.equals(currentUserId)) ||
                                (owner != null && owner.equals(currentUserId)) ||
                                (uploadedBy != null && uploadedBy.equals(currentUserId)) ||
                                (creatorId != null && creatorId.equals(currentUserId))) {
                            count++;
                            Log.d(TAG, "Found product with alternative field: " + productSnapshot.getKey());
                        }
                    }
                }

                // Last resort: count all products if we can't determine ownership
                if (count == 0 && snapshot.getChildrenCount() > 0) {
                    Log.d(TAG, "No ownership data found, counting all products");
                    count = snapshot.getChildrenCount();
                }

                statListing.setText(String.valueOf(count));
                Log.d(TAG, "Final listings count: " + count);

                // Debug: Print all products to log
                debugPrintAllProducts(snapshot);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load listings count: " + error.getMessage());
                statListing.setText("0");
                Toast.makeText(activity_profile.this, "Failed to load listings", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadRentalsCount() {
        Log.d(TAG, "Loading rentals count for user: " + currentUserId);

        bookingsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long count = 0;

                // Method 1: Try renterId field
                for (DataSnapshot bookingSnapshot : snapshot.getChildren()) {
                    String renterId = bookingSnapshot.child("renterId").getValue(String.class);
                    if (renterId != null && renterId.equals(currentUserId)) {
                        count++;
                        Log.d(TAG, "Found booking with renterId: " + bookingSnapshot.getKey());
                    }
                }

                // If no bookings found with renterId, try alternative field names
                if (count == 0) {
                    Log.d(TAG, "No bookings found with renterId, trying alternative fields");

                    for (DataSnapshot bookingSnapshot : snapshot.getChildren()) {
                        // Try different possible field names
                        String userId = bookingSnapshot.child("userId").getValue(String.class);
                        String customerId = bookingSnapshot.child("customerId").getValue(String.class);
                        String borrowerId = bookingSnapshot.child("borrowerId").getValue(String.class);

                        if ((userId != null && userId.equals(currentUserId)) ||
                                (customerId != null && customerId.equals(currentUserId)) ||
                                (borrowerId != null && borrowerId.equals(currentUserId))) {
                            count++;
                            Log.d(TAG, "Found booking with alternative field: " + bookingSnapshot.getKey());
                        }
                    }
                }

                statRentals.setText(String.valueOf(count));
                Log.d(TAG, "Final rentals count: " + count);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load rentals count: " + error.getMessage());
                statRentals.setText("0");
            }
        });
    }

    // ⭐ NEW: load rating summary from /reviews
    private void loadRatingSummary() {
        Log.d(TAG, "Loading rating summary for owner: " + currentUserId);

        if (reviewsRef == null) {
            statRating.setText("0.0");
            return;
        }

        reviewsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                double totalRating = 0.0;
                int reviewCount = 0;

                // reviews/{bookingId}/{reviewId}
                for (DataSnapshot bookingSnap : snapshot.getChildren()) {
                    for (DataSnapshot reviewSnap : bookingSnap.getChildren()) {
                        String ownerId = reviewSnap.child("ownerId").getValue(String.class);

                        if (ownerId != null && ownerId.equals(currentUserId)) {
                            // rating can be stored as Long or Double
                            Double ratingDouble = reviewSnap.child("rating").getValue(Double.class);
                            Long ratingLong = reviewSnap.child("rating").getValue(Long.class);

                            double ratingValue;
                            if (ratingDouble != null) {
                                ratingValue = ratingDouble;
                            } else if (ratingLong != null) {
                                ratingValue = ratingLong.doubleValue();
                            } else {
                                continue;
                            }

                            totalRating += ratingValue;
                            reviewCount++;
                        }
                    }
                }

                if (reviewCount > 0) {
                    double avg = totalRating / reviewCount;
                    String avgText = String.format(Locale.US, "%.1f", avg);
                    statRating.setText(avgText);
                    Log.d(TAG, "Rating summary: avg=" + avgText + " from " + reviewCount + " reviews");
                } else {
                    statRating.setText("0.0");
                    Log.d(TAG, "No reviews found for this owner. Rating = 0.0");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load rating summary: " + error.getMessage());
                statRating.setText("0.0");
            }
        });
    }

    private void debugPrintAllProducts(DataSnapshot snapshot) {
        Log.d(TAG, "=== DEBUG: All Products in Database ===");
        for (DataSnapshot productSnapshot : snapshot.getChildren()) {
            Log.d(TAG, "Product ID: " + productSnapshot.getKey());
            for (DataSnapshot field : productSnapshot.getChildren()) {
                Log.d(TAG, "  " + field.getKey() + ": " + field.getValue());
            }
            Log.d(TAG, "---");
        }
        Log.d(TAG, "Total products in database: " + snapshot.getChildrenCount());
    }

    private void setupClickListeners() {
        // Edit Profile button
        btnEditProfile.setOnClickListener(v -> {
            Intent intent = new Intent(activity_profile.this, activity_edit_profile.class);
            startActivity(intent);
        });

        // My Likes button
        btnMyLikes.setOnClickListener(v -> {
            Intent intent = new Intent(activity_profile.this, activity_mylikes.class);
            startActivity(intent);
        });

        // My Listings button
        btnMyListings.setOnClickListener(v -> {
            Intent intent = new Intent(activity_profile.this, activity_mylisting.class);
            startActivity(intent);
        });

        // My Rentals button
        btnMyRentals.setOnClickListener(v -> {
            Intent intent = new Intent(activity_profile.this, activity_myrentals.class);
            startActivity(intent);
        });

        // Booking Requests button
        btnBookingRequest.setOnClickListener(v -> {
            Intent intent = new Intent(activity_profile.this, activity_booking_requests.class);
            startActivity(intent);
        });

        // Sign Out button
        signOutButton.setOnClickListener(v -> signOutUser());
    }

    private void signOutUser() {
        if (userListener != null) {
            userRef.removeEventListener(userListener);
        }
        if (productsListener != null) {
            productsRef.removeEventListener(productsListener);
        }
        if (bookingsListener != null) {
            bookingsRef.removeEventListener(bookingsListener);
        }

        mAuth.signOut();
        Intent intent = new Intent(activity_profile.this, activity_home.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private boolean handleNavItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_home) {
            Intent intentHome = new Intent(activity_profile.this, activity_homepage.class);
            intentHome.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intentHome);
            return true;
        }

        if (id == R.id.nav_add_item) {
            Intent intent = new Intent(activity_profile.this, activity_add_product.class);
            startActivity(intent);
            return true;
        }

        if (id == R.id.nav_profile) {
            // Already on Profile
            return true;
        }

        if (id == R.id.nav_live) {
            Toast.makeText(this, "Live feature coming soon", Toast.LENGTH_SHORT).show();
            return true;
        }

        if (id == R.id.nav_chat) {
            Intent intent = new Intent(activity_profile.this, activity_chat_list.class);
            startActivity(intent);
        }

        return false;
    }

    // Helper method to extract initials
    private String getInitials(String name) {
        String[] words = name.trim().split("\\s+");
        StringBuilder initials = new StringBuilder();

        if (words.length >= 1 && words[0].length() > 0) {
            initials.append(words[0].substring(0, 1).toUpperCase());
        }
        if (words.length >= 2 && words[1].length() > 0) {
            initials.append(words[1].substring(0, 1).toUpperCase());
        }

        // fallback: at least one letter
        if (initials.length() == 0 && name.length() > 0) {
            initials.append(name.substring(0, 1).toUpperCase());
        }

        return initials.toString();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Detach listeners to prevent memory leaks
        if (userRef != null && userListener != null) {
            userRef.removeEventListener(userListener);
        }
        if (productsRef != null && productsListener != null) {
            productsRef.removeEventListener(productsListener);
        }
        if (bookingsRef != null && bookingsListener != null) {
            bookingsRef.removeEventListener(bookingsListener);
        }
    }
}

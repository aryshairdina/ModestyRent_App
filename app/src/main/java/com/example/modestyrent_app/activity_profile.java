package com.example.modestyrent_app;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
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

public class activity_profile extends AppCompatActivity {

    private MaterialButton signOutButton, btnEditProfile, btnMyListings;
    private FirebaseAuth mAuth;
    private DatabaseReference userRef;

    private TextView fullNameText, initialsText;

    private ValueEventListener userListener; // store listener so you can detach if needed

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

        String userId = currentUser.getUid();
        userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);

        // Initialize views
        signOutButton = findViewById(R.id.signOutButton);
        btnEditProfile = findViewById(R.id.btnEditProfile);
        btnMyListings = findViewById(R.id.btnMyListings);
        fullNameText = findViewById(R.id.fullName);
        initialsText = findViewById(R.id.initialsText);

        // ===== Bottom Navigation Setup =====
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        if (bottomNav == null) {
            Toast.makeText(this, "BottomNavigationView not found (check layout id)", Toast.LENGTH_LONG).show();
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
        // ===== End Bottom Nav Setup =====

        // ✅ Realtime auto-refresh listener for fullName
        userListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String fullName = snapshot.child("fullName").getValue(String.class);
                if (fullName != null && !fullName.isEmpty()) {
                    fullNameText.setText(fullName);

                    // ✅ Extract first and second letter for initials
                    String initials = getInitials(fullName);
                    initialsText.setText(initials);
                } else {
                    fullNameText.setText("User");
                    initialsText.setText("U");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(activity_profile.this, "Failed to load profile", Toast.LENGTH_SHORT).show();
            }
        };

        // Attach the listener — keeps listening for live updates
        userRef.addValueEventListener(userListener);

        // ✅ Edit Profile button
        btnEditProfile.setOnClickListener(v -> {
            Intent intent = new Intent(activity_profile.this, activity_edit_profile.class);
            startActivity(intent);
        });

        // ✅ My Listings button
        btnMyListings.setOnClickListener(v -> {
            Intent intent = new Intent(activity_profile.this, activity_mylisting.class);
            startActivity(intent);
        });

        // ✅ Sign Out button
        signOutButton.setOnClickListener(v -> {
            if (userListener != null) {
                userRef.removeEventListener(userListener); // remove listener when signing out
            }
            mAuth.signOut();
            Intent intent = new Intent(activity_profile.this, activity_home.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }

    /**
     * Handle bottom navigation selections
     */
    private boolean handleNavItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_home) {
            // Navigate to Home page
            Intent intentHome = new Intent(activity_profile.this, activity_homepage.class);
            // avoid creating multiple activities if already in stack (optional)
            intentHome.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intentHome);
            return true;
        }

        if (id == R.id.nav_add_item) {
            // Go to Add Product page
            Intent intent = new Intent(activity_profile.this, activity_add_product.class);
            startActivity(intent);
            return true;
        }

        if (id == R.id.nav_profile) {
            // Already on Profile
            return true;
        }

        if (id == R.id.nav_live) {
            // Placeholder behaviour (adjust when live page ready)
            // For now, navigate to profile (or change to activity_live when available)
            Intent intent = new Intent(activity_profile.this, activity_profile.class);
            startActivity(intent);
            return true;
        }

        if (id == R.id.nav_chat) {
            // Placeholder behaviour (adjust when chat page ready)
            Intent intent = new Intent(activity_profile.this, activity_profile.class);
            startActivity(intent);
            return true;
        }

        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Detach listener to prevent memory leaks
        if (userRef != null && userListener != null) {
            userRef.removeEventListener(userListener);
        }
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
}

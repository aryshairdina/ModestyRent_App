package com.example.modestyrent_app;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class activity_homepage extends AppCompatActivity {

    private TextView welcomeText;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_homepage);

        // Initialize views
        welcomeText = findViewById(R.id.welcomeText);
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Check current user
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // Display email first
            welcomeText.setText("Welcome, " + currentUser.getEmail());

            // Try to get full name from Realtime Database
            String uid = currentUser.getUid();
            DatabaseReference userRef = FirebaseDatabase.getInstance()
                    .getReference("users")
                    .child(uid)
                    .child("fullName");

            userRef.get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    String name = task.getResult().getValue(String.class);
                    if (name != null && !name.isEmpty()) {
                        welcomeText.setText("Welcome, " + name + "!");
                    } else {
                        welcomeText.setText("Welcome, " + currentUser.getEmail() + "!");
                    }
                } else {
                    welcomeText.setText("Welcome, " + currentUser.getEmail() + "!");
                }
            });
        } else {
            Toast.makeText(this, "Please sign in first.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, activity_signin.class));
            finish();
            return;
        }

        // ========== Bottom Navigation Setup ==========
        if (bottomNav == null) {
            Toast.makeText(this, "BottomNavigationView not found (check layout id)", Toast.LENGTH_LONG).show();
            return;
        }

        // Set default selected item (home)
        Menu menu = bottomNav.getMenu();
        MenuItem homeItem = menu.findItem(R.id.nav_home);
        if (homeItem != null) {
            bottomNav.setSelectedItemId(R.id.nav_home);
        }

        // Handle navigation item selection
        bottomNav.setOnItemSelectedListener(item -> handleNavItemSelected(item));
    }

    /**
     * Handle bottom navigation selections
     */
    private boolean handleNavItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_home) {
            // Already on Home
            return true;
        }

        if (id == R.id.nav_add_item) {
            // Go to Add Product page
            Intent intent = new Intent(activity_homepage.this, activity_add_product.class);
            startActivity(intent);
            return true;
        }

        if (id == R.id.nav_profile) {
            // Go to Profile page
            Intent intent = new Intent(activity_homepage.this, activity_profile.class);
            startActivity(intent);
            return true;
        }

        if (id == R.id.nav_live) {
            // Go to Live Stream or Sign In (adjust when live page ready)
            Intent intent = new Intent(activity_homepage.this, activity_profile.class);
            startActivity(intent);
            return true;
        }

        if (id == R.id.nav_chat) {
            // Go to Chat or Sign In (adjust when chat page ready)
            Intent intent = new Intent(activity_homepage.this, activity_profile.class);
            startActivity(intent);
            return true;
        }
        return true;
    }
}

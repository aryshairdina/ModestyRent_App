package com.example.modestyrent_app;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * activity_home - handles bottom navigation.
 * Clicking Add Item will open activity_add_product.
 * Clicking Profile will open activity_profile.
 */
public class activity_home extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        if (bottomNav == null) {
            // layout not found or id mismatch
            Toast.makeText(this, "BottomNavigationView not found (check layout id)", Toast.LENGTH_LONG).show();
            return;
        }

        // Optional: set default selected item if exists
        Menu menu = bottomNav.getMenu();
        MenuItem homeItem = menu.findItem(R.id.nav_home);
        if (homeItem != null) {
            bottomNav.setSelectedItemId(R.id.nav_home);
        }

        // Listen for item selection
        bottomNav.setOnItemSelectedListener(item -> {
            return handleNavItemSelected(item);
        });
    }

    /**
     * Handle navigation item selection.
     * Returns true if selection handled.
     */
    private boolean handleNavItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_home) {
            // already on home - you may refresh here
            return true;
        }

        if (id == R.id.nav_add_item) {
            // Open Add Product activity
            Intent intent = new Intent(activity_home.this, activity_signin.class);
            startActivity(intent);
            return true;
        }

        if (id == R.id.nav_profile) {
            // Open Profile activity
            Intent intent = new Intent(activity_home.this, activity_signin.class);
            startActivity(intent);
            return true;
        }

        if (id == R.id.nav_live) {
            // Open Profile activity
            Intent intent = new Intent(activity_home.this, activity_signin.class);
            startActivity(intent);
            return true;
        }

        if (id == R.id.nav_chat) {
            // Open Profile activity
            Intent intent = new Intent(activity_home.this, activity_signin.class);
            startActivity(intent);
            return true;
        }

        // Fallback: unknown id
        Toast.makeText(this, "Menu item clicked: " + id, Toast.LENGTH_SHORT).show();
        return true;
    }
}

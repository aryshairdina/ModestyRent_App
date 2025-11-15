package com.example.modestyrent_app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.List;

/**
 * activity_homepage - shows welcome text + product grid (only products with status == "available")
 * Keeps your original welcomeText & Firebase Auth logic and bottom navigation behavior.
 * Added: onResume refresh so newly-added products appear automatically.
 */
public class activity_homepage extends AppCompatActivity {

    private static final String TAG = "activity_homepage";

    private TextView welcomeText;
    private FirebaseAuth mAuth;

    // Product grid fields
    private RecyclerView recyclerProducts;
    private ProductAdapter adapter;
    private final List<Product> products = new ArrayList<>();
    private DatabaseReference productsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_homepage);

        // Initialize views
        welcomeText = findViewById(R.id.welcomeText);
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Check current user and set welcome text (preserve your original logic)
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

        // ========== Bottom Navigation Setup (preserve your logic) ==========
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

        // ---------- NEW: setup product grid ----------
        setupProductGrid();
    }

    /**
     * onResume will refresh the products list so newly added products appear immediately.
     */
    @Override
    protected void onResume() {
        super.onResume();
        // if productsRef is null, setupProductGrid() will set it and call loadProducts anyway.
        // Otherwise just reload to pick up any new items.
        try {
            if (productsRef != null) {
                loadProducts();
            } else {
                setupProductGrid(); // safe guard - will set productsRef then load
            }
        } catch (Exception e) {
            Log.w(TAG, "onResume refresh failed: " + e.getMessage(), e);
        }
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
            // Go to Live Stream or profile placeholder
            Intent intent = new Intent(activity_homepage.this, activity_profile.class);
            startActivity(intent);
            return true;
        }

        if (id == R.id.nav_chat) {
            // Go to Chat or profile placeholder
            Intent intent = new Intent(activity_homepage.this, activity_profile.class);
            startActivity(intent);
            return true;
        }
        return true;
    }

    // ---------------- Product grid setup & loading ----------------
    private void setupProductGrid() {
        recyclerProducts = findViewById(R.id.recyclerProducts);
        if (recyclerProducts == null) {
            Toast.makeText(this, "RecyclerView (recyclerProducts) not found in layout.", Toast.LENGTH_LONG).show();
            return;
        }

        // Use 2 columns grid to match item_product card style
        recyclerProducts.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new ProductAdapter(this, products);
        recyclerProducts.setAdapter(adapter);

        // Resolve database path: try "ModestyRent - App/products" then fallback to "products"
        DatabaseReference rootRef = FirebaseDatabase.getInstance().getReference();
        DatabaseReference candidate = rootRef.child("ModestyRent - App").child("products");

        candidate.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                productsRef = candidate;
            } else {
                productsRef = rootRef.child("products");
            }
            loadProducts();
        }).addOnFailureListener(e -> {
            productsRef = rootRef.child("products");
            loadProducts();
        });
    }

    private void loadProducts() {
        if (productsRef == null) {
            Toast.makeText(this, "Products DB reference not available.", Toast.LENGTH_SHORT).show();
            return;
        }

        productsRef.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                Toast.makeText(activity_homepage.this, "Failed loading products: " + (task.getException() != null ? task.getException().getMessage() : "unknown"), Toast.LENGTH_LONG).show();
                return;
            }

            DataSnapshot snapshot = task.getResult();
            products.clear();

            if (snapshot.exists()) {
                for (DataSnapshot child : snapshot.getChildren()) {
                    try {
                        // Try mapping automatically
                        Product p = child.getValue(Product.class);
                        if (p == null) p = new Product();

                        // Ensure id is set
                        if (p.getId() == null || p.getId().isEmpty()) {
                            p.setId(child.getKey());
                        }

                        // Read status if missing
                        String status = p.getStatus();
                        if (status == null && child.child("status").exists()) {
                            Object sObj = child.child("status").getValue();
                            status = sObj != null ? String.valueOf(sObj) : null;
                            p.setStatus(status);
                        }

                        if (status == null) continue;
                        if (!"available".equalsIgnoreCase(status.trim())) continue;

                        // Price fallback handling (price / day or price)
                        if (p.getPrice() == 0.0) {
                            if (child.child("price / day").exists()) {
                                Object priceObj = child.child("price / day").getValue();
                                if (priceObj != null) {
                                    try {
                                        p.setPrice(Double.parseDouble(String.valueOf(priceObj)));
                                    } catch (NumberFormatException ignored) {}
                                }
                            } else if (child.child("price").exists()) {
                                Object priceObj = child.child("price").getValue();
                                if (priceObj != null) {
                                    try {
                                        p.setPrice(Double.parseDouble(String.valueOf(priceObj)));
                                    } catch (NumberFormatException ignored) {}
                                }
                            }
                        }

                        // Image fallback
                        if ((p.getImageUrls() == null || p.getImageUrls().isEmpty())) {
                            if (child.child("imageUrls").exists()) {
                                ArrayList<String> urls = new ArrayList<>();
                                for (DataSnapshot img : child.child("imageUrls").getChildren()) {
                                    Object v = img.getValue();
                                    if (v != null) urls.add(String.valueOf(v));
                                }
                                if (!urls.isEmpty()) p.setImageUrls(urls);
                            } else if (child.child("imageUrl").exists()) {
                                Object v = child.child("imageUrl").getValue();
                                if (v != null) {
                                    ArrayList<String> urls = new ArrayList<>();
                                    urls.add(String.valueOf(v));
                                    p.setImageUrls(urls);
                                }
                            } else if (child.child("image").exists()) {
                                Object v = child.child("image").getValue();
                                if (v != null) {
                                    ArrayList<String> urls = new ArrayList<>();
                                    urls.add(String.valueOf(v));
                                    p.setImageUrls(urls);
                                }
                            }
                        }

                        products.add(p);

                    } catch (Exception ex) {
                        Log.e(TAG, "Skipping product node due to error", ex);
                    }
                }
            }

            adapter.setList(products);

            if (products.isEmpty()) {
                Toast.makeText(activity_homepage.this, "No available products found.", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(activity_homepage.this, "Error loading products: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    // ---------------- Inner Adapter (defensive) ----------------
    private static class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.VH> {
        private final Context ctx;
        private List<Product> list;

        ProductAdapter(Context ctx, List<Product> list) {
            this.ctx = ctx;
            this.list = list != null ? list : new ArrayList<>();
        }

        void setList(List<Product> list) {
            this.list = list != null ? list : new ArrayList<>();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(ctx).inflate(R.layout.product_card, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Product p = list.get(position);

            // Title
            String title = (p.getName() != null && !p.getName().isEmpty()) ? p.getName() : "No title";
            if (holder.tvTitle != null) holder.tvTitle.setText(title);

            // Price formatting
            double priceVal = p.getPrice();
            String priceText = priceVal > 0 ? String.format(java.util.Locale.getDefault(), "%.2f", priceVal) : "-";
            if (holder.tvPrice != null) holder.tvPrice.setText(priceText);

            // Per day label
            if (holder.tvPerDay != null) holder.tvPerDay.setVisibility(View.VISIBLE);

            // Image
            String img = null;
            if (p.getImageUrls() != null && !p.getImageUrls().isEmpty()) img = p.getImageUrls().get(0);
            if (holder.imgProduct != null) {
                if (img != null && !img.isEmpty()) {
                    Glide.with(ctx)
                            .load(img)
                            .centerCrop()
                            .placeholder(R.drawable.ic_launcher_foreground)
                            .into(holder.imgProduct);
                } else {
                    holder.imgProduct.setImageResource(R.drawable.ic_launcher_foreground);
                }
            }

            // Click -> ProductDetailActivity
            holder.itemView.setOnClickListener(v -> {
                Context c = v.getContext();
                if (p.getId() != null && !p.getId().isEmpty()) {
                    Intent i = new Intent(c, activity_product_details.class);
                    i.putExtra("productId", p.getId());
                    c.startActivity(i);
                } else {
                    Toast.makeText(c, "Product id not available", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public int getItemCount() {
            return list != null ? list.size() : 0;
        }

        static class VH extends RecyclerView.ViewHolder {
            ImageView imgProduct;
            TextView tvTitle, tvPrice, tvPerDay;

            VH(@NonNull View itemView) {
                super(itemView);
                // defensive findViewById
                try { imgProduct = itemView.findViewById(R.id.imgProduct); } catch (Exception e) { imgProduct = null; }
                try { tvTitle = itemView.findViewById(R.id.tvTitle); } catch (Exception e) { tvTitle = null; }
                try { tvPrice = itemView.findViewById(R.id.tvPrice); } catch (Exception e) { tvPrice = null; }
                try { tvPerDay = itemView.findViewById(R.id.tvPerDay); } catch (Exception e) { tvPerDay = null; }
            }
        }
    }
}

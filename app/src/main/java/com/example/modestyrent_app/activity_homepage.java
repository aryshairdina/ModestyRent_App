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
import android.widget.ImageButton;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * activity_homepage - shows welcome text + product grid (only products with status == "available")
 * Keeps your original welcomeText & Firebase Auth logic and bottom navigation behavior.
 * Added: onResume refresh so newly-added products appear automatically and love/like button functionality.
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

        // get currently signed-in user's uid so we can exclude their products
        String currentUid = null;
        try {
            FirebaseUser cu = mAuth.getCurrentUser();
            if (cu != null) currentUid = cu.getUid();
        } catch (Exception ignored) {}

        final String finalCurrentUid = currentUid;

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

                        // Defensive: read owner/userId from common DB keys if model not filled
                        String ownerId = p.getUserId();
                        if ((ownerId == null || ownerId.isEmpty())) {
                            if (child.child("userId").exists()) {
                                Object v = child.child("userId").getValue();
                                ownerId = v != null ? String.valueOf(v) : null;
                            } else if (child.child("userid").exists()) {
                                Object v = child.child("userid").getValue();
                                ownerId = v != null ? String.valueOf(v) : null;
                            } else if (child.child("userUID").exists()) {
                                Object v = child.child("userUID").getValue();
                                ownerId = v != null ? String.valueOf(v) : null;
                            }
                            if (ownerId != null) p.setUserId(ownerId);
                        }

                        // If ownerId matches current user, skip this product (do not show)
                        if (finalCurrentUid != null && ownerId != null && finalCurrentUid.equals(ownerId)) {
                            // skip products created by current user
                            continue;
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

                        // Add product (it passed all filters)
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


    // ---------------- Inner Adapter (with love button support) ----------------
    private static class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.VH> {
        private final Context ctx;
        private List<Product> list;

        // Firebase helpers
        private final FirebaseAuth auth = FirebaseAuth.getInstance();
        private final DatabaseReference usersRootRef = FirebaseDatabase.getInstance().getReference("users");
        private final DatabaseReference globalRootRef = FirebaseDatabase.getInstance().getReference();
        private String uid;

        // cache of liked product ids for quick UI state
        private final Set<String> likedIds = new HashSet<>();

        ProductAdapter(Context ctx, List<Product> list) {
            this.ctx = ctx;
            this.list = list != null ? list : new ArrayList<>();

            FirebaseUser u = auth.getCurrentUser();
            if (u != null) {
                uid = u.getUid();
                loadUserLikes();
            } else {
                uid = null;
            }
        }

        void setList(List<Product> list) {
            this.list = list != null ? list : new ArrayList<>();
            notifyDataSetChanged();
        }

        private void loadUserLikes() {
            if (uid == null) return;
            DatabaseReference likesRef = usersRootRef.child(uid).child("likes");
            likesRef.get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    likedIds.clear();
                    DataSnapshot snap = task.getResult();
                    for (DataSnapshot child : snap.getChildren()) {
                        likedIds.add(child.getKey());
                    }
                    // refresh UI on main thread
                    try {
                        ((android.app.Activity) ctx).runOnUiThread(this::notifyDataSetChanged);
                    } catch (Exception ignored) {
                        notifyDataSetChanged();
                    }
                }
            });
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

            // Set love icon state (requires product_card to have ImageButton with id btnLove)
            boolean isLiked = p.getId() != null && likedIds.contains(p.getId());
            if (holder.btnLove != null) {
                holder.btnLove.setImageResource(isLiked ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
                holder.btnLove.setOnClickListener(v -> {
                    if (auth.getCurrentUser() == null) {
                        Toast.makeText(ctx, "Please sign in to like products", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    toggleLike(p);
                });
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

        /**
         * Toggle like/unlike in Realtime DB for current user.
         * Write under users/{uid}/likes/{productId} = payload (timestamp, productId, name, price, image)
         * Also write inverse under likes/{productId}/{uid} = true (optional)
         */
        private void toggleLike(Product product) {
            if (uid == null || product == null || product.getId() == null) return;

            String pid = product.getId();
            DatabaseReference likeRef = usersRootRef.child(uid).child("likes").child(pid);
            DatabaseReference inverseRef = globalRootRef.child("likes").child(pid).child(uid);

            if (likedIds.contains(pid)) {
                // remove like
                likeRef.removeValue().addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        likedIds.remove(pid);
                        notifyChangedForProduct(pid);
                        Toast.makeText(ctx, "Removed from My Likes", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(ctx, "Failed to remove like", Toast.LENGTH_SHORT).show();
                    }
                });
                inverseRef.removeValue();
            } else {
                // add like (store small snapshot)
                java.util.Map<String, Object> payload = new java.util.HashMap<>();
                payload.put("addedAt", System.currentTimeMillis());
                payload.put("productId", pid);
                payload.put("name", product.getName() != null ? product.getName() : "");
                payload.put("price", product.getPrice());
                String image0 = (product.getImageUrls() != null && !product.getImageUrls().isEmpty()) ? product.getImageUrls().get(0) : "";
                payload.put("image", image0);

                likeRef.setValue(payload).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        likedIds.add(pid);
                        notifyChangedForProduct(pid);
                        Toast.makeText(ctx, "Added to My Likes", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(ctx, "Failed to add like", Toast.LENGTH_SHORT).show();
                    }
                });

                inverseRef.setValue(true);
            }
        }

        private void notifyChangedForProduct(String productId) {
            for (int i = 0; i < list.size(); i++) {
                Product p = list.get(i);
                if (p != null && productId.equals(p.getId())) {
                    final int idx = i;
                    try {
                        ((android.app.Activity) ctx).runOnUiThread(() -> notifyItemChanged(idx));
                    } catch (Exception ignored) {
                        notifyItemChanged(idx);
                    }
                    break;
                }
            }
        }

        static class VH extends RecyclerView.ViewHolder {
            ImageView imgProduct;
            TextView tvTitle, tvPrice, tvPerDay;
            ImageButton btnLove;

            VH(@NonNull View itemView) {
                super(itemView);
                // defensive findViewById
                try { imgProduct = itemView.findViewById(R.id.imgProduct); } catch (Exception e) { imgProduct = null; }
                try { tvTitle = itemView.findViewById(R.id.tvTitle); } catch (Exception e) { tvTitle = null; }
                try { tvPrice = itemView.findViewById(R.id.tvPrice); } catch (Exception e) { tvPrice = null; }
                try { tvPerDay = itemView.findViewById(R.id.tvPerDay); } catch (Exception e) { tvPerDay = null; }
                try { btnLove = itemView.findViewById(R.id.btnLove); } catch (Exception e) { btnLove = null; }
            }
        }
    }
}

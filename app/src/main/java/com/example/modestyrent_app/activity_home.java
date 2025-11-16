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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.List;

/**
 * activity_home - handles bottom navigation and product grid (Home).
 */
public class activity_home extends AppCompatActivity {

    private static final String TAG = "activity_home";

    // ---------- EXISTING BOTTOM NAV CODE (kept unchanged) ----------
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

        // ---------- NEW: initialize product grid below the existing nav setup ----------
        setupProductGrid();
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

    // ----------------- NEW: Fields for product grid -----------------
    private RecyclerView recyclerProducts;
    private ProductAdapter adapter;
    private final List<Product> products = new ArrayList<>();
    private DatabaseReference productsRef;

    private void setupProductGrid() {
        // find recycler from your layout
        recyclerProducts = findViewById(R.id.recyclerProducts);
        if (recyclerProducts == null) {
            // If your layout id is different or RecyclerView missing, let dev know
            Toast.makeText(this, "RecyclerView (recyclerProducts) not found in layout.", Toast.LENGTH_LONG).show();
            return;
        }

        // Grid with 2 columns (like the screenshot)
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
            // load after choosing the ref
            loadProducts();
        }).addOnFailureListener(e -> {
            // fallback if any failure
            productsRef = rootRef.child("products");
            loadProducts();
        });
    }

    /**
     * Load products once from Realtime DB. Only keep those with status == "available" (case-insensitive).
     */
    private void loadProducts() {
        if (productsRef == null) {
            Toast.makeText(this, "Products DB reference not available.", Toast.LENGTH_SHORT).show();
            return;
        }

        productsRef.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                Toast.makeText(activity_home.this, "Failed loading products: " + (task.getException() != null ? task.getException().getMessage() : "unknown"), Toast.LENGTH_LONG).show();
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

                        // Price fallback handling
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

                        // Image fallback handling
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
                Toast.makeText(activity_home.this, "No available products found.", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(activity_home.this, "Error loading products: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    // ----------------- INNER ADAPTER CLASS (defensive, avoids NPE) -----------------
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
            View v = LayoutInflater.from(ctx).inflate(R.layout.product_card_home, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Product p = list.get(position);

            // Title
            String title = (p.getName() != null && !p.getName().isEmpty()) ? p.getName() : "No title";
            if (holder.tvTitle != null) {
                holder.tvTitle.setText(title);
            } else {
                Log.e(TAG, "tvTitle is null in ViewHolder. Check item_product.xml id 'tvTitle'.");
            }

            // Price formatting
            double priceVal = p.getPrice();
            String priceText = priceVal > 0 ? String.format(java.util.Locale.getDefault(), "%.2f", priceVal) : "-";
            if (holder.tvPrice != null) {
                holder.tvPrice.setText(priceText);
            } else {
                Log.e(TAG, "tvPrice is null in ViewHolder. Check item_product.xml id 'tvPrice'.");
            }

            // Per day label
            if (holder.tvPerDay != null) {
                holder.tvPerDay.setVisibility(View.VISIBLE);
            } else {
                Log.w(TAG, "tvPerDay is null in ViewHolder. Check item_product.xml id 'tvPerDay'.");
            }

            // Image
            String img = null;
            if (p.getImageUrls() != null && !p.getImageUrls().isEmpty()) {
                img = p.getImageUrls().get(0);
            }
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
            } else {
                Log.e(TAG, "imgProduct is null in ViewHolder. Check item_product.xml id 'imgProduct'.");
            }

            // Click -> ProductDetailActivity with productId (defensive)
            holder.itemView.setOnClickListener(v -> {
                Context c = v.getContext();
                if (p.getId() != null && !p.getId().isEmpty()) {
                    Intent i = new Intent(c, activity_signin.class);
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
                // Use defensive findViewById; keep nulls if not present
                try {
                    imgProduct = itemView.findViewById(R.id.imgProduct);
                } catch (Exception e) {
                    imgProduct = null;
                }
                try {
                    tvTitle = itemView.findViewById(R.id.tvTitle);
                } catch (Exception e) {
                    tvTitle = null;
                }
                try {
                    tvPrice = itemView.findViewById(R.id.tvPrice);
                } catch (Exception e) {
                    tvPrice = null;
                }
                try {
                    tvPerDay = itemView.findViewById(R.id.tvPerDay);
                } catch (Exception e) {
                    tvPerDay = null;
                }
            }
        }
    }
}

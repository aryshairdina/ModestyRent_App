package com.example.modestyrent_app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class activity_owner_profile extends AppCompatActivity {

    private static final String TAG = "activity_owner_profile";

    private RecyclerView recyclerOwnerProducts;
    private OwnerProductsAdapter adapter;
    private final List<Product> ownerProducts = new ArrayList<>();

    private TextView tvOwnerName, tvOwnerBio, tvOwnerLocation, tvOwnerRating, tvOwnerAvatar, tvProductsCount;
    private View emptyState;

    private DatabaseReference usersRef;
    private DatabaseReference productsRef;
    private DatabaseReference usersLikesRef;
    private DatabaseReference likesRef;

    private String ownerId;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_owner_profile);

        // Get ownerId from intent
        ownerId = getIntent().getStringExtra("ownerId");
        if (ownerId == null || ownerId.isEmpty()) {
            Toast.makeText(this, "Owner information not available", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        initializeViews();
        setupRecyclerView();
        loadOwnerData();
        loadOwnerProducts();
    }

    private void initializeViews() {
        recyclerOwnerProducts = findViewById(R.id.recyclerOwnerProducts);
        tvOwnerName = findViewById(R.id.tvOwnerName);
        tvOwnerBio = findViewById(R.id.tvOwnerBio);
        tvOwnerLocation = findViewById(R.id.tvOwnerLocation);
        tvOwnerRating = findViewById(R.id.tvOwnerRating);
        tvOwnerAvatar = findViewById(R.id.tvOwnerAvatar);
        tvProductsCount = findViewById(R.id.tvProductsCount);
        emptyState = findViewById(R.id.emptyState);

        ImageView backIcon = findViewById(R.id.backIcon);
        backIcon.setOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        recyclerOwnerProducts.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new OwnerProductsAdapter(this, ownerProducts);
        recyclerOwnerProducts.setAdapter(adapter);
    }

    private void loadOwnerData() {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        usersRef = database.getReference("users").child(ownerId);

        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Get owner details
                    String fullName = snapshot.child("fullName").getValue(String.class);
                    if (fullName == null || fullName.isEmpty()) {
                        fullName = snapshot.child("name").getValue(String.class);
                    }

                    String email = snapshot.child("email").getValue(String.class);
                    String phone = snapshot.child("phone").getValue(String.class);
                    String address = snapshot.child("address").getValue(String.class);

                    // Set owner name
                    if (fullName != null && !fullName.isEmpty()) {
                        tvOwnerName.setText(fullName);
                        createAvatarFromName(fullName);
                    } else {
                        tvOwnerName.setText("Owner");
                        createAvatarFromName("Owner");
                    }

                    // Set bio (you can customize this based on available data)
                    if (email != null && !email.isEmpty()) {
                        tvOwnerBio.setText(email);
                    } else {
                        tvOwnerBio.setText("Fashion Enthusiast");
                    }

                    // Set location
                    if (address != null && !address.isEmpty()) {
                        tvOwnerLocation.setText(address);
                    } else {
                        tvOwnerLocation.setText("UiTM Shah Alam");
                    }

                    // Set rating (you might want to calculate this from reviews)
                    tvOwnerRating.setText("4.8 ⭐");

                } else {
                    Toast.makeText(activity_owner_profile.this, "Owner data not found", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(activity_owner_profile.this, "Failed to load owner data", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Error loading owner data: " + error.getMessage());
            }
        });
    }

    private void createAvatarFromName(String fullName) {
        String avatarText = getInitialsFromName(fullName);
        tvOwnerAvatar.setText(avatarText);
    }

    private String getInitialsFromName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return "OO";
        }

        String[] nameParts = fullName.trim().split("\\s+");

        if (nameParts.length == 1) {
            return nameParts[0].substring(0, Math.min(2, nameParts[0].length())).toUpperCase();
        } else {
            String firstInitial = nameParts[0].substring(0, 1).toUpperCase();
            String secondInitial = nameParts[1].substring(0, 1).toUpperCase();
            return firstInitial + secondInitial;
        }
    }

    private void loadOwnerProducts() {
        // Resolve products path (same as your other activities)
        DatabaseReference root = FirebaseDatabase.getInstance().getReference();
        DatabaseReference candidate = root.child("ModestyRent - App").child("products");

        candidate.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                productsRef = candidate;
            } else {
                productsRef = root.child("products");
            }
            fetchOwnerProducts();
        }).addOnFailureListener(e -> {
            productsRef = root.child("products");
            fetchOwnerProducts();
        });
    }

    private void fetchOwnerProducts() {
        if (productsRef == null) return;

        productsRef.orderByChild("userId").equalTo(ownerId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                ownerProducts.clear();

                if (snapshot.exists()) {
                    for (DataSnapshot productSnapshot : snapshot.getChildren()) {
                        Product product = productSnapshot.getValue(Product.class);
                        if (product != null) {
                            // Set product ID if not set
                            if (product.getId() == null || product.getId().isEmpty()) {
                                product.setId(productSnapshot.getKey());
                            }

                            // Handle image URLs fallback
                            handleImageFallback(product, productSnapshot);

                            // Only add products with status "Available"
                            String productStatus = product.getStatus();
                            if (productStatus != null && productStatus.equals("Available")) {
                                ownerProducts.add(product);
                            }
                        }
                    }

                    // Update UI
                    updateProductsUI();
                } else {
                    // No products found
                    updateProductsUI();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(activity_owner_profile.this, "Failed to load products", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Error loading owner products: " + error.getMessage());
                updateProductsUI();
            }
        });
    }

    private void handleImageFallback(Product product, DataSnapshot ds) {
        if ((product.getImageUrls() == null || product.getImageUrls().isEmpty())) {
            if (ds.child("imageUrls").exists()) {
                ArrayList<String> urls = new ArrayList<>();
                for (DataSnapshot img : ds.child("imageUrls").getChildren()) {
                    Object v = img.getValue();
                    if (v != null) urls.add(String.valueOf(v));
                }
                if (!urls.isEmpty()) product.setImageUrls(urls);
            } else if (ds.child("imageUrl").exists()) {
                Object v = ds.child("imageUrl").getValue();
                if (v != null) {
                    ArrayList<String> urls = new ArrayList<>();
                    urls.add(String.valueOf(v));
                    product.setImageUrls(urls);
                }
            } else if (ds.child("image").exists()) {
                Object v = ds.child("image").getValue();
                if (v != null) {
                    ArrayList<String> urls = new ArrayList<>();
                    urls.add(String.valueOf(v));
                    product.setImageUrls(urls);
                }
            }
        }
    }

    private void updateProductsUI() {
        adapter.setList(new ArrayList<>(ownerProducts));
        tvProductsCount.setText("(" + ownerProducts.size() + ")");

        // Show empty state if no products
        if (ownerProducts.isEmpty()) {
            recyclerOwnerProducts.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
        } else {
            recyclerOwnerProducts.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
        }
    }

    // Adapter for Owner Products
    private class OwnerProductsAdapter extends RecyclerView.Adapter<OwnerProductsAdapter.VH> {
        private final Context ctx;
        private List<Product> list;

        OwnerProductsAdapter(Context ctx, List<Product> list) {
            this.ctx = ctx;
            this.list = list != null ? list : new ArrayList<>();
        }

        void setList(List<Product> list) {
            this.list = list != null ? list : new ArrayList<>();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public OwnerProductsAdapter.VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(ctx).inflate(R.layout.product_card, parent, false);
            return new OwnerProductsAdapter.VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull OwnerProductsAdapter.VH holder, int position) {
            Product p = list.get(position);

            // Title
            String title = (p.getName() != null && !p.getName().isEmpty()) ? p.getName() : "No title";
            if (holder.tvTitle != null) holder.tvTitle.setText(title);

            // Price
            double priceVal = p.getPrice();
            String priceText = priceVal > 0 ? String.format(java.util.Locale.getDefault(), "%.2f", priceVal) : "-";
            if (holder.tvPrice != null) holder.tvPrice.setText(priceText);

            // Per day label visible
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

            // Like button functionality
            if (holder.btnLove != null) {
                // Show love button (don't hide it)
                holder.btnLove.setVisibility(View.VISIBLE);

                // Check if current user has liked this product
                checkIfLiked(p.getId(), holder.btnLove);

                // Set click listener for like button
                holder.btnLove.setOnClickListener(v -> {
                    if (currentUser == null) {
                        Toast.makeText(ctx, "Please sign in to like products", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    toggleLike(p.getId(), holder.btnLove);
                });
            }

            // Click opens details
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

        private void checkIfLiked(String productId, ImageButton btnLove) {
            if (currentUser == null || productId == null) {
                btnLove.setImageResource(R.drawable.ic_favorite_border);
                return;
            }

            String uid = currentUser.getUid();
            DatabaseReference userLikeRef = FirebaseDatabase.getInstance()
                    .getReference("users")
                    .child(uid)
                    .child("likes")
                    .child(productId);

            userLikeRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        // Product is liked - show filled heart
                        btnLove.setImageResource(R.drawable.ic_favorite);
                    } else {
                        // Product is not liked - show empty heart
                        btnLove.setImageResource(R.drawable.ic_favorite_border);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    btnLove.setImageResource(R.drawable.ic_favorite_border);
                }
            });
        }

        private void toggleLike(String productId, ImageButton btnLove) {
            if (currentUser == null || productId == null) return;

            String uid = currentUser.getUid();
            DatabaseReference userLikeRef = FirebaseDatabase.getInstance()
                    .getReference("users")
                    .child(uid)
                    .child("likes")
                    .child(productId);

            DatabaseReference productLikeRef = FirebaseDatabase.getInstance()
                    .getReference("likes")
                    .child(productId)
                    .child(uid);

            // Check current like status
            userLikeRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        // Unlike: Remove from both references
                        userLikeRef.removeValue().addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                btnLove.setImageResource(R.drawable.ic_favorite_border);
                                Toast.makeText(ctx, "Removed from likes", Toast.LENGTH_SHORT).show();
                            }
                        });
                        productLikeRef.removeValue();
                    } else {
                        // Like: Add to both references
                        userLikeRef.setValue(true).addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                btnLove.setImageResource(R.drawable.ic_favorite);
                                Toast.makeText(ctx, "Added to likes", Toast.LENGTH_SHORT).show();
                            }
                        });
                        productLikeRef.setValue(true);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(ctx, "Failed to update like", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public int getItemCount() {
            return list != null ? list.size() : 0;
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView imgProduct;
            TextView tvTitle, tvPrice, tvPerDay;
            ImageButton btnLove;

            VH(@NonNull View itemView) {
                super(itemView);
                try { imgProduct = itemView.findViewById(R.id.imgProduct); } catch (Exception e) { imgProduct = null; }
                try { tvTitle = itemView.findViewById(R.id.tvTitle); } catch (Exception e) { tvTitle = null; }
                try { tvPrice = itemView.findViewById(R.id.tvPrice); } catch (Exception e) { tvPrice = null; }
                try { tvPerDay = itemView.findViewById(R.id.tvPerDay); } catch (Exception e) { tvPerDay = null; }
                try { btnLove = itemView.findViewById(R.id.btnLove); } catch (Exception e) { btnLove = null; }
            }
        }
    }
}
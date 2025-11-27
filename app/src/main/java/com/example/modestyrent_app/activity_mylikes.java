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
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.List;

public class activity_mylikes extends AppCompatActivity {

    private static final String TAG = "activity_mylikes";

    private RecyclerView recycler;
    private MyLikesAdapter adapter;
    private final List<Product> likedProducts = new ArrayList<>();

    private FirebaseAuth mAuth;
    private DatabaseReference usersLikesRef;   // users/{uid}/likes
    private DatabaseReference productsRef;     // resolved products path

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mylikes);

        recycler = findViewById(R.id.recyclerMyLikes);
        ImageView backIcon = findViewById(R.id.backIcon);
        recycler.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new MyLikesAdapter(this, likedProducts);
        recycler.setAdapter(adapter);

        backIcon.setOnClickListener(v -> finish());

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser u = mAuth.getCurrentUser();
        if (u == null) {
            Toast.makeText(this, "Please sign in first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        String uid = u.getUid();
        usersLikesRef = FirebaseDatabase.getInstance().getReference("users").child(uid).child("likes");

        // Resolve products path (same fallback as homepage)
        DatabaseReference root = FirebaseDatabase.getInstance().getReference();
        DatabaseReference candidate = root.child("ModestyRent - App").child("products");
        candidate.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                productsRef = candidate;
            } else {
                productsRef = root.child("products");
            }
            loadLikedProductIds();
        }).addOnFailureListener(e -> {
            productsRef = root.child("products");
            loadLikedProductIds();
        });
    }

    private void loadLikedProductIds() {
        if (usersLikesRef == null) return;

        usersLikesRef.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                Log.w(TAG, "Failed to load likes", task.getException());
                Toast.makeText(activity_mylikes.this, "Failed to load likes", Toast.LENGTH_SHORT).show();
                return;
            }

            DataSnapshot snap = task.getResult();
            likedProducts.clear();

            if (snap.exists()) {
                List<String> ids = new ArrayList<>();
                for (DataSnapshot child : snap.getChildren()) {
                    String pid = child.getKey();
                    if (pid != null) ids.add(pid);
                }
                fetchProductsByIds(ids);
            } else {
                adapter.setList(new ArrayList<>());
                Toast.makeText(activity_mylikes.this, "No liked products found", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchProductsByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            adapter.setList(new ArrayList<>());
            return;
        }

        likedProducts.clear();
        // fetch product nodes individually, update adapter as responses come
        for (String pid : ids) {
            productsRef.child(pid).get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                    DataSnapshot ds = task.getResult();
                    Product p = ds.getValue(Product.class);
                    if (p == null) p = new Product();
                    if (p.getId() == null || p.getId().isEmpty()) p.setId(ds.getKey());

                    // image fallback handling
                    if ((p.getImageUrls() == null || p.getImageUrls().isEmpty())) {
                        if (ds.child("imageUrls").exists()) {
                            ArrayList<String> urls = new ArrayList<>();
                            for (DataSnapshot img : ds.child("imageUrls").getChildren()) {
                                Object v = img.getValue();
                                if (v != null) urls.add(String.valueOf(v));
                            }
                            if (!urls.isEmpty()) p.setImageUrls(urls);
                        } else if (ds.child("imageUrl").exists()) {
                            Object v = ds.child("imageUrl").getValue();
                            if (v != null) {
                                ArrayList<String> urls = new ArrayList<>();
                                urls.add(String.valueOf(v));
                                p.setImageUrls(urls);
                            }
                        } else if (ds.child("image").exists()) {
                            Object v = ds.child("image").getValue();
                            if (v != null) {
                                ArrayList<String> urls = new ArrayList<>();
                                urls.add(String.valueOf(v));
                                p.setImageUrls(urls);
                            }
                        }
                    }

                    // Check product status - only add if available
                    String productStatus = p.getStatus();
                    if (productStatus == null && ds.child("status").exists()) {
                        productStatus = ds.child("status").getValue(String.class);
                    }

                    // Only add product if status is not "not available"
                    if (productStatus == null || !productStatus.equalsIgnoreCase("unavailable")) {
                        likedProducts.add(p);
                    }
                    // update adapter continuously
                    adapter.setList(new ArrayList<>(likedProducts));
                } else {
                    // product missing or removed — skip silently
                }
            });
        }
    }

    // ---------------- Adapter for My Likes ----------------
    private class MyLikesAdapter extends RecyclerView.Adapter<MyLikesAdapter.VH> {
        private final Context ctx;
        private List<Product> list;

        MyLikesAdapter(Context ctx, List<Product> list) {
            this.ctx = ctx;
            this.list = list != null ? list : new ArrayList<>();
        }

        void setList(List<Product> list) {
            this.list = list != null ? list : new ArrayList<>();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public MyLikesAdapter.VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(ctx).inflate(R.layout.product_card, parent, false);
            return new MyLikesAdapter.VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull MyLikesAdapter.VH holder, int position) {
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

            // Show liked state (this screen lists liked items so show filled heart)
            if (holder.btnLove != null) {
                holder.btnLove.setImageResource(R.drawable.ic_favorite);
                holder.btnLove.setOnClickListener(v -> {
                    // unlike: remove from users/{uid}/likes/{pid} and likes/{pid}/{uid}
                    FirebaseUser u = mAuth.getCurrentUser();
                    if (u == null) {
                        Toast.makeText(ctx, "Please sign in", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String uid = u.getUid();
                    String pid = p.getId();
                    if (pid == null) return;

                    DatabaseReference likeRef = FirebaseDatabase.getInstance().getReference("users").child(uid).child("likes").child(pid);
                    DatabaseReference inverseRef = FirebaseDatabase.getInstance().getReference().child("likes").child(pid).child(uid);

                    likeRef.removeValue().addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            // remove from local list and update adapter
                            list.remove(p);
                            notifyDataSetChanged();
                            Toast.makeText(ctx, "Removed from My Likes", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(ctx, "Failed to remove like", Toast.LENGTH_SHORT).show();
                        }
                    });
                    inverseRef.removeValue();
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
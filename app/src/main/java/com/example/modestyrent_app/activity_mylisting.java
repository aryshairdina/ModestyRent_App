package com.example.modestyrent_app;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

// Realtime Database imports (correct ones)
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class activity_mylisting extends AppCompatActivity {

    private RecyclerView recyclerView;
    private LinearLayout emptyState;
    private Button btnAddItem;
    private EditText searchBar;
    private ImageView backIcon;

    private FirebaseAuth auth;

    private ArrayList<Product> productList = new ArrayList<>();
    private MyListingAdapter adapter;

    private static final String TAG = "MyListingActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mylisting);

        auth = FirebaseAuth.getInstance();

        recyclerView = findViewById(R.id.recyclerMyListings);
        emptyState = findViewById(R.id.emptyStateContainer);
        btnAddItem = findViewById(R.id.btnAddItem);
        searchBar = findViewById(R.id.searchBar);
        backIcon = findViewById(R.id.backIcon);

        // Recycler setup
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MyListingAdapter(productList, this);
        recyclerView.setAdapter(adapter);

        // ===== ITEM CLICK: OPEN LISTING DETAILS =====
        // Attach a touch listener to RecyclerView so tapping an item opens activity_listing_details
        final GestureDetector gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                return true;
            }
        });

        recyclerView.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                View child = rv.findChildViewUnder(e.getX(), e.getY());
                if (child != null && gestureDetector.onTouchEvent(e)) {
                    int pos = rv.getChildAdapterPosition(child);
                    if (pos >= 0 && pos < productList.size()) {
                        Product clicked = productList.get(pos);
                        if (clicked != null && clicked.getId() != null && !clicked.getId().isEmpty()) {
                            Intent intent = new Intent(activity_mylisting.this, activity_listing_details.class);
                            intent.putExtra("productId", clicked.getId());
                            startActivity(intent);
                        } else {
                            Log.w(TAG, "Clicked product has no ID (pos=" + pos + ")");
                        }
                    } else {
                        Log.w(TAG, "Clicked position out of range: " + pos);
                    }
                    return true;
                }
                return false;
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                // no-op
            }

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
                // no-op
            }
        });
        // ===== END ITEM CLICK =====

        // Load listings (Realtime)
        loadMyListingsRealtime();

        // Back button
        backIcon.setOnClickListener(v -> onBackPressed());

        // Add item → go to Add Product Page
        btnAddItem.setOnClickListener(v -> {
            Intent i = new Intent(activity_mylisting.this, activity_add_product.class);
            startActivity(i);
        });

        // Search filter (calls adapter.filter)
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (adapter != null) adapter.filter(s.toString());
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh list each time activity resumes (useful when returning from add/edit)
        loadMyListingsRealtime();
    }

    /**
     * Load products from Firebase Realtime Database for the currently logged-in user.
     */
    private void loadMyListingsRealtime() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Log.d(TAG, "No signed-in user. Showing empty state.");
            recyclerView.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
            productList.clear();
            if (adapter != null) adapter.notifyDataSetChanged();
            return;
        }

        String userId = user.getUid();
        DatabaseReference productsRef = FirebaseDatabase.getInstance().getReference("products");

        // Query: products where userId == currentUser.uid
        Query q = productsRef.orderByChild("userId").equalTo(userId);

        q.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                productList.clear();

                Log.d(TAG, "products snapshot children: " + snapshot.getChildrenCount());

                for (DataSnapshot child : snapshot.getChildren()) {
                    // Try POJO mapping first (requires Product with no-arg constructor + setters)
                    Product p = child.getValue(Product.class);
                    if (p != null) {
                        // If id wasn't stored inside object, set it from key
                        if (p.getId() == null || p.getId().isEmpty()) {
                            p.setId(child.getKey());
                        }
                        productList.add(p);
                        continue;
                    }

                    // Defensive mapping (if POJO mapping fails)
                    Product p2 = new Product();
                    p2.setId(child.getKey());
                    p2.setName(child.child("name").getValue(String.class));
                    Object priceObj = child.child("price").getValue();
                    if (priceObj != null) {
                        try {
                            p2.setPrice(Double.parseDouble(String.valueOf(priceObj)));
                        } catch (NumberFormatException e) {
                            p2.setPrice(0);
                        }
                    }
                    p2.setSize(child.child("size").getValue(String.class));
                    p2.setStatus(child.child("status").getValue(String.class));
                    p2.setCategory(child.child("category").getValue(String.class));
                    p2.setDescription(child.child("description").getValue(String.class));
                    p2.setUserId(child.child("userId").getValue(String.class));

                    ArrayList<String> colors = new ArrayList<>();
                    for (DataSnapshot c : child.child("colors").getChildren()) {
                        String col = c.getValue(String.class);
                        if (col != null) colors.add(col);
                    }
                    p2.setColors(colors);

                    ArrayList<String> imgs = new ArrayList<>();
                    for (DataSnapshot img : child.child("imageUrls").getChildren()) {
                        String u = img.getValue(String.class);
                        if (u != null) imgs.add(u);
                    }
                    p2.setImageUrls(imgs);

                    productList.add(p2);
                }

                // Update UI
                runOnUiThread(() -> {
                    if (productList.isEmpty()) {
                        recyclerView.setVisibility(View.GONE);
                        emptyState.setVisibility(View.VISIBLE);
                    } else {
                        recyclerView.setVisibility(View.VISIBLE);
                        emptyState.setVisibility(View.GONE);
                    }
                    if (adapter != null) {
                        // refresh adapter's data source (adapter should use the same list reference)
                        adapter.notifyDataSetChanged();
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Realtime load cancelled: " + error.getMessage());
                runOnUiThread(() -> {
                    recyclerView.setVisibility(View.GONE);
                    emptyState.setVisibility(View.VISIBLE);
                });
            }
        });
    }
}

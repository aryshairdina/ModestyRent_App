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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class activity_mylisting extends AppCompatActivity {

    private static final String TAG = "MyListingActivity";

    private RecyclerView recyclerView;
    private LinearLayout emptyState;
    private Button btnAddItem;
    private EditText searchBar;
    private ImageView backIcon;

    private FirebaseAuth auth;

    private final ArrayList<Product> productList = new ArrayList<>();
    private MyListingAdapter adapter;

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
        adapter = new MyListingAdapter(this);
        recyclerView.setAdapter(adapter);

        // ITEM CLICK → open listing details
        GestureDetector gestureDetector =
                new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
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
                    Product clicked = adapter.getItemAt(pos);
                    if (clicked != null && clicked.getId() != null) {
                        Intent i = new Intent(activity_mylisting.this, activity_listing_details.class);
                        i.putExtra("productId", clicked.getId());
                        startActivity(i);
                    }
                    return true;
                }
                return false;
            }

            @Override public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {}
            @Override public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {}
        });

        // Back
        backIcon.setOnClickListener(v -> onBackPressed());

        // Add item
        btnAddItem.setOnClickListener(v ->
                startActivity(new Intent(this, activity_add_product.class)));

        // SEARCH
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }
        });

        loadMyListingsRealtime();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadMyListingsRealtime();
    }

    // ---------------- LOAD DATA ----------------

    private void loadMyListingsRealtime() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            showEmpty();
            return;
        }

        DatabaseReference productsRef =
                FirebaseDatabase.getInstance().getReference("products");

        Query q = productsRef.orderByChild("userId").equalTo(user.getUid());

        q.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                productList.clear();

                for (DataSnapshot child : snapshot.getChildren()) {
                    Product p = child.getValue(Product.class);
                    if (p == null) continue;

                    if (p.getId() == null || p.getId().isEmpty()) {
                        p.setId(child.getKey());
                    }
                    productList.add(p);
                }

                runOnUiThread(() -> {
                    if (productList.isEmpty()) {
                        showEmpty();
                    } else {
                        recyclerView.setVisibility(View.VISIBLE);
                        emptyState.setVisibility(View.GONE);
                    }
                    adapter.setData(productList);
                    adapter.filter(searchBar.getText().toString());
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, error.getMessage());
                showEmpty();
            }
        });
    }

    private void showEmpty() {
        recyclerView.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
        adapter.setData(new ArrayList<>());
    }
}

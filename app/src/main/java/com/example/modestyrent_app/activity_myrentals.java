package com.example.modestyrent_app;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import java.util.*;

public class activity_myrentals extends AppCompatActivity {

    private RecyclerView rentalsRecyclerView;
    private RentalAdapter rentalAdapter;
    private ProgressBar loadingProgress;
    private View emptyState;
    private EditText searchBar;
    private ChipGroup filterChipGroup;

    private DatabaseReference bookingsRef, productsRef, usersRef, deliveriesRef, chatsRef;
    private FirebaseAuth mAuth;
    private List<Rental> rentalList = new ArrayList<>();
    private List<Rental> filteredList = new ArrayList<>();
    private Map<String, String> productImageMap = new HashMap<>();
    private Map<String, String> ownerNameMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_myrentals);

        initializeViews();
        setupFirebase();
        setupRecyclerView();
        setupListeners();
        loadProductsAndUsers();
    }

    private void initializeViews() {
        rentalsRecyclerView = findViewById(R.id.rentalsRecyclerView);
        loadingProgress = findViewById(R.id.loadingProgress);
        emptyState = findViewById(R.id.emptyState);
        searchBar = findViewById(R.id.searchBar);
        filterChipGroup = findViewById(R.id.filterChipGroup);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        findViewById(R.id.btnBrowse).setOnClickListener(v -> {
            startActivity(new Intent(this, activity_homepage.class));
            finish();
        });
    }

    private void setupFirebase() {
        mAuth = FirebaseAuth.getInstance();
        bookingsRef = FirebaseDatabase.getInstance().getReference("bookings");
        productsRef = FirebaseDatabase.getInstance().getReference("products");
        usersRef = FirebaseDatabase.getInstance().getReference("users");
        deliveriesRef = FirebaseDatabase.getInstance().getReference("deliveries");
        chatsRef = FirebaseDatabase.getInstance().getReference("chats");
    }

    private void setupRecyclerView() {
        rentalAdapter = new RentalAdapter(filteredList, productImageMap, ownerNameMap);
        rentalsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        rentalsRecyclerView.setAdapter(rentalAdapter);
    }

    private void setupListeners() {
        // Search functionality
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterRentals(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Filter chips
        filterChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> filterRentals());
    }

    private void loadProductsAndUsers() {
        loadingProgress.setVisibility(View.VISIBLE);
        productsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                productImageMap.clear();
                for (DataSnapshot productSnapshot : snapshot.getChildren()) {
                    String productId = productSnapshot.getKey();
                    DataSnapshot imageUrlsSnapshot = productSnapshot.child("imageUrls");
                    if (imageUrlsSnapshot.exists() && imageUrlsSnapshot.getChildrenCount() > 0) {
                        String firstImageUrl = imageUrlsSnapshot.child("0").getValue(String.class);
                        if (firstImageUrl != null) productImageMap.put(productId, firstImageUrl);
                    }
                }
                loadUsers();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(activity_myrentals.this, "Failed to load products", Toast.LENGTH_SHORT).show();
                loadUsers();
            }
        });
    }

    private void loadUsers() {
        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                ownerNameMap.clear();
                for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                    String userId = userSnapshot.getKey();
                    String fullName = userSnapshot.child("fullName").getValue(String.class);
                    if (fullName != null) ownerNameMap.put(userId, fullName);
                }
                loadRentals();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(activity_myrentals.this, "Failed to load users", Toast.LENGTH_SHORT).show();
                loadRentals();
            }
        });
    }

    private void loadRentals() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "Please sign in to view rentals", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        String currentUserId = mAuth.getCurrentUser().getUid();
        Query userRentalsQuery = bookingsRef.orderByChild("renterId").equalTo(currentUserId);

        userRentalsQuery.addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                rentalList.clear();
                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    try {
                        Rental rental = parseRentalManually(dataSnapshot);
                        rentalList.add(rental);
                    } catch (Exception e) {
                        Toast.makeText(activity_myrentals.this, "Error parsing rental data", Toast.LENGTH_SHORT).show();
                    }
                }
                filterRentals();
                loadingProgress.setVisibility(View.GONE);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                loadingProgress.setVisibility(View.GONE);
                Toast.makeText(activity_myrentals.this, "Failed to load rentals: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private Rental parseRentalManually(DataSnapshot dataSnapshot) {
        Rental rental = new Rental();
        rental.setBookingId(dataSnapshot.getKey());
        rental.setBookingNumber(getStringValue(dataSnapshot, "bookingNumber"));
        rental.setProductName(getStringValue(dataSnapshot, "productName"));
        rental.setProductId(getStringValue(dataSnapshot, "productId"));
        rental.setOwnerId(getStringValue(dataSnapshot, "ownerId"));
        rental.setRenterName(getStringValue(dataSnapshot, "renterName"));
        Long startDateMillis = getLongValue(dataSnapshot, "startDate");
        Long endDateMillis = getLongValue(dataSnapshot, "endDate");
        rental.setStartDate(startDateMillis != null ? String.valueOf(startDateMillis) : "");
        rental.setEndDate(endDateMillis != null ? String.valueOf(endDateMillis) : "");
        rental.setRentalAmount(getDoubleValue(dataSnapshot, "rentalAmount"));
        rental.setDepositAmount(getDoubleValue(dataSnapshot, "depositAmount"));
        rental.setTotalAmount(getDoubleValue(dataSnapshot, "totalAmount"));
        rental.setDeliveryOption(getStringValue(dataSnapshot, "deliveryOption"));
        rental.setStatus(getStringValue(dataSnapshot, "status"));
        rental.setDeliveryStatus(getStringValue(dataSnapshot, "deliveryStatus"));
        rental.setPaymentStatus(getStringValue(dataSnapshot, "paymentStatus"));
        return rental;
    }

    private String getStringValue(DataSnapshot dataSnapshot, String key) {
        DataSnapshot child = dataSnapshot.child(key);
        if (child.exists()) {
            Object value = child.getValue();
            return value != null ? value.toString() : "";
        }
        return "";
    }

    private Long getLongValue(DataSnapshot dataSnapshot, String key) {
        DataSnapshot child = dataSnapshot.child(key);
        if (child.exists()) {
            Object value = child.getValue();
            if (value instanceof Long) return (Long) value;
            else if (value instanceof Integer) return ((Integer) value).longValue();
            else if (value instanceof String) {
                try { return Long.parseLong((String) value); } catch (NumberFormatException e) { return null; }
            }
        }
        return null;
    }

    private double getDoubleValue(DataSnapshot dataSnapshot, String key) {
        DataSnapshot child = dataSnapshot.child(key);
        if (child.exists()) {
            Object value = child.getValue();
            if (value instanceof Long) return ((Long) value).doubleValue();
            else if (value instanceof Double) return (Double) value;
            else if (value instanceof String) {
                try { return Double.parseDouble((String) value); } catch (NumberFormatException e) { return 0.0; }
            }
        }
        return 0.0;
    }

    private void filterRentals() {
        filteredList.clear();
        String searchQuery = searchBar.getText().toString().toLowerCase().trim();
        List<String> selectedStatuses = getSelectedStatuses();
        for (Rental rental : rentalList) {
            boolean matchesSearch = searchQuery.isEmpty() ||
                    (rental.getBookingNumber() != null && rental.getBookingNumber().toLowerCase().contains(searchQuery));
            boolean matchesStatus = selectedStatuses.isEmpty() || (rental.getStatus() != null && selectedStatuses.contains(rental.getStatus().toLowerCase()));
            if (matchesSearch && matchesStatus) filteredList.add(rental);
        }
        rentalAdapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private List<String> getSelectedStatuses() {
        List<String> selectedStatuses = new ArrayList<>();
        boolean showAll = false;
        for (int i = 0; i < filterChipGroup.getChildCount(); i++) {
            Chip chip = (Chip) filterChipGroup.getChildAt(i);
            if (chip.isChecked()) {
                int chipId = chip.getId();
                if (chipId == R.id.chipAll) showAll = true;
                else if (chipId == R.id.chipConfirmed) selectedStatuses.add("confirmed");
                else if (chipId == R.id.chipOnRent) selectedStatuses.add("onrent");
                else if (chipId == R.id.chipCompleted) selectedStatuses.add("completed");
            }
        }
        if (showAll || selectedStatuses.isEmpty()) return new ArrayList<>();
        return selectedStatuses;
    }

    private void updateEmptyState() {
        if (filteredList.isEmpty() && rentalList.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            rentalsRecyclerView.setVisibility(View.GONE);
        } else if (filteredList.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            rentalsRecyclerView.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            rentalsRecyclerView.setVisibility(View.VISIBLE);
        }
    }
}
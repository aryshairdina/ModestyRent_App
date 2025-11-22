package com.example.modestyrent_app;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import java.util.*;

public class activity_booking_requests extends AppCompatActivity {

    private RecyclerView bookingsRecyclerView;
    private BookingAdapter bookingAdapter;
    private ProgressBar loadingProgress;
    private View emptyState;
    private EditText searchBar;
    private MaterialCardView statusFilterCard, dateFilterCard;
    private TextView statusFilterText, dateFilterText;

    private DatabaseReference bookingsRef, productsRef, usersRef;
    private FirebaseAuth mAuth;
    private List<Booking> bookingList = new ArrayList<>();
    private List<Booking> filteredList = new ArrayList<>();
    private Map<String, String> productImageMap = new HashMap<>();
    private Map<String, String> renterNameMap = new HashMap<>();
    private Map<String, String> renterPhoneMap = new HashMap<>();

    // Filter variables
    private String selectedStatus = "All";
    private String selectedDateFilter = "All";
    private String currentSearchQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_requests);

        initializeViews();
        setupFirebase();
        setupRecyclerView();
        setupListeners();
        loadProductsAndUsers();
    }

    private void initializeViews() {
        bookingsRecyclerView = findViewById(R.id.bookingsRecyclerView);
        loadingProgress = findViewById(R.id.loadingProgress);
        emptyState = findViewById(R.id.emptyState);
        searchBar = findViewById(R.id.searchBar);
        statusFilterCard = findViewById(R.id.statusFilterCard);
        dateFilterCard = findViewById(R.id.dateFilterCard);
        statusFilterText = findViewById(R.id.statusFilterText);
        dateFilterText = findViewById(R.id.dateFilterText);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
    }

    private void setupFirebase() {
        mAuth = FirebaseAuth.getInstance();
        bookingsRef = FirebaseDatabase.getInstance().getReference("bookings");
        productsRef = FirebaseDatabase.getInstance().getReference("products");
        usersRef = FirebaseDatabase.getInstance().getReference("users");
    }

    private void setupRecyclerView() {
        bookingAdapter = new BookingAdapter(filteredList, productImageMap, renterNameMap, renterPhoneMap, new BookingAdapter.BookingActionListener() {
            @Override
            public void onPrepareDelivery(Booking booking) {
                prepareDelivery(booking);
            }

            @Override
            public void onConfirmReadyForPickup(Booking booking) {
                confirmReadyForPickup(booking);
            }

            @Override
            public void onContactRenter(Booking booking) {
                contactRenter(booking);
            }

            @Override
            public void onInspectReturn(Booking booking) {
                inspectReturn(booking);
            }

            @Override
            public void onViewBookingDetails(Booking booking) {
                viewBookingDetails(booking);
            }

            @Override
            public void onAwaitReturn(Booking booking) {
                // No action needed, just show message
                Toast.makeText(activity_booking_requests.this, "Awaiting return of " + booking.getProductName(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onArrangeReturn(Booking booking) {
                arrangeReturn(booking);
            }

            @Override
            public void onLeaveReview(Booking booking) {
                leaveReview(booking);
            }

            @Override
            public void onViewTransaction(Booking booking) {
                viewTransaction(booking);
            }

            @Override
            public void onViewDispute(Booking booking) {
                viewDispute(booking);
            }
        });
        bookingsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        bookingsRecyclerView.setAdapter(bookingAdapter);
    }

    private void setupListeners() {
        // Search functionality
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString().toLowerCase().trim();
                filterBookings();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Status filter
        statusFilterCard.setOnClickListener(v -> showStatusFilterDialog());

        // Date filter
        dateFilterCard.setOnClickListener(v -> showDateFilterDialog());
    }

    private void showStatusFilterDialog() {
        String[] statusOptions = {"All", "Confirmed", "PreparingDelivery", "OutForDelivery", "OnRent", "ReturnRequested", "AwaitingInspection", "Completed", "Dispute"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Filter by Status");
        builder.setItems(statusOptions, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                selectedStatus = statusOptions[which];
                statusFilterText.setText(selectedStatus);
                filterBookings();
            }
        });
        builder.show();
    }

    private void showDateFilterDialog() {
        String[] dateOptions = {"All", "Today", "This Week", "This Month", "Upcoming", "Past"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Filter by Date");
        builder.setItems(dateOptions, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                selectedDateFilter = dateOptions[which];
                dateFilterText.setText(selectedDateFilter);
                filterBookings();
            }
        });
        builder.show();
    }

    private void loadProductsAndUsers() {
        loadingProgress.setVisibility(View.VISIBLE);

        // Load products to get images
        productsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                productImageMap.clear();
                for (DataSnapshot productSnapshot : snapshot.getChildren()) {
                    String productId = productSnapshot.getKey();
                    DataSnapshot imageUrlsSnapshot = productSnapshot.child("imageUrls");
                    if (imageUrlsSnapshot.exists() && imageUrlsSnapshot.getChildrenCount() > 0) {
                        String firstImageUrl = imageUrlsSnapshot.child("0").getValue(String.class);
                        if (firstImageUrl != null) {
                            productImageMap.put(productId, firstImageUrl);
                        }
                    }
                }
                loadUsers();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(activity_booking_requests.this, "Failed to load products", Toast.LENGTH_SHORT).show();
                loadUsers();
            }
        });
    }

    private void loadUsers() {
        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                renterNameMap.clear();
                renterPhoneMap.clear();
                for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                    String userId = userSnapshot.getKey();
                    String fullName = userSnapshot.child("fullName").getValue(String.class);
                    String phone = userSnapshot.child("phone").getValue(String.class);
                    if (fullName != null) {
                        renterNameMap.put(userId, fullName);
                    }
                    if (phone != null) {
                        renterPhoneMap.put(userId, phone);
                    }
                }
                loadBookings();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(activity_booking_requests.this, "Failed to load users", Toast.LENGTH_SHORT).show();
                loadBookings();
            }
        });
    }

    private void loadBookings() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "Please sign in to view bookings", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String currentUserId = mAuth.getCurrentUser().getUid();
        Query ownerBookingsQuery = bookingsRef.orderByChild("ownerId").equalTo(currentUserId);

        ownerBookingsQuery.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                bookingList.clear();
                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    try {
                        Booking booking = dataSnapshot.getValue(Booking.class);
                        if (booking != null) {
                            booking.setBookingId(dataSnapshot.getKey());
                            bookingList.add(booking);
                        }
                    } catch (Exception e) {
                        Toast.makeText(activity_booking_requests.this, "Error parsing booking data", Toast.LENGTH_SHORT).show();
                    }
                }
                filterBookings();
                loadingProgress.setVisibility(View.GONE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                loadingProgress.setVisibility(View.GONE);
                Toast.makeText(activity_booking_requests.this, "Failed to load bookings: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void filterBookings() {
        filteredList.clear();

        for (Booking booking : bookingList) {
            boolean matchesSearch = currentSearchQuery.isEmpty() ||
                    (booking.getProductName() != null && booking.getProductName().toLowerCase().contains(currentSearchQuery)) ||
                    (booking.getRenterName() != null && booking.getRenterName().toLowerCase().contains(currentSearchQuery)) ||
                    (booking.getBookingNumber() != null && booking.getBookingNumber().toLowerCase().contains(currentSearchQuery));

            boolean matchesStatus = selectedStatus.equals("All") ||
                    (booking.getStatus() != null && booking.getStatus().equals(selectedStatus));

            boolean matchesDate = selectedDateFilter.equals("All") ||
                    matchesDateFilter(booking, selectedDateFilter);

            if (matchesSearch && matchesStatus && matchesDate) {
                filteredList.add(booking);
            }
        }

        bookingAdapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private boolean matchesDateFilter(Booking booking, String dateFilter) {
        long startDate = booking.getStartDate();
        Calendar calendar = Calendar.getInstance();
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        Calendar startCal = Calendar.getInstance();
        startCal.setTimeInMillis(startDate);

        switch (dateFilter) {
            case "Today":
                return startCal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                        startCal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
            case "This Week":
                Calendar weekStart = (Calendar) today.clone();
                weekStart.add(Calendar.DAY_OF_WEEK, today.getFirstDayOfWeek() - today.get(Calendar.DAY_OF_WEEK));
                return !startCal.before(weekStart) && !startCal.after(today);
            case "This Month":
                return startCal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                        startCal.get(Calendar.MONTH) == today.get(Calendar.MONTH);
            case "Upcoming":
                return startCal.after(today);
            case "Past":
                return startCal.before(today);
            default:
                return true;
        }
    }

    private void updateEmptyState() {
        if (filteredList.isEmpty() && bookingList.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            bookingsRecyclerView.setVisibility(View.GONE);
        } else if (filteredList.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            bookingsRecyclerView.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            bookingsRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    // Action Methods
    private void prepareDelivery(Booking booking) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "PreparingDelivery");
        updates.put("preparationTime", System.currentTimeMillis());

        bookingsRef.child(booking.getBookingId()).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Delivery preparation started", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update status", Toast.LENGTH_SHORT).show();
                });
    }

    private void confirmReadyForPickup(Booking booking) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("deliveryStatus", "ReadyForPickup");
        updates.put("readyForPickupTime", System.currentTimeMillis());

        bookingsRef.child(booking.getBookingId()).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Item ready for pickup", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update status", Toast.LENGTH_SHORT).show();
                });
    }

    private void contactRenter(Booking booking) {
        String currentUserId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;

        if (currentUserId == null) {
            Toast.makeText(this, "Please login to chat", Toast.LENGTH_SHORT).show();
            return;
        }

        String renterId = booking.getRenterId();
        String productId = booking.getProductId();

        if (renterId == null) {
            Toast.makeText(this, "Renter information not available", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get renter name for the chat
        String renterName = renterNameMap.get(renterId);
        if (renterName == null) {
            renterName = "Renter";
        }

        // Generate chat ID using the same format as your existing chats
        String chatId = generateChatId(currentUserId, renterId);

        Intent chatIntent = new Intent(this, activity_chat_owner.class);
        chatIntent.putExtra("chatId", chatId);
        chatIntent.putExtra("renterId", renterId);
        chatIntent.putExtra("productId", productId);
        chatIntent.putExtra("renterName", renterName);
        chatIntent.putExtra("bookingId", booking.getBookingId());
        this.startActivity(chatIntent);
    }

    private void inspectReturn(Booking booking) {
        Intent intent = new Intent(this, activity_inspection.class);
        intent.putExtra("bookingId", booking.getBookingId());
        intent.putExtra("productId", booking.getProductId());
        intent.putExtra("renterId", booking.getRenterId());
        this.startActivity(intent);
    }

    private void viewBookingDetails(Booking booking) {
        Intent intent = new Intent(this, activity_rentals_details.class);
        intent.putExtra("bookingId", booking.getBookingId());
        intent.putExtra("productId", booking.getProductId());
        intent.putExtra("renterId", booking.getRenterId());
        this.startActivity(intent);
    }

    private void arrangeReturn(Booking booking) {
        Intent intent = new Intent(this, activity_arrange_return.class);
        intent.putExtra("bookingId", booking.getBookingId());
        intent.putExtra("productId", booking.getProductId());
        intent.putExtra("renterId", booking.getRenterId());
        this.startActivity(intent);
    }

    private void leaveReview(Booking booking) {
        Intent intent = new Intent(this, activity_leave_review.class);
        intent.putExtra("bookingId", booking.getBookingId());
        intent.putExtra("renterId", booking.getRenterId());
        intent.putExtra("productId", booking.getProductId());
        this.startActivity(intent);
    }

    private void viewTransaction(Booking booking) {
        Intent intent = new Intent(this, activity_transaction_details.class);
        intent.putExtra("bookingId", booking.getBookingId());
        this.startActivity(intent);
    }

    private void viewDispute(Booking booking) {
        Intent intent = new Intent(this, activity_dispute_details.class);
        intent.putExtra("bookingId", booking.getBookingId());
        this.startActivity(intent);
    }

    private String generateChatId(String userId1, String userId2) {
        if (userId1 == null || userId2 == null) {
            return "invalid_chat";
        }

        // Sort the user IDs to ensure consistent chat ID generation
        if (userId1.compareTo(userId2) < 0) {
            return userId1 + "_" + userId2;
        } else {
            return userId2 + "_" + userId1;
        }
    }
}

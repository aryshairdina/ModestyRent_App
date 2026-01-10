package com.example.modestyrent_app;

import android.app.AlertDialog;
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

    private DatabaseReference bookingsRef, productsRef, usersRef, chatsRef;
    private FirebaseAuth mAuth;

    private final List<Booking> bookingList = new ArrayList<>();
    private final List<Booking> filteredList = new ArrayList<>();

    private final Map<String, String> productImageMap = new HashMap<>();
    private final Map<String, String> renterNameMap = new HashMap<>();
    private final Map<String, String> renterPhoneMap = new HashMap<>();

    private String selectedStatus = "All";
    private String selectedDateFilter = "All";
    private String currentSearchQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_requests);

        mAuth = FirebaseAuth.getInstance();
        if (mAuth.getCurrentUser() == null) {
            startActivity(new Intent(this, activity_signin.class));
            finish();
            return;
        }

        initViews();
        forceFilterCardsClickable();   // ⭐ IMPORTANT FIX
        initFirebase();
        setupRecycler();
        setupListeners();
        loadProductsAndUsers();
    }

    // ----------------------------------------------------
    // INIT
    // ----------------------------------------------------

    private void initViews() {
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

    /**
     * ⭐ CRITICAL FIX:
     * Force MaterialCardView to receive touch events
     */
    private void forceFilterCardsClickable() {
        statusFilterCard.setClickable(true);
        statusFilterCard.setFocusable(true);

        dateFilterCard.setClickable(true);
        dateFilterCard.setFocusable(true);

        // Prevent child TextViews from stealing clicks
        statusFilterText.setClickable(false);
        statusFilterText.setFocusable(false);

        dateFilterText.setClickable(false);
        dateFilterText.setFocusable(false);
    }

    private void initFirebase() {
        bookingsRef = FirebaseDatabase.getInstance().getReference("bookings");
        productsRef = FirebaseDatabase.getInstance().getReference("products");
        usersRef = FirebaseDatabase.getInstance().getReference("users");
        chatsRef = FirebaseDatabase.getInstance().getReference("chats");
    }

    private void setupRecycler() {
        bookingAdapter = new BookingAdapter(
                filteredList,
                productImageMap,
                renterNameMap,
                renterPhoneMap,
                new BookingAdapter.BookingActionListener() {
                    @Override public void onPrepareDelivery(Booking b) { updateStatus(b, "PreparingDelivery"); }
                    @Override public void onPreparePickup(Booking b) { updateStatus(b, "PreparingPickup"); }
                    @Override public void onContactRenter(Booking b) { contactRenter(b); }
                    @Override public void onViewBookingDetails(Booking b) { viewBookingDetails(b); }
                });

        bookingsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        bookingsRecyclerView.setAdapter(bookingAdapter);
    }

    private void setupListeners() {

        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString().trim().toLowerCase();
                filterBookings();
            }
        });

        statusFilterCard.setOnClickListener(v -> showStatusDialog());
        dateFilterCard.setOnClickListener(v -> showDateDialog());
    }

    // ----------------------------------------------------
    // LOAD DATA
    // ----------------------------------------------------

    private void loadProductsAndUsers() {
        loadingProgress.setVisibility(View.VISIBLE);

        productsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                productImageMap.clear();
                for (DataSnapshot s : snapshot.getChildren()) {
                    String pid = s.getKey();
                    String img = s.child("imageUrls").child("0").getValue(String.class);
                    if (pid != null && img != null) productImageMap.put(pid, img);
                }
                loadUsers();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
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

                for (DataSnapshot s : snapshot.getChildren()) {
                    String uid = s.getKey();
                    if (uid == null) continue;

                    String name = s.child("fullName").getValue(String.class);
                    String phone = s.child("phone").getValue(String.class);

                    if (name != null) renterNameMap.put(uid, name);
                    if (phone != null) renterPhoneMap.put(uid, phone);
                }
                loadBookings();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                loadBookings();
            }
        });
    }

    private void loadBookings() {
        String ownerId = mAuth.getCurrentUser().getUid();

        bookingsRef.orderByChild("ownerId").equalTo(ownerId)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        bookingList.clear();
                        for (DataSnapshot s : snapshot.getChildren()) {
                            Booking b = s.getValue(Booking.class);
                            if (b != null) {
                                b.setBookingId(s.getKey());
                                bookingList.add(b);
                            }
                        }
                        filterBookings();
                        loadingProgress.setVisibility(View.GONE);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        loadingProgress.setVisibility(View.GONE);
                    }
                });
    }

    // ----------------------------------------------------
    // FILTER LOGIC
    // ----------------------------------------------------

    private void filterBookings() {
        filteredList.clear();

        for (Booking b : bookingList) {

            boolean matchSearch =
                    currentSearchQuery.isEmpty()
                            || contains(b.getProductName())
                            || contains(b.getRenterName())
                            || contains(b.getBookingNumber());

            boolean matchStatus =
                    selectedStatus.equals("All")
                            || normalize(b.getStatus()).equals(normalize(selectedStatus));

            boolean matchDate =
                    selectedDateFilter.equals("All")
                            || matchesDate(b.getStartDate(), selectedDateFilter);

            if (matchSearch && matchStatus && matchDate) {
                filteredList.add(b);
            }
        }

        bookingAdapter.notifyDataSetChanged();
        updateEmpty();
    }

    private boolean contains(String s) {
        return s != null && s.toLowerCase().contains(currentSearchQuery);
    }

    private String normalize(String s) {
        return s == null ? "" : s.replace("_", "").toLowerCase();
    }

    private boolean matchesDate(long millis, String filter) {
        Calendar todayStart = Calendar.getInstance();
        todayStart.set(Calendar.HOUR_OF_DAY, 0);
        todayStart.set(Calendar.MINUTE, 0);
        todayStart.set(Calendar.SECOND, 0);
        todayStart.set(Calendar.MILLISECOND, 0);

        Calendar todayEnd = (Calendar) todayStart.clone();
        todayEnd.add(Calendar.DAY_OF_MONTH, 1);

        Calendar date = Calendar.getInstance();
        date.setTimeInMillis(millis);

        switch (filter) {
            case "Today":
                return date.after(todayStart) && date.before(todayEnd);

            case "This Week":
                Calendar weekStart = (Calendar) todayStart.clone();
                weekStart.set(Calendar.DAY_OF_WEEK, weekStart.getFirstDayOfWeek());
                Calendar weekEnd = (Calendar) weekStart.clone();
                weekEnd.add(Calendar.DAY_OF_WEEK, 7);
                return date.after(weekStart) && date.before(weekEnd);

            case "This Month":
                return date.get(Calendar.YEAR) == todayStart.get(Calendar.YEAR)
                        && date.get(Calendar.MONTH) == todayStart.get(Calendar.MONTH);

            case "Upcoming":
                return date.after(todayEnd);

            case "Past":
                return date.before(todayStart);
        }
        return true;
    }

    private void updateEmpty() {
        boolean show = filteredList.isEmpty();
        emptyState.setVisibility(show ? View.VISIBLE : View.GONE);
        bookingsRecyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    // ----------------------------------------------------
    // DIALOGS
    // ----------------------------------------------------

    private void showStatusDialog() {
        String[] items = {"All", "Confirmed", "PreparingDelivery", "PreparingPickup",
                "OutForDelivery", "ReadyForPickup", "OnRent", "Completed", "Dispute"};

        new AlertDialog.Builder(this)
                .setTitle("Filter by Status")
                .setItems(items, (d, i) -> {
                    selectedStatus = items[i];
                    statusFilterText.setText(selectedStatus);
                    filterBookings();
                })
                .show();
    }

    private void showDateDialog() {
        String[] items = {"All", "Today", "This Week", "This Month", "Upcoming", "Past"};

        new AlertDialog.Builder(this)
                .setTitle("Filter by Date")
                .setItems(items, (d, i) -> {
                    selectedDateFilter = items[i];
                    dateFilterText.setText(selectedDateFilter);
                    filterBookings();
                })
                .show();
    }

    // ----------------------------------------------------
    // ACTIONS
    // ----------------------------------------------------

    private void updateStatus(Booking b, String status) {
        bookingsRef.child(b.getBookingId()).child("status").setValue(status);
        Toast.makeText(this, "Status updated", Toast.LENGTH_SHORT).show();
    }

    private void contactRenter(Booking booking) {
        String me = mAuth.getCurrentUser().getUid();
        String renter = booking.getRenterId();
        if (renter == null) return;

        String chatId = generateChatId(me, renter);
        Intent i = new Intent(this, activity_chat_owner.class);
        i.putExtra("chatId", chatId);
        i.putExtra("productId", booking.getProductId());
        i.putExtra("ownerId", renter);
        startActivity(i);
    }

    private void viewBookingDetails(Booking booking) {
        Intent i = new Intent(this, activity_rentals_details_owner.class);
        i.putExtra("bookingId", booking.getBookingId());
        startActivity(i);
    }

    private String generateChatId(String a, String b) {
        return a.compareTo(b) < 0 ? a + "_" + b : b + "_" + a;
    }
}

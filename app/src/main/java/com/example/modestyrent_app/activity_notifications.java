package com.example.modestyrent_app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class activity_notifications extends AppCompatActivity implements NotificationAdapter.OnNotificationClickListener {

    private ImageView backButton;
    private TextView tvClearAll;
    private RecyclerView rvNotifications;
    private LinearLayout emptyState;

    private NotificationAdapter adapter;
    private List<NotificationModel> notificationList;

    private DatabaseReference notificationsRef;
    private FirebaseAuth auth;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        initializeViews();
        setupFirebase();
        setupRecyclerView();
        loadNotifications();
    }

    private void initializeViews() {
        backButton = findViewById(R.id.backButton);
        tvClearAll = findViewById(R.id.tvClearAll);
        rvNotifications = findViewById(R.id.rvNotifications);
        emptyState = findViewById(R.id.emptyState);

        backButton.setOnClickListener(v -> finish());

        tvClearAll.setOnClickListener(v -> {
            if (notificationList.isEmpty()) {
                Toast.makeText(this, "No notifications to clear", Toast.LENGTH_SHORT).show();
                return;
            }

            // Clear all notifications from Firebase
            new NotificationManager(this).deleteAllNotifications(currentUserId);

            // Clear local list
            notificationList.clear();
            adapter.notifyDataSetChanged();
            showEmptyState();

            Toast.makeText(this, "All notifications cleared", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupFirebase() {
        auth = FirebaseAuth.getInstance();
        currentUserId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;

        if (currentUserId == null) {
            Toast.makeText(this, "Please login to view notifications", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        notificationsRef = FirebaseDatabase.getInstance().getReference("notifications");
    }

    private void setupRecyclerView() {
        notificationList = new ArrayList<>();
        adapter = new NotificationAdapter(this, notificationList, this);

        rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        rvNotifications.setAdapter(adapter);
    }

    private void loadNotifications() {
        if (currentUserId == null) return;

        Query query = notificationsRef.orderByChild("userId").equalTo(currentUserId);

        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                notificationList.clear();

                // CORRECTED LINE: Changed DataNotificationSnapshot to DataSnapshot notificationSnapshot
                for (DataSnapshot notificationSnapshot : snapshot.getChildren()) {
                    NotificationModel notification = notificationSnapshot.getValue(NotificationModel.class);
                    if (notification != null) {
                        notificationList.add(notification);
                    }
                }

                // Sort by timestamp (newest first)
                Collections.sort(notificationList, new Comparator<NotificationModel>() {
                    @Override
                    public int compare(NotificationModel n1, NotificationModel n2) {
                        return Long.compare(n2.getTimestamp(), n1.getTimestamp());
                    }
                });

                adapter.notifyDataSetChanged();

                if (notificationList.isEmpty()) {
                    showEmptyState();
                } else {
                    hideEmptyState();
                }

                // Update badge count in main activity
                updateBadgeCount();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(activity_notifications.this,
                        "Failed to load notifications", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEmptyState() {
        emptyState.setVisibility(View.VISIBLE);
        rvNotifications.setVisibility(View.GONE);
    }

    private void hideEmptyState() {
        emptyState.setVisibility(View.GONE);
        rvNotifications.setVisibility(View.VISIBLE);
    }

    private void updateBadgeCount() {
        long unreadCount = 0;
        for (NotificationModel notification : notificationList) {
            if (!notification.isRead()) {
                unreadCount++;
            }
        }

        // Update badge in MainActivity or wherever needed
        // You can use LocalBroadcast or SharedPreferences to update badge
    }

    @Override
    public void onNotificationClick(NotificationModel notification) {
        // Handle notification click based on type
        handleNotificationAction(notification);
    }

    @Override
    public void onNotificationLongClick(NotificationModel notification) {
        // Show options: Mark as read/Delete
        // You can implement a dialog here
        Toast.makeText(this, "Long press: " + notification.getTitle(), Toast.LENGTH_SHORT).show();
    }

    private void handleNotificationAction(NotificationModel notification) {
        Intent intent = null;

        switch (notification.getType()) {
            case "booking_confirmation":
            case "ready_pickup":
            case "completed_refund":
            case "exceed_date":
                // Open booking details
                if (notification.getUserType().equals("borrower")) {
                    intent = new Intent(this, activity_rentals_details_borrower.class);
                } else {
                    intent = new Intent(this, activity_rentals_details_owner.class);
                }
                intent.putExtra("bookingId", notification.getBookingId());
                intent.putExtra("productId", notification.getProductId());
                intent.putExtra("ownerId", notification.getOtherUserId());
                break;

            case "borrower_return":
                // Open owner booking details
                intent = new Intent(this, activity_rentals_details_owner.class);
                intent.putExtra("bookingId", notification.getBookingId());
                intent.putExtra("productId", notification.getProductId());
                intent.putExtra("ownerId", currentUserId);
                break;

            case "chat":
                // Open chat activity
                intent = new Intent(this, activity_chat_owner.class);
                intent.putExtra("otherUserId", notification.getOtherUserId());
                intent.putExtra("bookingId", notification.getBookingId());
                intent.putExtra("productId", notification.getProductId());
                break;
        }

        if (intent != null) {
            startActivity(intent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Mark all as read when opening notifications
        if (currentUserId != null) {
            new NotificationManager(this).markAllAsRead(currentUserId);
        }
    }
}
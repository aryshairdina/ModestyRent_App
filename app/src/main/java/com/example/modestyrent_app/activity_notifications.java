package com.example.modestyrent_app;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.util.Log;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class activity_notifications extends AppCompatActivity {

    private RecyclerView rvNotifications;
    private TextView tvClearAll;
    private View emptyState;
    private ImageView backButton;

    private NotificationAdapter adapter;
    private List<NotificationModel> notificationList;
    private DatabaseReference notificationsRef;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        initializeViews();
        setupFirebase();
        loadNotifications();
    }

    private void initializeViews() {
        rvNotifications = findViewById(R.id.rvNotifications);
        tvClearAll = findViewById(R.id.tvClearAll);
        emptyState = findViewById(R.id.emptyState);
        backButton = findViewById(R.id.backButton);

        notificationList = new ArrayList<>();
        adapter = new NotificationAdapter(notificationList);
        rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        rvNotifications.setAdapter(adapter);

        backButton.setOnClickListener(v -> finish());

        tvClearAll.setOnClickListener(v -> clearAllNotifications());
    }

    private void setupFirebase() {
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            notificationsRef = FirebaseDatabase.getInstance()
                    .getReference("notifications")
                    .child(currentUserId);
        }
    }

    private void loadNotifications() {
        if (notificationsRef == null || currentUserId == null) {
            showEmptyState();
            return;
        }

        notificationsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                notificationList.clear();

                for (DataSnapshot notificationSnapshot : snapshot.getChildren()) {
                    String key = notificationSnapshot.getKey();

                    // Skip the notification_count field
                    if (key != null && !key.equals("notification_count")) {
                        NotificationModel notification = notificationSnapshot.getValue(NotificationModel.class);
                        if (notification != null) {
                            notification.setId(key);
                            notificationList.add(notification);
                        }
                    }
                }

                // Sort by timestamp (newest first)
                Collections.sort(notificationList, (n1, n2) ->
                        Long.compare(n2.getTimestamp(), n1.getTimestamp()));

                adapter.notifyDataSetChanged();
                updateEmptyState();

                // Also clear the notification count when user views notifications
                clearNotificationCount();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Handle error
                showEmptyState();
            }
        });
    }

    private void updateEmptyState() {
        if (notificationList.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            rvNotifications.setVisibility(View.GONE);
            tvClearAll.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            rvNotifications.setVisibility(View.VISIBLE);
            tvClearAll.setVisibility(View.VISIBLE);
        }
    }

    private void showEmptyState() {
        emptyState.setVisibility(View.VISIBLE);
        rvNotifications.setVisibility(View.GONE);
        tvClearAll.setVisibility(View.GONE);
    }

    private void clearAllNotifications() {
        if (notificationsRef != null) {
            // Remove all notifications except the notification_count field
            notificationsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    for (DataSnapshot childSnapshot : snapshot.getChildren()) {
                        String key = childSnapshot.getKey();
                        // Only delete actual notifications, not the count field
                        if (key != null && !key.equals("notification_count")) {
                            childSnapshot.getRef().removeValue();
                        }
                    }
                    // Also reset the count
                    if (snapshot.child("notification_count").exists()) {
                        snapshot.child("notification_count").getRef().setValue(0);
                    }

                    notificationList.clear();
                    adapter.notifyDataSetChanged();
                    updateEmptyState();
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    // Handle error
                }
            });
        }
    }

    private void clearNotificationCount() {
        if (notificationsRef != null) {
            // Reset notification count to 0 when user views notifications
            notificationsRef.child("notification_count").setValue(0)
                    .addOnSuccessListener(aVoid ->
                            Log.d("Notifications", "Notification count cleared"))
                    .addOnFailureListener(e ->
                            Log.e("Notifications", "Failed to clear count: " + e.getMessage()));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clear notification count when activity is destroyed
        clearNotificationCount();
    }
}
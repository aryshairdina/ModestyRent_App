package com.example.modestyrent_app;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
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
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (currentUserId != null) {
            notificationsRef = FirebaseDatabase.getInstance()
                    .getReference("notifications")
                    .child(currentUserId);
        }
    }

    private void loadNotifications() {
        if (notificationsRef == null) return;

        notificationsRef.orderByChild("timestamp").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                notificationList.clear();

                for (DataSnapshot notificationSnapshot : snapshot.getChildren()) {
                    NotificationModel notification = notificationSnapshot.getValue(NotificationModel.class);
                    if (notification != null) {
                        notification.setId(notificationSnapshot.getKey());
                        notificationList.add(notification);
                    }
                }

                // Sort by timestamp (newest first)
                Collections.sort(notificationList, (n1, n2) ->
                        Long.compare(n2.getTimestamp(), n1.getTimestamp()));

                adapter.notifyDataSetChanged();
                updateEmptyState();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Handle error
            }
        });
    }

    private void updateEmptyState() {
        if (notificationList.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            rvNotifications.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            rvNotifications.setVisibility(View.VISIBLE);
        }
    }

    private void clearAllNotifications() {
        if (notificationsRef != null) {
            notificationsRef.removeValue()
                    .addOnSuccessListener(aVoid -> {
                        notificationList.clear();
                        adapter.notifyDataSetChanged();
                        updateEmptyState();
                    });
        }
    }
}
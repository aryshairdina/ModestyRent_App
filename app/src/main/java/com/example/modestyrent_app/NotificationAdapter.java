package com.example.modestyrent_app;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {

    private List<NotificationModel> notificationList;
    private Context context;
    private String currentUserId;
    private static final String TAG = "NotificationAdapter";

    public NotificationAdapter(List<NotificationModel> notificationList) {
        this.notificationList = notificationList;
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_notification, parent, false);
        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        NotificationModel notification = notificationList.get(position);
        holder.bind(notification);
    }

    @Override
    public int getItemCount() {
        return notificationList.size();
    }

    class NotificationViewHolder extends RecyclerView.ViewHolder {

        private View notificationCard;
        private ImageView ivNotificationIcon;
        private TextView tvNotificationTitle;
        private TextView tvNotificationMessage;
        private TextView tvNotificationTime;
        private View unreadIndicator;

        public NotificationViewHolder(@NonNull View itemView) {
            super(itemView);

            notificationCard = itemView.findViewById(R.id.notificationCard);
            ivNotificationIcon = itemView.findViewById(R.id.ivNotificationIcon);
            tvNotificationTitle = itemView.findViewById(R.id.tvNotificationTitle);
            tvNotificationMessage = itemView.findViewById(R.id.tvNotificationMessage);
            tvNotificationTime = itemView.findViewById(R.id.tvNotificationTime);
            unreadIndicator = itemView.findViewById(R.id.unreadIndicator);

            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            }

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    NotificationModel notification = notificationList.get(position);
                    markAsRead(notification.getId());
                    handleNotificationClick(notification);
                }
            });
        }

        public void bind(NotificationModel notification) {
            tvNotificationTitle.setText(notification.getTitle());
            tvNotificationMessage.setText(notification.getMessage());
            tvNotificationTime.setText(formatTimestamp(notification.getTimestamp()));

            // Set icon
            int iconResId = R.drawable.ic_notifications;
            if (notification.getType() != null) {
                switch (notification.getType()) {
                    case "chat":
                    case "new_message":
                        iconResId = R.drawable.ic_chat;
                        break;
                }
            }
            ivNotificationIcon.setImageResource(iconResId);

            if (notification.isRead()) {
                notificationCard.setBackgroundColor(
                        context.getResources().getColor(R.color.notification_read));
                unreadIndicator.setVisibility(View.GONE);
            } else {
                notificationCard.setBackgroundColor(
                        context.getResources().getColor(R.color.notification_new));
                unreadIndicator.setVisibility(View.VISIBLE);
            }
        }

        private void handleNotificationClick(NotificationModel notification) {
            String type = notification.getType();
            String bookingId = notification.getBookingId();
            String chatId = notification.getChatId();
            String clickAction = notification.getClickAction();

            Log.d(TAG, "Notification clicked - Type: " + type +
                    ", ClickAction: " + clickAction +
                    ", ChatId: " + chatId);

            try {
                // FIRST: Always try to use clickAction if available
                if (clickAction != null && !clickAction.isEmpty()) {
                    Log.d(TAG, "Using clickAction: " + clickAction);
                    openActivityByClickAction(clickAction, bookingId, chatId);
                    return;
                }

                // SECOND: If no clickAction, check type specifically for chat
                if (type != null) {
                    Log.d(TAG, "Using type-based navigation for: " + type);
                    if (type.equals("chat") || type.equals("new_message")) {
                        // FIXED: Always open chat list for chat notifications
                        openChatListActivity();
                        return;
                    }

                    // Handle other types
                    switch (type) {
                        case "booking":
                            openActivity(activity_booking_requests.class);
                            break;
                        case "booking_confirmed":
                        case "payment_success":
                            openActivity(activity_myrentals.class);
                            break;
                        case "status":
                        case "status_update":
                        case "completed":
                        case "refund":
                        case "penalty_alert":
                        case "return_reminder":
                            if (bookingId != null) {
                                checkUserRoleAndOpenRentalDetails(bookingId);
                            } else {
                                openMainActivity();
                            }
                            break;
                        case "inspection":
                            openActivityWithExtra(activity_inspection.class, "bookingId", bookingId);
                            break;
                        case "review_request":
                        case "review":
                            openActivity(activity_myrentals.class);
                            break;
                        default:
                            openMainActivity();
                            break;
                    }
                } else {
                    Log.d(TAG, "Type is null, opening main activity");
                    openMainActivity();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error handling notification click: " + e.getMessage());
                e.printStackTrace();
                openMainActivity();
            }
        }

        private void openActivityByClickAction(String clickAction, String bookingId, String chatId) {
            try {
                Intent intent = null;
                Log.d(TAG, "Opening activity by click action: " + clickAction);

                switch (clickAction) {
                    case "activity_myrentals":
                        intent = new Intent(context, activity_myrentals.class);
                        break;
                    case "activity_booking_requests":
                        intent = new Intent(context, activity_booking_requests.class);
                        break;
                    case "activity_rentals_details_owner":
                        intent = new Intent(context, activity_rentals_details_owner.class);
                        intent.putExtra("bookingId", bookingId);
                        break;
                    case "activity_chat_list":
                        // FIXED: Directly open chat list
                        intent = new Intent(context, activity_chat_list.class);
                        Log.d(TAG, "Opening chat list activity");
                        break;
                    case "activity_chat_owner":
                        intent = new Intent(context, activity_chat_owner.class);
                        if (chatId != null) {
                            intent.putExtra("chatId", chatId);
                        }
                        break;
                    case "activity_inspection":
                        intent = new Intent(context, activity_inspection.class);
                        intent.putExtra("bookingId", bookingId);
                        break;
                    default:
                        Log.w(TAG, "Unknown click action: " + clickAction + ", opening main activity");
                        intent = new Intent(context, MainActivity.class);
                        break;
                }

                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    context.startActivity(intent);
                    Log.d(TAG, "Activity started: " + clickAction);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error opening activity by click action: " + clickAction, e);
                e.printStackTrace();
                openMainActivity();
            }
        }

        // FIXED: Direct chat list opening method
        private void openChatListActivity() {
            try {
                Log.d(TAG, "Attempting to open chat list activity");
                Intent intent = new Intent(context, activity_chat_list.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                context.startActivity(intent);
                Log.d(TAG, "Chat list activity opened successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error opening chat list activity: " + e.getMessage());
                e.printStackTrace();

                // Try chat owner as fallback
                try {
                    Log.d(TAG, "Trying chat owner as fallback");
                    Intent intent = new Intent(context, activity_chat_owner.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    context.startActivity(intent);
                } catch (Exception ex) {
                    Log.e(TAG, "Error opening chat owner activity: " + ex.getMessage());
                    openMainActivity();
                }
            }
        }

        private void openActivity(Class<?> activityClass) {
            try {
                Intent intent = new Intent(context, activityClass);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "Error opening activity: " + e.getMessage());
                openMainActivity();
            }
        }

        private void openActivityWithExtra(Class<?> activityClass, String extraKey, String extraValue) {
            try {
                Intent intent = new Intent(context, activityClass);
                if (extraValue != null) {
                    intent.putExtra(extraKey, extraValue);
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "Error opening activity with extra: " + e.getMessage());
                openMainActivity();
            }
        }

        private void checkUserRoleAndOpenRentalDetails(String bookingId) {
            if (currentUserId == null || bookingId == null) {
                openMainActivity();
                return;
            }

            DatabaseReference bookingRef = FirebaseDatabase.getInstance()
                    .getReference("bookings")
                    .child(bookingId);

            bookingRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Intent intent = null;
                    if (snapshot.exists()) {
                        String ownerId = snapshot.child("ownerId").getValue(String.class);
                        String renterId = snapshot.child("renterId").getValue(String.class);

                        if (ownerId != null && ownerId.equals(currentUserId)) {
                            intent = new Intent(context, activity_rentals_details_owner.class);
                        } else if (renterId != null && renterId.equals(currentUserId)) {
                            intent = new Intent(context, activity_myrentals.class);
                        }
                    }

                    if (intent != null) {
                        intent.putExtra("bookingId", bookingId);
                        context.startActivity(intent);
                    } else {
                        openMainActivity();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    openMainActivity();
                }
            });
        }

        private void openMainActivity() {
            try {
                Log.d(TAG, "Opening main activity as fallback");
                Intent intent = new Intent(context, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                context.startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "Error opening main activity: " + e.getMessage());
            }
        }

        private void markAsRead(String notificationId) {
            String userId = currentUserId;
            if (userId == null || notificationId == null) return;

            DatabaseReference notificationRef = FirebaseDatabase.getInstance()
                    .getReference("notifications")
                    .child(userId)
                    .child(notificationId)
                    .child("read");

            notificationRef.setValue(true)
                    .addOnSuccessListener(aVoid -> {
                        unreadIndicator.setVisibility(View.GONE);
                        notificationCard.setBackgroundColor(
                                context.getResources().getColor(R.color.notification_read));
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to mark notification as read: " + e.getMessage());
                    });
        }

        private String formatTimestamp(long timestamp) {
            long now = System.currentTimeMillis();
            long diff = now - timestamp;

            long seconds = diff / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            long days = hours / 24;

            if (days > 7) {
                SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
                return sdf.format(new Date(timestamp));
            } else if (days > 0) {
                return days + "d ago";
            } else if (hours > 0) {
                return hours + "h ago";
            } else if (minutes > 0) {
                return minutes + "m ago";
            } else {
                return "Just now";
            }
        }
    }
}
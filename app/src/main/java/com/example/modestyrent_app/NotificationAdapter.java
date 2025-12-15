package com.example.modestyrent_app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {

    private List<NotificationModel> notificationList;
    private Context context;

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
        private MaterialButton btnAction;
        private View unreadIndicator;

        public NotificationViewHolder(@NonNull View itemView) {
            super(itemView);

            notificationCard = itemView.findViewById(R.id.notificationCard);
            ivNotificationIcon = itemView.findViewById(R.id.ivNotificationIcon);
            tvNotificationTitle = itemView.findViewById(R.id.tvNotificationTitle);
            tvNotificationMessage = itemView.findViewById(R.id.tvNotificationMessage);
            tvNotificationTime = itemView.findViewById(R.id.tvNotificationTime);
            btnAction = itemView.findViewById(R.id.btnAction);
            unreadIndicator = itemView.findViewById(R.id.unreadIndicator);
        }

        public void bind(NotificationModel notification) {
            // Set notification data
            tvNotificationTitle.setText(notification.getTitle());
            tvNotificationMessage.setText(notification.getMessage());
            tvNotificationTime.setText(getTimeAgo(notification.getTimestamp()));

            // Set icon based on notification type
            setNotificationIcon(notification.getType());

            // Set background based on read status
            if (notification.isRead()) {
                notificationCard.setBackgroundColor(
                        context.getResources().getColor(R.color.notification_read));
                unreadIndicator.setVisibility(View.GONE);
            } else {
                notificationCard.setBackgroundColor(
                        context.getResources().getColor(R.color.notification_new));
                unreadIndicator.setVisibility(View.VISIBLE);
            }

            // Set up action button
            setupActionButton(notification);

            // Click listener
            itemView.setOnClickListener(v -> {
                markAsRead(notification.getId());
                handleNotificationClick(notification);
            });
        }

        private void setNotificationIcon(String type) {
            int iconResId = android.R.drawable.ic_dialog_info;
            int iconColor = R.color.neutral;

            if (type != null) {
                switch (type) {
                    case "booking_confirmed":
                        iconResId = android.R.drawable.ic_lock_lock;
                        iconColor = R.color.notification_success;
                        break;
                    case "payment_received":
                        iconResId = android.R.drawable.ic_menu_save;
                        iconColor = R.color.notification_success;
                        break;
                    case "delivery_update":
                        iconResId = android.R.drawable.ic_menu_directions;
                        iconColor = R.color.primary;
                        break;
                    case "return_reminder":
                        iconResId = android.R.drawable.ic_menu_recent_history;
                        iconColor = R.color.notification_warning;
                        break;
                    case "review_request":
                        iconResId = android.R.drawable.star_big_on;
                        iconColor = R.color.notification_warning;
                        break;
                    case "new_message":
                        iconResId = android.R.drawable.ic_dialog_email;
                        iconColor = R.color.primary;
                        break;
                    case "special_offer":
                        iconResId = android.R.drawable.ic_menu_share;
                        iconColor = R.color.notification_sale;
                        break;
                    case "penalty_alert":
                        iconResId = android.R.drawable.ic_delete;
                        iconColor = R.color.error;
                        break;
                }
            }

            ivNotificationIcon.setImageResource(iconResId);
            ivNotificationIcon.setColorFilter(
                    context.getResources().getColor(iconColor));
        }

        private void setupActionButton(NotificationModel notification) {
            if (notification.getType() != null) {
                switch (notification.getType()) {
                    case "booking_confirmed":
                    case "delivery_update":
                        btnAction.setText("View Booking");
                        btnAction.setVisibility(View.VISIBLE);
                        break;
                    case "new_message":
                        btnAction.setText("Reply");
                        btnAction.setVisibility(View.VISIBLE);
                        break;
                    case "review_request":
                        btnAction.setText("Leave Review");
                        btnAction.setVisibility(View.VISIBLE);
                        break;
                    default:
                        btnAction.setVisibility(View.GONE);
                        return;
                }

                btnAction.setOnClickListener(v -> {
                    markAsRead(notification.getId());
                    handleNotificationClick(notification);
                });
            }
        }

        private void handleNotificationClick(NotificationModel notification) {
            if (notification.getType() == null) return;

            Intent intent = null;

            switch (notification.getType()) {
                case "booking_confirmed":
                case "delivery_update":
                    intent = new Intent(context, activity_rentals_details_borrower.class);
                    intent.putExtra("bookingId", notification.getBookingId());
                    break;
                case "new_message":
                    intent = new Intent(context, activity_chat_owner.class);
                    intent.putExtra("chatId", notification.getChatId());
                    break;
                case "review_request":
                    intent = new Intent(context, activity_leave_review.class);
                    intent.putExtra("bookingId", notification.getBookingId());
                    break;
            }

            if (intent != null) {
                context.startActivity(intent);
            }
        }

        private void markAsRead(String notificationId) {
            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            if (userId == null) return;

            DatabaseReference notificationsRef = FirebaseDatabase.getInstance()
                    .getReference("notifications")
                    .child(userId)
                    .child(notificationId)
                    .child("read");

            notificationsRef.setValue(true);
        }

        private String getTimeAgo(long timestamp) {
            long now = System.currentTimeMillis();
            long diff = now - timestamp;

            long seconds = diff / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            long days = hours / 24;

            if (days > 0) {
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
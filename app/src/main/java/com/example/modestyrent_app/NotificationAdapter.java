package com.example.modestyrent_app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {

    private Context context;
    private List<NotificationModel> notificationList;
    private OnNotificationClickListener listener;
    private DatabaseReference notificationsRef;

    public interface OnNotificationClickListener {
        void onNotificationClick(NotificationModel notification);
        void onNotificationLongClick(NotificationModel notification);
    }

    public NotificationAdapter(Context context, List<NotificationModel> notificationList,
                               OnNotificationClickListener listener) {
        this.context = context;
        this.notificationList = notificationList;
        this.listener = listener;
        this.notificationsRef = FirebaseDatabase.getInstance().getReference("notifications");
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_notification, parent, false);
        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        NotificationModel notification = notificationList.get(position);

        // Set notification title and message
        holder.tvNotificationTitle.setText(notification.getTitle());
        holder.tvNotificationMessage.setText(notification.getMessage());

        // Set time
        String timeAgo = getTimeAgo(notification.getTimestamp());
        holder.tvNotificationTime.setText(timeAgo);

        // Set icon based on notification type
        setNotificationIcon(holder.ivNotificationIcon, notification.getType());

        // Show/hide unread indicator
        holder.unreadIndicator.setVisibility(notification.isRead() ? View.GONE : View.VISIBLE);

        // Card click listener
        holder.notificationCard.setOnClickListener(v -> {
            // Mark as read
            if (!notification.isRead()) {
                markAsRead(notification.getNotificationId());
                notification.setRead(true);
                holder.unreadIndicator.setVisibility(View.GONE);
            }
            if (listener != null) {
                listener.onNotificationClick(notification);
            }
        });

        // Long click listener
        holder.notificationCard.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onNotificationLongClick(notification);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return notificationList.size();
    }

    public void updateNotifications(List<NotificationModel> newList) {
        notificationList.clear();
        notificationList.addAll(newList);
        notifyDataSetChanged();
    }

    public void clearAll() {
        notificationList.clear();
        notifyDataSetChanged();
    }

    private void markAsRead(String notificationId) {
        notificationsRef.child(notificationId).child("read").setValue(true);
    }

    private void setNotificationIcon(ImageView imageView, String type) {
        int iconResId;
        switch (type) {
            case "booking_confirmation":
                iconResId = R.drawable.ic_notifications;
                break;
            case "ready_pickup":
                iconResId = R.drawable.ic_notifications;
                break;
            case "completed_refund":
                iconResId = R.drawable.ic_notifications;
                break;
            case "exceed_date":
                iconResId = R.drawable.ic_warning;
                break;
            case "chat":
                iconResId = R.drawable.ic_chat;
                break;
            case "borrower_return":
                iconResId = R.drawable.ic_notifications;
                break;
            default:
                iconResId = R.drawable.ic_notifications;
                break;
        }
        imageView.setImageResource(iconResId);
    }

    private String getTimeAgo(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (seconds < 60) {
            return "Just now";
        } else if (minutes < 60) {
            return minutes + "m ago";
        } else if (hours < 24) {
            return hours + "h ago";
        } else if (days < 7) {
            return days + "d ago";
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }
    }

    static class NotificationViewHolder extends RecyclerView.ViewHolder {
        CardView notificationCard;
        ImageView ivNotificationIcon;
        TextView tvNotificationTitle;
        TextView tvNotificationMessage;
        TextView tvNotificationTime;
        View unreadIndicator;

        public NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            notificationCard = itemView.findViewById(R.id.notificationCard);
            ivNotificationIcon = itemView.findViewById(R.id.ivNotificationIcon);
            tvNotificationTitle = itemView.findViewById(R.id.tvNotificationTitle);
            tvNotificationMessage = itemView.findViewById(R.id.tvNotificationMessage);
            tvNotificationTime = itemView.findViewById(R.id.tvNotificationTime);
            unreadIndicator = itemView.findViewById(R.id.unreadIndicator);
        }
    }
}
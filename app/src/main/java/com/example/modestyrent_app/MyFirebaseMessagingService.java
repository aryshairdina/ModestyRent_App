package com.example.modestyrent_app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "MyFirebaseMsgService";
    private static final String CHANNEL_ID = "modestyrent_channel";
    private static final String CHANNEL_NAME = "ModestyRent Notifications";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, "From: " + remoteMessage.getFrom());

        if (remoteMessage.getData().size() > 0) {
            Log.d(TAG, "Message data payload: " + remoteMessage.getData());
            handleDataMessage(remoteMessage.getData());
        }

        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Message Notification Body: " + remoteMessage.getNotification().getBody());
            sendNotification(
                    remoteMessage.getNotification().getTitle(),
                    remoteMessage.getNotification().getBody(),
                    remoteMessage.getData()
            );
        }
    }

    private void handleDataMessage(Map<String, String> data) {
        try {
            String type = data.get("type");
            String bookingId = data.get("bookingId");
            String chatId = data.get("chatId");
            String userId = data.get("userId");
            String title = data.get("title");
            String message = data.get("message");

            if (userId != null && title != null && message != null) {
                NotificationModel notification = new NotificationModel(title, message, type, userId);
                notification.setBookingId(bookingId);
                notification.setChatId(chatId);
                notification.setFCM(true);
                notification.setTimestamp(System.currentTimeMillis());

                String clickAction = getClickActionForType(type);
                notification.setClickAction(clickAction);

                saveInAppNotification(notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling data message: " + e.getMessage());
        }
    }

    private String getClickActionForType(String type) {
        if (type == null) return "MainActivity";

        switch (type) {
            case "booking":
                return "activity_booking_requests";
            case "booking_confirmed":
            case "payment_success":
                return "activity_myrentals";
            case "status":
            case "status_update":
                return "activity_rentals_details_owner";
            case "chat":
            case "new_message":
                return "activity_chat_list";
            case "review_request":
            case "review":
                return "activity_myrentals"; // CHANGED: from activity_checkout
            case "inspection":
                return "activity_inspection";
            case "refund":
            case "completed":
            case "penalty_alert":
            case "return_reminder":
                return "activity_myrentals";
            default:
                return "MainActivity";
        }
    }

    private void saveInAppNotification(NotificationModel notification) {
        String userId = notification.getUserId();
        if (userId == null) {
            Log.e(TAG, "User ID is null, cannot save notification");
            return;
        }

        DatabaseReference notificationsRef = FirebaseDatabase.getInstance()
                .getReference("notifications")
                .child(userId);

        String notificationId = notificationsRef.push().getKey();

        if (notificationId != null) {
            notification.setId(notificationId);
            notificationsRef.child(notificationId).setValue(notification)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "In-app notification saved for user: " + userId);
                        updateNotificationCount(userId);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to save notification: " + e.getMessage());
                    });
        }
    }

    private void updateNotificationCount(String userId) {
        if (userId == null) return;

        DatabaseReference countRef = FirebaseDatabase.getInstance()
                .getReference("notifications")
                .child(userId)
                .child("notification_count");

        countRef.get().addOnSuccessListener(dataSnapshot -> {
            int currentCount = 0;
            if (dataSnapshot.exists() && dataSnapshot.getValue() != null) {
                Integer countValue = dataSnapshot.getValue(Integer.class);
                currentCount = countValue != null ? countValue : 0;
            }
            int newCount = currentCount + 1;
            countRef.setValue(newCount)
                    .addOnSuccessListener(aVoid ->
                            Log.d(TAG, "Notification count updated: " + newCount))
                    .addOnFailureListener(e ->
                            Log.e(TAG, "Failed to update count: " + e.getMessage()));
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to get current count: " + e.getMessage());
            countRef.setValue(1);
        });
    }

    private void sendNotification(String title, String message, Map<String, String> data) {
        Intent intent = null;
        String type = data != null ? data.get("type") : null;
        String bookingId = data != null ? data.get("bookingId") : null;
        String chatId = data != null ? data.get("chatId") : null;

        if (type != null) {
            switch (type) {
                case "booking":
                    intent = new Intent(this, activity_booking_requests.class);
                    break;
                case "booking_confirmed":
                case "payment_success":
                    intent = new Intent(this, activity_myrentals.class);
                    if (bookingId != null) intent.putExtra("bookingId", bookingId);
                    break;
                case "status":
                case "status_update":
                case "completed":
                case "refund":
                case "penalty_alert":
                case "return_reminder":
                    intent = new Intent(this, activity_myrentals.class);
                    if (bookingId != null) intent.putExtra("bookingId", bookingId);
                    break;
                case "chat":
                case "new_message":
                    intent = new Intent(this, activity_chat_list.class);
                    break;
                case "review_request":
                case "review":
                    // CHANGED: Goes to my rentals instead of checkout
                    intent = new Intent(this, activity_myrentals.class);
                    if (bookingId != null) intent.putExtra("bookingId", bookingId);
                    break;
                case "inspection":
                    intent = new Intent(this, activity_inspection.class);
                    if (bookingId != null) intent.putExtra("bookingId", bookingId);
                    break;
                default:
                    intent = new Intent(this, MainActivity.class);
                    break;
            }
        } else {
            intent = new Intent(this, MainActivity.class);
        }

        if (intent == null) {
            intent = new Intent(this, MainActivity.class);
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{100, 200, 300, 400, 500});
            notificationManager.createNotificationChannel(channel);
        }

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notifications)
                        .setContentTitle(title != null ? title : "ModestyRent")
                        .setContentText(message)
                        .setAutoCancel(true)
                        .setSound(defaultSoundUri)
                        .setContentIntent(pendingIntent)
                        .setPriority(NotificationCompat.PRIORITY_HIGH);

        int notificationId = (int) System.currentTimeMillis();
        notificationManager.notify(notificationId, notificationBuilder.build());
        Log.d(TAG, "Local notification sent: " + title);
    }

    @Override
    public void onNewToken(String token) {
        Log.d(TAG, "Refreshed FCM token: " + token);
        // Save token to Firebase if needed
    }
}
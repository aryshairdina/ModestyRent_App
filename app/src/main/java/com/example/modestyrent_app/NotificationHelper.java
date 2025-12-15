package com.example.modestyrent_app;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.firebase.database.*;

public class NotificationHelper {

    private static final String TAG = "NotificationHelper";

    // Send notification to both borrower and owner
    public static void sendBookingNotification(String bookingId, String title, String message,
                                               String type, String borrowerId, String ownerId) {

        // Send to borrower
        sendNotification(borrowerId, title, message, type, bookingId);

        // Send to owner
        String ownerTitle = "Booking Update: " + title;
        String ownerMessage = "Booking #" + (bookingId != null && bookingId.length() > 8 ?
                bookingId.substring(0, 8) : bookingId) + " - " + message;
        sendNotification(ownerId, ownerTitle, ownerMessage, type, bookingId);
    }

    // Send single notification
    public static void sendNotification(String userId, String title, String message,
                                        String type, String bookingId) {

        if (userId == null) {
            Log.e(TAG, "User ID is null, cannot send notification");
            return;
        }

        DatabaseReference notificationsRef = FirebaseDatabase.getInstance()
                .getReference("notifications")
                .child(userId);

        String notificationId = notificationsRef.push().getKey();

        NotificationModel notification = new NotificationModel(title, message, type);
        notification.setBookingId(bookingId);

        if (notificationId != null) {
            notificationsRef.child(notificationId).setValue(notification)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Notification sent to user: " + userId);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to send notification: " + e.getMessage());
                    });
        }
    }

    // Send status change notification
    public static void sendStatusNotification(String bookingId, String oldStatus, String newStatus,
                                              String userId, String userType) {

        String title = "Status Updated";
        String message = "Status changed from " + oldStatus + " to " + newStatus;
        String type = "status_update";

        sendNotification(userId, title, message, type, bookingId);
    }

    // Send late return notification
    public static void sendLateReturnNotification(String bookingId, int daysLate, double penalty,
                                                  String userId) {

        String title = "Late Return Alert";
        String message = "Return is " + daysLate + " days late. Penalty: RM " + penalty;
        String type = "penalty_alert";

        sendNotification(userId, title, message, type, bookingId);
    }

    // Send review reminder notification
    public static void sendReviewReminder(String bookingId, String userId) {

        String title = "Leave a Review";
        String message = "How was your rental experience? Share your feedback!";
        String type = "review_request";

        sendNotification(userId, title, message, type, bookingId);
    }
}
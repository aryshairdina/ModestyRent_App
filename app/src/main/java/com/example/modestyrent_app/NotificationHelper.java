package com.example.modestyrent_app;

import android.util.Log;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.auth.FirebaseAuth;
import java.util.HashMap;
import java.util.Map;

public class NotificationHelper {
    private static final String TAG = "NotificationHelper";

    // 1. NEW BOOKING FOR OWNER
    public static void sendNewBookingNotification(String bookingId, String productName,
                                                  String borrowerName, String ownerId,
                                                  String borrowerId) {
        String title = "New Booking Request";
        String message = borrowerName + " wants to book: " + productName;

        NotificationModel notification = new NotificationModel(title, message, "booking", ownerId);
        notification.setBookingId(bookingId);
        notification.setTargetUserId(borrowerId);
        notification.setClickAction("activity_booking_requests");

        saveInAppNotification(notification);
        Log.d(TAG, "New booking notification sent to owner: " + ownerId);
    }

    // 2. BOOKING STATUS UPDATES
    public static void sendStatusNotification(String bookingId, String status,
                                              String userId, String userType,
                                              String productName) {
        String title = "Booking Status Updated";
        String message = productName + " - Status: " + status;

        String targetActivity = userType.equals("owner") ?
                "activity_rentals_details_owner" :
                "activity_myrentals";

        NotificationModel notification = new NotificationModel(title, message, "status", userId);
        notification.setBookingId(bookingId);
        notification.setClickAction(targetActivity);

        saveInAppNotification(notification);
        Log.d(TAG, "Status notification sent to " + userType + ": " + userId);
    }

    // 3. GENERAL NOTIFICATION METHOD
    public static void sendNotification(String userId, String title, String message,
                                        String type, String bookingId) {
        if (userId == null) {
            Log.e(TAG, "User ID is null, cannot send notification");
            return;
        }

        String clickAction = getClickActionForType(type);
        NotificationModel notification = new NotificationModel(title, message, type, userId);
        notification.setBookingId(bookingId);
        notification.setClickAction(clickAction);

        saveInAppNotification(notification);
        Log.d(TAG, "General notification sent to: " + userId);
    }

    // 4. CHAT NOTIFICATION METHOD
    public static void sendChatNotification(String senderId, String receiverId,
                                            String title, String message,
                                            String chatId, String bookingId) {
        Log.d(TAG, "sendChatNotification called - sender: " + senderId + ", receiver: " + receiverId);

        NotificationModel notification = new NotificationModel(title, message, "new_message", receiverId);
        notification.setChatId(chatId);
        notification.setBookingId(bookingId);
        notification.setTargetUserId(senderId);
        notification.setClickAction("activity_chat_list");

        saveInAppNotification(notification);
        Log.d(TAG, "Chat notification sent to: " + receiverId);
    }

    // 5. SIMPLE CHAT NOTIFICATION
    public static void sendSimpleChatNotification(String chatId, String senderName,
                                                  String messageText, String receiverId) {
        String title = "New Message from " + senderName;
        String message = messageText.length() > 50 ?
                messageText.substring(0, 50) + "..." : messageText;

        NotificationModel notification = new NotificationModel(title, message, "new_message", receiverId);
        notification.setChatId(chatId);
        notification.setClickAction("activity_chat_list");

        saveInAppNotification(notification);
        Log.d(TAG, "Simple chat notification sent to: " + receiverId);
    }

    // 6. OTHER NOTIFICATION METHODS
    public static void sendInspectionNotification(String bookingId, String inspectorName,
                                                  String userId, String userType) {
        String title = "Inspection Scheduled";
        String message = inspectorName + " will inspect the item soon";

        NotificationModel notification = new NotificationModel(title, message, "inspection", userId);
        notification.setBookingId(bookingId);
        notification.setClickAction("activity_inspection");

        saveInAppNotification(notification);
        Log.d(TAG, "Inspection notification sent to " + userType + ": " + userId);
    }

    public static void sendRefundNotification(String bookingId, double amount,
                                              String userId, String userType) {
        String title = "Refund Processed";
        String message = "RM " + String.format("%.2f", amount) + " has been refunded";

        String targetActivity = userType.equals("owner") ?
                "activity_rentals_details_owner" :
                "activity_myrentals";

        NotificationModel notification = new NotificationModel(title, message, "refund", userId);
        notification.setBookingId(bookingId);
        notification.setClickAction(targetActivity);

        saveInAppNotification(notification);
        Log.d(TAG, "Refund notification sent to " + userType + ": " + userId);
    }

    public static void sendCompletionNotification(String bookingId, String productName,
                                                  String userId, String userType) {
        String title = "Rental Completed";
        String message = "Your rental for " + productName + " has been completed";

        String targetActivity = userType.equals("owner") ?
                "activity_rentals_details_owner" :
                "activity_myrentals";

        NotificationModel notification = new NotificationModel(title, message, "completed", userId);
        notification.setBookingId(bookingId);
        notification.setClickAction(targetActivity);

        saveInAppNotification(notification);
        Log.d(TAG, "Completion notification sent to " + userType + ": " + userId);
    }

    // 7. REVIEW REQUEST NOTIFICATION - FIXED: Changed from activity_checkout to activity_myrentals
    public static void sendReviewRequest(String bookingId, String productName, String userId) {
        String title = "Leave a Review";
        String message = "How was your experience with " + productName + "?";

        NotificationModel notification = new NotificationModel(title, message, "review_request", userId);
        notification.setBookingId(bookingId);
        notification.setClickAction("activity_myrentals"); // CHANGED: from activity_checkout

        saveInAppNotification(notification);
        Log.d(TAG, "Review request sent to: " + userId);
    }

    // 8. BOOKING NOTIFICATION - FIXED: Simplified version
    public static void sendBookingNotification(String bookingId, String title, String message,
                                               String type, String borrowerId, String ownerId) {
        // Send to borrower - Booking confirmed goes to my rentals
        if (title != null && title.contains("Booking Confirmed")) {
            sendNotification(borrowerId, title, message, "booking_confirmed", bookingId);
        } else {
            sendNotification(borrowerId, title, message, type, bookingId);
        }

        // Send to owner - New booking goes to booking requests
        String ownerTitle = "New Booking: " + title;
        String ownerMessage = "Booking #" + (bookingId != null && bookingId.length() > 8 ?
                bookingId.substring(0, 8) : bookingId) + " - " + message;
        sendNotification(ownerId, ownerTitle, ownerMessage, "booking", bookingId);

        Log.d(TAG, "Booking notification sent to both parties for booking: " + bookingId);
    }

    // 9. PAYMENT SUCCESS NOTIFICATION
    public static void sendPaymentSuccessNotification(String bookingId, String userId,
                                                      double amount, String productName) {
        String title = "Payment Successful";
        String message = "Payment of RM " + String.format("%.2f", amount) +
                " for " + productName + " has been confirmed";

        NotificationModel notification = new NotificationModel(title, message, "payment_success", userId);
        notification.setBookingId(bookingId);
        notification.setClickAction("activity_myrentals");

        saveInAppNotification(notification);
        Log.d(TAG, "Payment success notification sent to: " + userId);
    }

    public static void sendLateReturnNotification(String bookingId, int daysLate, double penalty,
                                                  String userId) {
        // Add null check
        if (userId == null || bookingId == null) {
            Log.e(TAG, "Cannot send late return notification - userId or bookingId is null");
            return;
        }

        String title = "Late Return Alert";
        String message = "Return is " + daysLate + " days late. Penalty: RM " +
                String.format("%.2f", penalty);

        NotificationModel notification = new NotificationModel(title, message, "penalty_alert", userId);
        notification.setBookingId(bookingId);
        notification.setClickAction("activity_myrentals");

        saveInAppNotification(notification);
        Log.d(TAG, "Late return notification sent to: " + userId);
    }

    // 10. SAVE IN-APP NOTIFICATION TO FIREBASE
    private static void saveInAppNotification(NotificationModel notification) {
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

    // 11. UPDATE NOTIFICATION COUNT
    private static void updateNotificationCount(String userId) {
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

    // 12. CLEAR NOTIFICATION COUNT
    public static void clearNotificationCount(String userId) {
        if (userId == null) return;

        DatabaseReference countRef = FirebaseDatabase.getInstance()
                .getReference("notifications")
                .child(userId)
                .child("notification_count");

        countRef.setValue(0)
                .addOnSuccessListener(aVoid ->
                        Log.d(TAG, "Notification count cleared for user: " + userId))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Failed to clear notification count: " + e.getMessage()));
    }

    // 13. GET CLICK ACTION FOR TYPE - FIXED: Removed activity_checkout references
    private static String getClickActionForType(String type) {
        if (type == null) return "MainActivity";

        switch (type) {
            case "booking":
                return "activity_booking_requests";
            case "booking_confirmed":
                return "activity_myrentals";
            case "status":
            case "status_update":
                return "activity_rentals_details_owner";
            case "completed":
            case "refund":
            case "penalty_alert":
            case "return_reminder":
                return "activity_myrentals";
            case "chat":
            case "new_message":
                return "activity_chat_list";
            case "review_request":
            case "review":
                return "activity_myrentals"; // CHANGED: from activity_checkout
            case "inspection":
                return "activity_inspection";
            case "payment_success":
                return "activity_myrentals";
            default:
                return "MainActivity";
        }
    }

    // Interface for notification count callback
    public interface NotificationCountListener {
        void onCountReceived(int count);
        void onError(String error);
    }
}
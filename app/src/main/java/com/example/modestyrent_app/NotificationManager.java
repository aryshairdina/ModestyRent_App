package com.example.modestyrent_app;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NotificationManager {

    private static final String CHANNEL_ID = "modestyrent_notifications";
    private static final int NOTIFICATION_ID = 1001;

    private Context context;
    private DatabaseReference notificationsRef;
    private FirebaseAuth auth;

    public NotificationManager(Context context) {
        this.context = context;
        this.notificationsRef = FirebaseDatabase.getInstance().getReference("notifications");
        this.auth = FirebaseAuth.getInstance();
    }

    // General method to send notification to specific user
    public void sendNotification(String type, String bookingId, String productId,
                                 String receiverId, String receiverType, // receiver info
                                 String otherUserId, String productName,
                                 Map<String, Object> extraData, String title, String message) {

        if (receiverId == null || receiverId.isEmpty()) return;

        String notificationId = UUID.randomUUID().toString();

        // Create notification model
        Map<String, Object> notification = new HashMap<>();
        notification.put("notificationId", notificationId);
        notification.put("userId", receiverId); // Store for RECEIVER, not sender
        notification.put("userType", receiverType); // "borrower" or "owner"
        notification.put("type", type);
        notification.put("bookingId", bookingId);
        notification.put("productId", productId);
        notification.put("otherUserId", otherUserId);
        notification.put("title", title);
        notification.put("message", message);
        notification.put("timestamp", System.currentTimeMillis());
        notification.put("read", false);

        if (extraData != null) {
            notification.put("extraData", extraData);
        } else {
            notification.put("extraData", new HashMap<>());
        }

        // Save to Firebase
        notificationsRef.child(notificationId).setValue(notification)
                .addOnSuccessListener(aVoid -> {
                    // Notification saved successfully
                    Log.d("Notification", "Notification sent to " + receiverId + ": " + title);
                })
                .addOnFailureListener(e -> {
                    Log.e("Notification", "Failed to send notification: " + e.getMessage());
                });
    }

    // Send notification to borrower
    public void sendBorrowerNotification(String type, String bookingId, String productId,
                                         String ownerId, String productName, Map<String, Object> extraData) {

        // Get receiver ID from booking data (borrower)
        getBookingInfo(bookingId, (borrowerId, ownerIdFromBooking) -> {
            if (borrowerId == null) return;

            String title = "";
            String message = "";

            switch (type) {
                case "booking_confirmation":
                    title = "Booking Confirmed";
                    message = "Your booking for " + productName + " has been confirmed.";
                    break;
                case "ready_pickup":
                    String deliveryOption = extraData != null && extraData.containsKey("deliveryOption")
                            ? extraData.get("deliveryOption").toString() : "";
                    if ("Delivery".equals(deliveryOption)) {
                        title = "Out for Delivery";
                        message = productName + " is out for delivery. It will arrive soon.";
                    } else {
                        title = "Ready for Pickup";
                        message = productName + " is ready for pickup. Please collect it soon.";
                    }
                    break;
                case "completed_refund":
                    title = "Refund Completed";
                    message = "Your refund for " + productName + " has been successfully returned.";
                    break;
            }

            sendNotification(type, bookingId, productId, borrowerId, "borrower",
                    ownerId, productName, extraData, title, message);
        });
    }

    // Send notification to owner
    public void sendOwnerNotification(String type, String bookingId, String productId,
                                      String borrowerId, String productName, Map<String, Object> extraData) {

        // Get receiver ID from booking data (owner)
        getBookingInfo(bookingId, (renterId, ownerId) -> {
            if (ownerId == null) return;

            String title = "";
            String message = "";

            switch (type) {
                case "booking_confirmation":
                    String renterName = extraData != null && extraData.containsKey("renterName")
                            ? extraData.get("renterName").toString() : "a borrower";
                    title = "New Booking Received";
                    message = renterName + " has booked your " + productName + ".";
                    break;
                case "borrower_return":
                    title = "Item Returned";
                    message = "The borrower has returned " + productName + ". Please inspect it.";
                    break;
                // Chat case removed
            }

            sendNotification(type, bookingId, productId, ownerId, "owner",
                    borrowerId, productName, extraData, title, message);
        });
    }

    // Helper method to get booking info
    private void getBookingInfo(String bookingId, BookingInfoCallback callback) {
        DatabaseReference bookingsRef = FirebaseDatabase.getInstance().getReference("bookings");
        bookingsRef.child(bookingId).addListenerForSingleValueEvent(
                new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String renterId = snapshot.child("renterId").getValue(String.class);
                            String ownerId = snapshot.child("ownerId").getValue(String.class);
                            callback.onBookingInfoReceived(renterId, ownerId);
                        } else {
                            callback.onBookingInfoReceived(null, null);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                        callback.onBookingInfoReceived(null, null);
                    }
                });
    }

    interface BookingInfoCallback {
        void onBookingInfoReceived(String renterId, String ownerId);
    }

    // Mark all notifications as read
    public void markAllAsRead(String userId) {
        if (userId == null) return;

        notificationsRef.orderByChild("userId").equalTo(userId)
                .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                        for (com.google.firebase.database.DataSnapshot notificationSnapshot : snapshot.getChildren()) {
                            notificationSnapshot.getRef().child("read").setValue(true);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                        // Handle error
                    }
                });
    }

    // Delete all notifications
    public void deleteAllNotifications(String userId) {
        if (userId == null) return;

        notificationsRef.orderByChild("userId").equalTo(userId)
                .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                        for (com.google.firebase.database.DataSnapshot notificationSnapshot : snapshot.getChildren()) {
                            notificationSnapshot.getRef().removeValue();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                        // Handle error
                    }
                });
    }
}
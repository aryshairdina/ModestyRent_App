package com.example.modestyrent_app;

import java.util.HashMap;
import java.util.Map;

public class NotificationModel {
    private String notificationId;
    private String userId; // Who receives the notification
    private String userType; // "borrower" or "owner"
    private String type; // Booking types: booking_confirmation, ready_pickup, completed_refund, exceed_date, chat
    private String title;
    private String message;
    private String bookingId;
    private String productId;
    private String otherUserId;
    private long timestamp;
    private boolean read;
    private Map<String, Object> extraData;

    public NotificationModel() {
        // Default constructor required for Firebase
    }

    public NotificationModel(String notificationId, String userId, String userType, String type,
                             String title, String message, String bookingId, String productId,
                             String otherUserId) {
        this.notificationId = notificationId;
        this.userId = userId;
        this.userType = userType;
        this.type = type;
        this.title = title;
        this.message = message;
        this.bookingId = bookingId;
        this.productId = productId;
        this.otherUserId = otherUserId;
        this.timestamp = System.currentTimeMillis();
        this.read = false;
        this.extraData = new HashMap<>();
    }

    // Getters and Setters
    public String getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(String notificationId) {
        this.notificationId = notificationId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getOtherUserId() {
        return otherUserId;
    }

    public void setOtherUserId(String otherUserId) {
        this.otherUserId = otherUserId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public Map<String, Object> getExtraData() {
        return extraData;
    }

    public void setExtraData(Map<String, Object> extraData) {
        this.extraData = extraData;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("notificationId", notificationId);
        map.put("userId", userId);
        map.put("userType", userType);
        map.put("type", type);
        map.put("title", title);
        map.put("message", message);
        map.put("bookingId", bookingId);
        map.put("productId", productId);
        map.put("otherUserId", otherUserId);
        map.put("timestamp", timestamp);
        map.put("read", read);
        map.put("extraData", extraData);
        return map;
    }
}
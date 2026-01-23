package com.example.modestyrent_app;

public class NotificationModel {
    private String id;
    private String title;
    private String message;
    private String type; // "booking", "chat", "status", "inspection", "refund", "completed", "penalty_alert", "return_reminder", "review_request", "dispute"
    private String userId;
    private String targetUserId;
    private String bookingId;
    private String chatId;
    private String productId;
    private String productName;
    private String status; // For booking status updates
    private String amount; // For refund/penalty amounts
    private long timestamp;
    private boolean read;
    private boolean isInApp;
    private boolean isFCM;
    private String clickAction; // Activity to open
    private String icon; // Custom icon

    public NotificationModel() {
        // Default constructor for Firebase
    }

    public NotificationModel(String title, String message, String type, String userId) {
        this.title = title;
        this.message = message;
        this.type = type;
        this.userId = userId;
        this.timestamp = System.currentTimeMillis();
        this.read = false;
        this.isInApp = true;
        this.isFCM = true;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTargetUserId() { return targetUserId; }
    public void setTargetUserId(String targetUserId) { this.targetUserId = targetUserId; }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public boolean isInApp() { return isInApp; }
    public void setInApp(boolean inApp) { isInApp = inApp; }

    public boolean isFCM() { return isFCM; }
    public void setFCM(boolean fcm) { isFCM = fcm; }

    public String getClickAction() { return clickAction; }
    public void setClickAction(String clickAction) { this.clickAction = clickAction; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
}
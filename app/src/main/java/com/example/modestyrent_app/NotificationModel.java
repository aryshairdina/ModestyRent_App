package com.example.modestyrent_app;

public class NotificationModel {
    private String id;
    private String title;
    private String message;
    private String type;
    private long timestamp;
    private boolean read;
    private String bookingId;
    private String chatId;
    private String productId;

    public NotificationModel() {
        // Default constructor for Firebase
    }

    public NotificationModel(String title, String message, String type) {
        this.title = title;
        this.message = message;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
        this.read = false;
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

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
}
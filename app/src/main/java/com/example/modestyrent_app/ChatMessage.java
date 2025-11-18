package com.example.modestyrent_app;

public class ChatMessage {
    private String messageId;
    private String senderId;
    private String message;
    private String fileUrl;
    private String fileType;
    private String type; // "text" or "image"
    private long timestamp;

    public ChatMessage() {}

    public ChatMessage(String messageId, String senderId, String message, long timestamp) {
        this.messageId = messageId;
        this.senderId = senderId;
        this.message = message;
        this.timestamp = timestamp;
        this.type = "text";
    }

    public ChatMessage(String messageId, String senderId, String fileUrl, String fileType, long timestamp) {
        this.messageId = messageId;
        this.senderId = senderId;
        this.fileUrl = fileUrl;
        this.fileType = fileType;
        this.timestamp = timestamp;
        this.type = fileType != null && fileType.equals("image") ? "image" : "file";
    }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}

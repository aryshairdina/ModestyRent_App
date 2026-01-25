package com.example.modestyrent_app;

import java.util.HashMap;
import java.util.Map;

public class ChatMessage {
    private String messageId;
    private String senderId;
    private String message;
    private String fileUrl;
    private String fileType;
    private String type; // "text" or "image"
    private long timestamp;
    private Map<String, Boolean> readBy = new HashMap<>();

    public ChatMessage() {}

    public ChatMessage(String messageId, String senderId, String message, long timestamp) {
        this.messageId = messageId;
        this.senderId = senderId;
        this.message = message;
        this.timestamp = timestamp;
        this.type = "text";
        this.readBy = new HashMap<>();
    }

    public ChatMessage(String messageId, String senderId, String fileUrl, String fileType, long timestamp) {
        this.messageId = messageId;
        this.senderId = senderId;
        this.fileUrl = fileUrl;
        this.fileType = fileType;
        this.timestamp = timestamp;
        this.type = fileType != null && fileType.equals("image") ? "image" : "file";
        this.readBy = new HashMap<>();
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

    public Map<String, Boolean> getReadBy() { return readBy; }
    public void setReadBy(Map<String, Boolean> readBy) { this.readBy = readBy; }

    public boolean isReadBy(String userId) {
        return readBy != null && readBy.containsKey(userId) && Boolean.TRUE.equals(readBy.get(userId));
    }

    public void markAsRead(String userId) {
        if (readBy == null) {
            readBy = new HashMap<>();
        }
        readBy.put(userId, true);
    }
}
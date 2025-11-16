package com.example.modestyrent_app;

import java.util.ArrayList;

public class Product {
    private String id;
    private String name;
    private String description;
    private String category;
    private String size;
    private String status;
    private String userId;
    private double price;
    private ArrayList<String> colors;
    private ArrayList<String> imageUrls;
    private long createdAt;
    private long updated_at;

    // No-arg constructor required for Firebase mapping
    public Product() {}

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public String getSize() { return size; }
    public String getStatus() { return status; }
    public String getUserId() { return userId; }
    public double getPrice() { return price; }
    public ArrayList<String> getColors() { return colors; }
    public ArrayList<String> getImageUrls() { return imageUrls; }
    public long getCreatedAt() { return createdAt; }
    public long getUpdated_at() { return updated_at; }

    // Setters (required)
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setCategory(String category) { this.category = category; }
    public void setSize(String size) { this.size = size; }
    public void setStatus(String status) { this.status = status; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setPrice(double price) { this.price = price; }
    public void setColors(ArrayList<String> colors) { this.colors = colors; }
    public void setImageUrls(ArrayList<String> imageUrls) { this.imageUrls = imageUrls; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public void setUpdated_at(long updated_at) { this.updated_at = updated_at; }
}

package com.example.modestyrent_app;

import java.util.ArrayList;
public class ReelItem {
    public String productId;
    public String title;
    public String ownerId;
    public String rentalPrice;
    public String description;
    public ArrayList<String> mediaUrls;

    public ReelItem(String productId, String title, String ownerId, String rentalPrice, String description, ArrayList<String> mediaUrls) {
        this.productId = productId;
        this.title = title;
        this.ownerId = ownerId;
        this.rentalPrice = rentalPrice;
        this.description = description;
        this.mediaUrls = mediaUrls;
    }
}
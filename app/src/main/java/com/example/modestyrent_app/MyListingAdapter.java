package com.example.modestyrent_app;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MyListingAdapter extends RecyclerView.Adapter<MyListingAdapter.ViewHolder> {

    private final Context context;

    private final List<Product> fullList = new ArrayList<>();
    private final List<Product> displayList = new ArrayList<>();

    public MyListingAdapter(Context context) {
        this.context = context;
    }

    // -------- DATA CONTROL --------

    public void setData(List<Product> data) {
        fullList.clear();
        displayList.clear();
        if (data != null) {
            fullList.addAll(data);
            displayList.addAll(data);
        }
        notifyDataSetChanged();
    }

    public Product getItemAt(int position) {
        if (position < 0 || position >= displayList.size()) return null;
        return displayList.get(position);
    }

    // -------- SEARCH FILTER --------

    public void filter(String query) {
        displayList.clear();

        if (query == null || query.trim().isEmpty()) {
            displayList.addAll(fullList);
        } else {
            String q = query.toLowerCase(Locale.getDefault());

            for (Product p : fullList) {
                if (p == null) continue;

                if (safe(p.getName()).contains(q) ||
                        safe(p.getCategory()).contains(q) ||
                        safe(p.getDescription()).contains(q) ||
                        safe(p.getStatus()).contains(q)) {
                    displayList.add(p);
                }
            }
        }
        notifyDataSetChanged();
    }

    private String safe(String s) {
        return s == null ? "" : s.toLowerCase(Locale.getDefault());
    }

    // -------- RECYCLER --------

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context)
                .inflate(R.layout.item_product, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        Product p = displayList.get(position);

        h.itemName.setText(p.getName());
        h.itemPrice.setText("RM " + p.getPrice());
        h.itemSize.setText(p.getSize());
        h.itemStatusBadge.setText(p.getStatus());

        if (p.getImageUrls() != null && !p.getImageUrls().isEmpty()) {
            Glide.with(context)
                    .load(p.getImageUrls().get(0))
                    .into(h.itemImage);
        } else {
            h.itemImage.setImageResource(R.drawable.sample_product);
        }

        GradientDrawable bg = (GradientDrawable) h.itemStatusBadge.getBackground();
        if ("available".equalsIgnoreCase(p.getStatus())) {
            bg.setColor(ContextCompat.getColor(context, android.R.color.holo_green_dark));
        } else {
            bg.setColor(ContextCompat.getColor(context, android.R.color.holo_red_dark));
        }
    }

    @Override
    public int getItemCount() {
        return displayList.size();
    }

    // -------- VIEW HOLDER --------

    static class ViewHolder extends RecyclerView.ViewHolder {

        ImageView itemImage;
        TextView itemName, itemPrice, itemSize, itemStatusBadge;

        ViewHolder(@NonNull View v) {
            super(v);
            itemImage = v.findViewById(R.id.itemImage);
            itemName = v.findViewById(R.id.itemName);
            itemPrice = v.findViewById(R.id.itemPrice);
            itemSize = v.findViewById(R.id.itemSize);
            itemStatusBadge = v.findViewById(R.id.itemStatusBadge);
        }
    }
}

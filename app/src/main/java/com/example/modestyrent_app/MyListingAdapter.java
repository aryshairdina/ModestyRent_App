package com.example.modestyrent_app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import androidx.core.content.ContextCompat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;

public class MyListingAdapter extends RecyclerView.Adapter<MyListingAdapter.ViewHolder> {

    ArrayList<Product> list;
    ArrayList<Product> fullList;
    Context context;

    public MyListingAdapter(ArrayList<Product> list, Context context) {
        this.list = list;
        this.context = context;
        this.fullList = new ArrayList<>(list);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_product, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        Product p = list.get(position);

        h.itemName.setText(p.getName());
        h.itemPrice.setText("RM " + p.getPrice());
        h.itemSize.setText(p.getSize());
        h.itemStatusBadge.setText(p.getStatus());

        // Load product image
        if (p.getImageUrls() != null && !p.getImageUrls().isEmpty()) {
            Glide.with(context)
                    .load(p.getImageUrls().get(0))
                    .placeholder(R.drawable.sample_product)
                    .into(h.itemImage);
        } else {
            h.itemImage.setImageResource(R.drawable.sample_product);
        }

        // Status badge color
        GradientDrawable bg = (GradientDrawable) h.itemStatusBadge.getBackground();
        if (p.getStatus().equalsIgnoreCase("Available")) {
            bg.setColor(ContextCompat.getColor(context, android.R.color.holo_green_dark));
        } else {
            bg.setColor(ContextCompat.getColor(context, android.R.color.holo_red_dark));
        }

        // Click → open UpdateListingActivity
        h.itemView.setOnClickListener(v -> {
            Intent i = new Intent(context, activity_mylisting.class);
            i.putExtra("productId", p.getId());
            context.startActivity(i);
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    // Search filter
    public void filter(String query) {
        list.clear();

        if (query.isEmpty()) {
            list.addAll(fullList);
        } else {
            query = query.toLowerCase();

            for (Product p : fullList) {
                if (p.getName().toLowerCase().contains(query) ||
                        p.getCategory().toLowerCase().contains(query) ||
                        p.getDescription().toLowerCase().contains(query)) {

                    list.add(p);
                }
            }
        }

        notifyDataSetChanged();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        ImageView itemImage;
        TextView itemName, itemPrice, itemSize, itemStatusBadge;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            itemImage = itemView.findViewById(R.id.itemImage);
            itemName = itemView.findViewById(R.id.itemName);
            itemPrice = itemView.findViewById(R.id.itemPrice);
            itemSize = itemView.findViewById(R.id.itemSize);
            itemStatusBadge = itemView.findViewById(R.id.itemStatusBadge);
        }
    }
}

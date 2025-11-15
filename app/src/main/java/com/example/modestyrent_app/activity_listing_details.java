package com.example.modestyrent_app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * activity_listing_details.java
 * - Loads a product by productId (intent extra "productId")
 * - Populates form fields
 * - Allows changing image & updating product data in Firebase Realtime Database
 * - Uploads images to storage path: products/{ownerUserId}/{productId}.jpg
 */
public class activity_listing_details extends AppCompatActivity {

    private static final int REQ_PICK_IMAGE = 1001;

    private ImageView ld_backIcon, ld_imgProduct;
    private Button ld_btnPickImage, ld_btnUpdate;
    private TextInputEditText ld_etName, ld_etSize, ld_etPrice, ld_etDescription;
    private ChipGroup ld_chipGroupStatus, ld_chipGroupCategory, ld_chipGroupColor;
    private Chip ld_chipAvailable, ld_chipReserved, ld_chipUnavailable;
    private Chip ld_chipCategoryKurung, ld_chipCategoryJubah, ld_chipCategoryKebaya;
    private Chip ld_chipColorWhite, ld_chipColorBlack, ld_chipColorNavy, ld_chipColorPink;
    private ProgressBar ld_progress;

    private FirebaseAuth auth;
    private DatabaseReference productsRef;
    private StorageReference storageRootRef;

    private String productId;
    private Uri selectedImageUri = null;
    private String currentImageUrl = null;
    private String ownerUserId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_listing_details);

        // Firebase init
        auth = FirebaseAuth.getInstance();
        productsRef = FirebaseDatabase.getInstance().getReference("products");
        storageRootRef = FirebaseStorage.getInstance().getReference();

        // bind views
        ld_backIcon = findViewById(R.id.ld_backIcon);
        ld_imgProduct = findViewById(R.id.ld_imgProduct);
        ld_btnPickImage = findViewById(R.id.ld_btnPickImage);
        ld_btnUpdate = findViewById(R.id.ld_btnUpdate);

        ld_etName = findViewById(R.id.ld_etName);
        ld_etSize = findViewById(R.id.ld_etSize);
        ld_etPrice = findViewById(R.id.ld_etPrice);
        ld_etDescription = findViewById(R.id.ld_etDescription);

        ld_chipGroupStatus = findViewById(R.id.ld_chipGroupStatus);
        ld_chipGroupCategory = findViewById(R.id.ld_chipGroupCategory);
        ld_chipGroupColor = findViewById(R.id.ld_chipGroupColor);

        ld_chipAvailable = findViewById(R.id.ld_chipAvailable);
        ld_chipReserved = findViewById(R.id.ld_chipReserved);
        ld_chipUnavailable = findViewById(R.id.ld_chipUnavailable);

        ld_chipCategoryKurung = findViewById(R.id.ld_chipCategoryKurung);
        ld_chipCategoryJubah = findViewById(R.id.ld_chipCategoryJubah);
        ld_chipCategoryKebaya = findViewById(R.id.ld_chipCategoryKebaya);

        ld_chipColorWhite = findViewById(R.id.ld_chipColorWhite);
        ld_chipColorBlack = findViewById(R.id.ld_chipColorBlack);
        ld_chipColorNavy = findViewById(R.id.ld_chipColorNavy);
        ld_chipColorPink = findViewById(R.id.ld_chipColorPink);

        ld_progress = findViewById(R.id.ld_progress);

        // disable image button until product loads (prevent upload before ownerUserId known)
        ld_btnPickImage.setEnabled(false);

        // Back pressed
        ld_backIcon.setOnClickListener(v -> finish());

        // get productId
        productId = getIntent().getStringExtra("productId");
        if (TextUtils.isEmpty(productId)) {
            Toast.makeText(this, "No product selected.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // pick image
        ld_btnPickImage.setOnClickListener(v -> openImagePicker());

        // update
        ld_btnUpdate.setOnClickListener(v -> attemptUpdate());

        // load product
        loadProductAndPopulate();
    }

    private void setLoading(boolean loading) {
        ld_progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        ld_btnUpdate.setEnabled(!loading);
        ld_btnPickImage.setEnabled(!loading && ownerUserId != null);
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, "Select Product Image"), REQ_PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_PICK_IMAGE && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                selectedImageUri = uri;
                // preview
                Glide.with(this).load(selectedImageUri).into(ld_imgProduct);
            }
        }
    }

    private void loadProductAndPopulate() {
        setLoading(true);
        // Single read
        productsRef.child(productId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                setLoading(false);
                if (!snapshot.exists()) {
                    Toast.makeText(activity_listing_details.this, "Product not found.", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                ownerUserId = snapshot.child("userId").getValue(String.class);

                // check signed-in user
                FirebaseUser current = auth.getCurrentUser();
                if (current == null) {
                    Toast.makeText(activity_listing_details.this, "Please login first.", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                String currentUid = current.getUid();
                if (ownerUserId == null || !ownerUserId.equals(currentUid)) {
                    Toast.makeText(activity_listing_details.this, "You don't have permission to edit this product.", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                // enable image pick now that ownerUserId is known
                ld_btnPickImage.setEnabled(true);

                // populate fields
                String name = snapshot.child("name").getValue(String.class);
                String size = snapshot.child("size").getValue(String.class);
                String description = snapshot.child("description").getValue(String.class);

                Double price = null;
                Object priceObj = snapshot.child("price").getValue();
                if (priceObj != null) {
                    try {
                        price = Double.parseDouble(String.valueOf(priceObj));
                    } catch (Exception ignored) {}
                }

                String status = snapshot.child("status").getValue(String.class);
                String category = snapshot.child("category").getValue(String.class);

                // load imageUrls (use first if exists)
                currentImageUrl = null;
                if (snapshot.child("imageUrls").exists()) {
                    for (DataSnapshot imgNode : snapshot.child("imageUrls").getChildren()) {
                        String url = imgNode.getValue(String.class);
                        if (!TextUtils.isEmpty(url)) {
                            currentImageUrl = url;
                            break;
                        }
                    }
                } else if (snapshot.child("imageUrl").exists()) {
                    currentImageUrl = snapshot.child("imageUrl").getValue(String.class);
                }

                if (!TextUtils.isEmpty(currentImageUrl)) {
                    Glide.with(activity_listing_details.this)
                            .load(currentImageUrl)
                            .placeholder(R.drawable.sample_product)
                            .into(ld_imgProduct);
                } else {
                    ld_imgProduct.setImageResource(R.drawable.sample_product);
                }

                if (name != null) ld_etName.setText(name);
                if (size != null) ld_etSize.setText(size);
                if (description != null) ld_etDescription.setText(description);
                if (price != null) ld_etPrice.setText(String.valueOf(price));

                // status
                if (status != null) {
                    switch (status.toLowerCase().trim()) {
                        case "available":
                            ld_chipGroupStatus.check(ld_chipAvailable.getId());
                            break;
                        case "reserved":
                            ld_chipGroupStatus.check(ld_chipReserved.getId());
                            break;
                        case "unavailable":
                            ld_chipGroupStatus.check(ld_chipUnavailable.getId());
                            break;
                        default:
                            ld_chipGroupStatus.clearCheck();
                    }
                }

                // category
                if (category != null) {
                    String cat = category.toLowerCase().trim();
                    if (cat.contains("kurung")) ld_chipGroupCategory.check(ld_chipCategoryKurung.getId());
                    else if (cat.contains("jubah")) ld_chipGroupCategory.check(ld_chipCategoryJubah.getId());
                    else if (cat.contains("kebaya")) ld_chipGroupCategory.check(ld_chipCategoryKebaya.getId());
                    else ld_chipGroupCategory.clearCheck();
                }

                // colors: many possible shapes; try list then string
                if (snapshot.child("colors").exists()) {
                    // clear all
                    ld_chipColorWhite.setChecked(false);
                    ld_chipColorBlack.setChecked(false);
                    ld_chipColorNavy.setChecked(false);
                    ld_chipColorPink.setChecked(false);

                    for (DataSnapshot c : snapshot.child("colors").getChildren()) {
                        String color = c.getValue(String.class);
                        if (color == null) continue;
                        String lower = color.toLowerCase();
                        if (lower.contains("white")) ld_chipColorWhite.setChecked(true);
                        else if (lower.contains("black")) ld_chipColorBlack.setChecked(true);
                        else if (lower.contains("navy")) ld_chipColorNavy.setChecked(true);
                        else if (lower.contains("pink")) ld_chipColorPink.setChecked(true);
                    }
                } else if (snapshot.child("color").exists()) {
                    String colorsStr = snapshot.child("color").getValue(String.class);
                    applyColorStringToChips(colorsStr);
                } else if (snapshot.child("colorCSV").exists()) {
                    applyColorStringToChips(snapshot.child("colorCSV").getValue(String.class));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                setLoading(false);
                Toast.makeText(activity_listing_details.this, "Failed to load product: " + error.getMessage(), Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void applyColorStringToChips(String colorsStr) {
        if (colorsStr == null) return;
        String lower = colorsStr.toLowerCase();
        if (lower.contains("white")) ld_chipColorWhite.setChecked(true);
        if (lower.contains("black")) ld_chipColorBlack.setChecked(true);
        if (lower.contains("navy")) ld_chipColorNavy.setChecked(true);
        if (lower.contains("pink")) ld_chipColorPink.setChecked(true);
    }

    private void attemptUpdate() {
        // check auth & ownership again
        FirebaseUser current = auth.getCurrentUser();
        if (current == null) {
            Toast.makeText(this, "Please sign in first.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (ownerUserId == null || !ownerUserId.equals(current.getUid())) {
            Toast.makeText(this, "You don't have permission to update this product.", Toast.LENGTH_SHORT).show();
            return;
        }

        final String name = ld_etName.getText() != null ? ld_etName.getText().toString().trim() : "";
        final String size = ld_etSize.getText() != null ? ld_etSize.getText().toString().trim() : "";
        final String priceStr = ld_etPrice.getText() != null ? ld_etPrice.getText().toString().trim() : "";
        final String description = ld_etDescription.getText() != null ? ld_etDescription.getText().toString().trim() : "";

        if (TextUtils.isEmpty(name)) {
            ld_etName.setError("Name required");
            ld_etName.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(priceStr)) {
            ld_etPrice.setError("Price required");
            ld_etPrice.requestFocus();
            return;
        }

        double price;
        try {
            price = Double.parseDouble(priceStr);
            if (price < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            ld_etPrice.setError("Invalid price");
            ld_etPrice.requestFocus();
            return;
        }

        // status
        String status = null;
        int checkedStatusId = ld_chipGroupStatus.getCheckedChipId();
        if (checkedStatusId == ld_chipAvailable.getId()) status = "Available";
        else if (checkedStatusId == ld_chipReserved.getId()) status = "Reserved";
        else if (checkedStatusId == ld_chipUnavailable.getId()) status = "Unavailable";

        // category
        String category = null;
        int checkedCatId = ld_chipGroupCategory.getCheckedChipId();
        if (checkedCatId == ld_chipCategoryKurung.getId()) category = "Kurung";
        else if (checkedCatId == ld_chipCategoryJubah.getId()) category = "Jubah";
        else if (checkedCatId == ld_chipCategoryKebaya.getId()) category = "Kebaya";

        // colors
        List<String> colors = new ArrayList<>();
        if (ld_chipColorWhite.isChecked()) colors.add("White");
        if (ld_chipColorBlack.isChecked()) colors.add("Black");
        if (ld_chipColorNavy.isChecked()) colors.add("Navy");
        if (ld_chipColorPink.isChecked()) colors.add("Pastel Pink");

        // prepare updates
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("size", size);
        updates.put("price", price);
        updates.put("description", description);
        if (status != null) updates.put("status", status);
        if (category != null) updates.put("category", category);
        updates.put("colors", colors);
        updates.put("updated_at", System.currentTimeMillis());

        setLoading(true);

        // If user selected new image — upload it under: products/{ownerUserId}/{productId}.jpg
        if (selectedImageUri != null) {
            if (ownerUserId == null) {
                setLoading(false);
                Toast.makeText(this, "Owner UID not determined. Try again.", Toast.LENGTH_LONG).show();
                return;
            }

            StorageReference imgRef = storageRootRef.child("products")
                    .child(ownerUserId)
                    .child(productId + ".jpg");

            imgRef.putFile(selectedImageUri)
                    .addOnSuccessListener(taskSnapshot -> imgRef.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                        @Override
                        public void onSuccess(Uri uri) {
                            String downloadUrl = uri.toString();
                            List<String> imageUrls = new ArrayList<>();
                            imageUrls.add(downloadUrl);
                            updates.put("imageUrls", imageUrls);

                            // update database
                            productsRef.child(productId).updateChildren(updates)
                                    .addOnCompleteListener(task -> {
                                        setLoading(false);
                                        if (task.isSuccessful()) {
                                            Toast.makeText(activity_listing_details.this, "Product updated successfully.", Toast.LENGTH_SHORT).show();
                                            finish();
                                        } else {
                                            Toast.makeText(activity_listing_details.this, "Failed to update product: " + task.getException(), Toast.LENGTH_LONG).show();
                                        }
                                    });
                        }
                    }).addOnFailureListener(e -> {
                        setLoading(false);
                        Toast.makeText(activity_listing_details.this, "Failed to get image URL: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }))
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            setLoading(false);
                            Toast.makeText(activity_listing_details.this, "Image upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
        } else {
            // no image change, update DB directly
            productsRef.child(productId).updateChildren(updates).addOnCompleteListener(task -> {
                setLoading(false);
                if (task.isSuccessful()) {
                    Toast.makeText(activity_listing_details.this, "Product updated successfully.", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(activity_listing_details.this, "Failed to update product: " + task.getException(), Toast.LENGTH_LONG).show();
                }
            });
        }
    }
}

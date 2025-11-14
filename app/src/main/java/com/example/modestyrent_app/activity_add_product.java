package com.example.modestyrent_app; // change to your package

import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class activity_add_product extends AppCompatActivity {

    private Button btnSelectImage, btnSave;
    private TextInputEditText etItemName, etPrice, spinnerSize, etDescription;
    private ChipGroup chipGroupStatus, chipGroupColor, chipGroupCategory;

    // selected URIs (no persist)
    private final ArrayList<Uri> selectedImageUris = new ArrayList<>();

    private ActivityResultLauncher<Intent> pickImagesLauncher;

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseStorage storage;
    private DatabaseReference realtimeDb;

    private ProgressDialog progressDialog;

    private static final int MAX_IMAGES = 8;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_product);

        // Firebase
        mAuth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();
        realtimeDb = FirebaseDatabase.getInstance().getReference();

        // UI refs
        btnSelectImage = findViewById(R.id.btnSelectImage);
        btnSave = findViewById(R.id.btnSave);
        etItemName = findViewById(R.id.etItemName);
        etPrice = findViewById(R.id.etPrice);
        spinnerSize = findViewById(R.id.spinnerSize);
        etDescription = findViewById(R.id.etDescription);

        chipGroupStatus = findViewById(R.id.chipGroupStatus);
        chipGroupColor = findViewById(R.id.chipGroupColor);
        chipGroupCategory = findViewById(R.id.chipGroupCategory);

        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);

        // Prepare ActivityResultLauncher to pick images (ACTION_GET_CONTENT)
        pickImagesLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        handlePickedImages(result.getData());
                    }
                }
        );

        btnSelectImage.setOnClickListener(v -> openImagePicker());

        btnSave.setOnClickListener(v -> saveProduct());
    }

    /**
     * Open picker using ACTION_GET_CONTENT (no persistable URI permissions).
     */
    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        // Do NOT add PERSISTABLE flags since we won't call takePersistableUriPermission
        pickImagesLauncher.launch(Intent.createChooser(intent, "Select images"));
    }

    /**
     * Collect selected URIs from intent. No persistence requested.
     */
    private void handlePickedImages(Intent data) {
        selectedImageUris.clear();
        if (data == null) {
            Toast.makeText(this, "No images selected", Toast.LENGTH_SHORT).show();
            return;
        }

        ClipData clip = data.getClipData();
        if (clip != null) {
            int count = Math.min(clip.getItemCount(), MAX_IMAGES);
            for (int i = 0; i < count; i++) {
                ClipData.Item item = clip.getItemAt(i);
                if (item == null) continue;
                Uri uri = item.getUri();
                if (uri == null) continue;
                selectedImageUris.add(uri);
            }
        } else {
            Uri uri = data.getData();
            if (uri != null) {
                selectedImageUris.add(uri);
            }
        }

        if (selectedImageUris.isEmpty()) {
            Toast.makeText(this, "No images selected", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, selectedImageUris.size() + " image(s) selected", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Validate inputs, upload images to Storage, then write a product object to Realtime Database.
     */
    private void saveProduct() {
        String name = etItemName.getText() != null ? etItemName.getText().toString().trim() : "";
        String priceStr = etPrice.getText() != null ? etPrice.getText().toString().trim() : "";
        String size = spinnerSize.getText() != null ? spinnerSize.getText().toString().trim() : "";
        String desc = etDescription.getText() != null ? etDescription.getText().toString().trim() : "";

        if (TextUtils.isEmpty(name)) {
            etItemName.setError("Enter item name");
            return;
        }
        if (TextUtils.isEmpty(priceStr)) {
            etPrice.setError("Enter price");
            return;
        }

        double price;
        try {
            price = Double.parseDouble(priceStr);
            if (price < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            etPrice.setError("Invalid price");
            return;
        }

        // status
        int checkedStatusId = chipGroupStatus.getCheckedChipId();
        String status = "available";
        if (checkedStatusId != -1) {
            Chip c = findViewById(checkedStatusId);
            if (c != null) status = c.getText().toString().toUpperCase();
        }

        // colors (multi)
        List<String> colors = new ArrayList<>();
        for (int i = 0; i < chipGroupColor.getChildCount(); i++) {
            View v = chipGroupColor.getChildAt(i);
            if (v instanceof Chip) {
                Chip chip = (Chip) v;
                if (chip.isChecked()) colors.add(chip.getText().toString());
            }
        }

        // category single
        int checkedCategoryId = chipGroupCategory.getCheckedChipId();
        String category = "Other";
        if (checkedCategoryId != -1) {
            Chip c = findViewById(checkedCategoryId);
            if (c != null) category = c.getText().toString();
        }

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please sign in first.", Toast.LENGTH_SHORT).show();
            return;
        }

        progressDialog.setMessage("Uploading images...");
        progressDialog.show();

        if (selectedImageUris.isEmpty()) {
            // create product with empty imageUrls
            createProductInRealtimeDb(user.getUid(), name, price, size, status, colors, category, desc, new ArrayList<>());
        } else {
            uploadImagesThenSave(user.getUid(), name, price, size, status, colors, category, desc);
        }
    }

    /**
     * Upload images to Firebase Storage and gather their download URLs.
     */
    private void uploadImagesThenSave(String uid,
                                      String name,
                                      double price,
                                      String size,
                                      String status,
                                      List<String> colors,
                                      String category,
                                      String desc) {

        StorageReference rootRef = storage.getReference();
        List<Task<Uri>> urlTasks = new ArrayList<>();

        Executor executor = Executors.newSingleThreadExecutor();

        for (Uri uri : selectedImageUris) {
            String filename = UUID.randomUUID().toString();
            StorageReference fileRef = rootRef.child("products").child(uid).child(filename);

            UploadTask uploadTask = fileRef.putFile(uri);
            // chain to get download url
            Task<Uri> urlTask = uploadTask.continueWithTask(task -> {
                if (!task.isSuccessful()) {
                    throw task.getException() != null ? task.getException() : new Exception("Upload failed");
                }
                return fileRef.getDownloadUrl();
            });

            urlTasks.add(urlTask);
        }

        // Wait for all to succeed
        Tasks.whenAllSuccess(urlTasks)
                .addOnSuccessListener(objects -> {
                    List<String> downloadUrls = new ArrayList<>();
                    for (Object obj : objects) {
                        if (obj instanceof Uri) downloadUrls.add(((Uri) obj).toString());
                        else if (obj != null) downloadUrls.add(obj.toString());
                    }
                    // write to Realtime DB
                    createProductInRealtimeDb(uid, name, price, size, status, colors, category, desc, downloadUrls);
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(activity_add_product.this, "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    /**
     * Create product node under "products" in Realtime Database.
     */
    private void createProductInRealtimeDb(String uid,
                                           String name,
                                           double price,
                                           String size,
                                           String status,
                                           List<String> colors,
                                           String category,
                                           String desc,
                                           List<String> imageUrls) {

        progressDialog.setMessage("Saving product...");

        DatabaseReference productsRef = realtimeDb.child("products");
        String productId = productsRef.push().getKey(); // generate id

        if (productId == null) {
            progressDialog.dismiss();
            Toast.makeText(this, "Failed to generate product id", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> product = new HashMap<>();
        product.put("id", productId);
        product.put("userId", uid);
        product.put("name", name);
        product.put("price", price);
        product.put("size", size);
        product.put("status", status);
        product.put("colors", colors);
        product.put("category", category);
        product.put("description", desc);
        product.put("imageUrls", imageUrls);
        product.put("createdAt", System.currentTimeMillis());

        productsRef.child(productId)
                .setValue(product)
                .addOnSuccessListener(aVoid -> {
                    progressDialog.dismiss();
                    Toast.makeText(activity_add_product.this, "Product added.", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(activity_add_product.this, "Failed to save product: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}

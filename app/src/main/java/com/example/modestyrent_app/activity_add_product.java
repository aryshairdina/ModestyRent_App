package com.example.modestyrent_app;

import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.gms.tasks.Continuation;
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

public class activity_add_product extends AppCompatActivity {

    private Button btnSelectImage, btnSave;

    private ImageView btnBack;
    private TextInputEditText etItemName, etPrice, spinnerSize, etDescription;
    private ChipGroup chipGroupStatus, chipGroupColor, chipGroupCategory;

    private ViewPager2 mediaViewPager;
    private MediaPagerAdapter mediaPagerAdapter;
    private TextView tvMediaCount;

    private final ArrayList<Uri> selectedMediaUris = new ArrayList<>();
    private ActivityResultLauncher<Intent> pickMediaLauncher;

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseStorage storage;
    private DatabaseReference realtimeDb;

    private ProgressDialog progressDialog;

    private static final int MAX_MEDIA = 8;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_product);

        // Firebase Initialization
        mAuth = FirebaseAuth.getInstance();

        // 🔹 AUTH SAFETY GUARD (only addition)
        if (mAuth.getCurrentUser() == null) {
            startActivity(new Intent(this, activity_signin.class));
            finish();
            return;
        }
        // 🔹 END auth guard

        storage = FirebaseStorage.getInstance();
        realtimeDb = FirebaseDatabase.getInstance().getReference();

        // UI references
        btnSelectImage = findViewById(R.id.btnSelectImage);
        btnSave = findViewById(R.id.btnSave);
        etItemName = findViewById(R.id.etItemName);
        etPrice = findViewById(R.id.etPrice);
        spinnerSize = findViewById(R.id.spinnerSize);
        etDescription = findViewById(R.id.etDescription);
        btnBack = findViewById(R.id.btnBack);

        chipGroupStatus = findViewById(R.id.chipGroupStatus);
        chipGroupColor = findViewById(R.id.chipGroupColor);
        chipGroupCategory = findViewById(R.id.chipGroupCategory);

        mediaViewPager = findViewById(R.id.mediaViewPager);
        tvMediaCount = findViewById(R.id.tvMediaCount);

        mediaPagerAdapter = new MediaPagerAdapter(this, new ArrayList<>());
        mediaViewPager.setAdapter(mediaPagerAdapter);

        mediaViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateMediaCountText();
            }
        });

        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);

        pickMediaLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        handlePickedImages(result.getData());
                    }
                }
        );

        btnSelectImage.setOnClickListener(v -> openMediaPicker());
        btnSave.setOnClickListener(v -> saveProduct());
        btnBack.setOnClickListener(v -> {
            onBackPressed(); // or finish();
        });

        updateMediaCountText();
    }



private void updateMediaCountText() {
        int count = selectedMediaUris.size();
        if (count > 0) {
            tvMediaCount.setText(String.format("%d / %d", mediaViewPager.getCurrentItem() + 1, count));
            tvMediaCount.setVisibility(View.VISIBLE);
            btnSelectImage.setVisibility(View.GONE);
        } else {
            tvMediaCount.setVisibility(View.GONE);
            btnSelectImage.setVisibility(View.VISIBLE);
        }
    }

    private void openMediaPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

        pickMediaLauncher.launch(
                Intent.createChooser(intent, "Select photos or videos (Max " + MAX_MEDIA + ")")
        );
    }

    private void handlePickedImages(Intent data) {
        ArrayList<Uri> newUris = new ArrayList<>();

        ClipData clip = data.getClipData();
        if (clip != null) {
            int count = Math.min(clip.getItemCount(), MAX_MEDIA);
            for (int i = 0; i < count; i++) {
                ClipData.Item item = clip.getItemAt(i);
                if (item == null) continue;
                Uri uri = item.getUri();
                if (uri != null) {
                    newUris.add(uri);
                }
            }
        } else {
            Uri uri = data.getData();
            if (uri != null) {
                newUris.add(uri);
            }
        }

        selectedMediaUris.clear();
        selectedMediaUris.addAll(newUris);

        mediaPagerAdapter.updateMedia(selectedMediaUris);

        if (!selectedMediaUris.isEmpty()) {
            mediaViewPager.setCurrentItem(0, false);
        }

        updateMediaCountText();

        if (selectedMediaUris.isEmpty()) {
            Toast.makeText(this, "No media selected", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, selectedMediaUris.size() + " media item(s) selected", Toast.LENGTH_SHORT).show();
        }
    }

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
        String status = "Available";
        if (checkedStatusId != -1) {
            Chip c = findViewById(checkedStatusId);
            if (c != null) status = c.getText().toString();
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

        progressDialog.setMessage("Uploading media...");
        progressDialog.show();

        if (selectedMediaUris.isEmpty()) {
            createProductInRealtimeDb(user.getUid(), name, price, size, status, colors, category, desc, new ArrayList<>());
        } else {
            uploadImagesThenSave(user.getUid(), name, price, size, status, colors, category, desc);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPagerAdapter != null) {
            mediaPagerAdapter.releasePlayers();
        }
    }

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

        for (Uri uri : selectedMediaUris) {
            String filename = UUID.randomUUID().toString();
            String extension = ".dat";

            String mimeType = getContentResolver().getType(uri);
            if (mimeType != null) {
                if (mimeType.contains("image")) extension = ".jpg";
                else if (mimeType.contains("video")) extension = ".mp4";
            }

            StorageReference fileRef = rootRef.child("products").child(uid).child(filename + extension);
            UploadTask uploadTask = fileRef.putFile(uri);

            Task<Uri> urlTask = uploadTask.continueWithTask(
                    new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
                        @Override
                        public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) throws Exception {
                            if (!task.isSuccessful()) {
                                Exception e = task.getException();
                                throw e != null ? e : new Exception("Upload failed");
                            }
                            return fileRef.getDownloadUrl();
                        }
                    }
            );

            urlTasks.add(urlTask);
        }

        Tasks.whenAllSuccess(urlTasks)
                .addOnSuccessListener(objects -> {
                    List<String> downloadUrls = new ArrayList<>();
                    for (Object obj : objects) {
                        if (obj instanceof Uri) {
                            downloadUrls.add(((Uri) obj).toString());
                        }
                    }
                    createProductInRealtimeDb(uid, name, price, size, status, colors, category, desc, downloadUrls);
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(activity_add_product.this, "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

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
        String productId = productsRef.push().getKey();

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
                    Toast.makeText(activity_add_product.this, "Product added successfully!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(activity_add_product.this, "Failed to save product: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}

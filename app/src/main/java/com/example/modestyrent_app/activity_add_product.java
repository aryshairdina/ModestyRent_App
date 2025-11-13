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

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ActivityAddProduct
 * - pick multiple images from gallery
 * - upload images to Firebase Storage
 * - store product metadata in Firestore under users/{uid}/products/{productId}
 */
public class activity_add_product extends AppCompatActivity {

    private Button btnSelectImage, btnSave;
    private TextInputEditText etItemName, spinnerSize, etPrice, etDescription;
    private ChipGroup chipGroupStatus, chipGroupColor, chipGroupCategory;

    private final List<Uri> imageUriList = new ArrayList<>();
    private final List<String> uploadedImageUrls = new ArrayList<>();

    private FirebaseAuth mAuth;
    private FirebaseFirestore firestore;
    private StorageReference storageRef;

    private ProgressDialog progressDialog;

    // ActivityResultLauncher to handle gallery intent
    private ActivityResultLauncher<Intent> galleryLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_product); // ensure this matches your XML filename

        // init firebase
        mAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        storageRef = FirebaseStorage.getInstance().getReference();

        // UI
        btnSelectImage = findViewById(R.id.btnSelectImage);
        btnSave = findViewById(R.id.btnSave);
        etItemName = findViewById(R.id.etItemName);
        spinnerSize = findViewById(R.id.spinnerSize);
        etPrice = findViewById(R.id.etPrice);
        etDescription = findViewById(R.id.etDescription);

        chipGroupStatus = findViewById(R.id.chipGroupStatus);
        chipGroupColor = findViewById(R.id.chipGroupColor);
        chipGroupCategory = findViewById(R.id.chipGroupCategory);

        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);

        // register gallery launcher
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            Intent data = result.getData();
                            // multiple selection
                            if (data.getClipData() != null) {
                                ClipData clipData = data.getClipData();
                                int count = clipData.getItemCount();
                                imageUriList.clear();
                                for (int i = 0; i < count; i++) {
                                    Uri imageUri = clipData.getItemAt(i).getUri();
                                    imageUriList.add(imageUri);
                                }
                                Toast.makeText(activity_add_product.this, count + " images selected", Toast.LENGTH_SHORT).show();
                            } else {
                                // single selection
                                Uri imageUri = data.getData();
                                imageUriList.clear();
                                if (imageUri != null) {
                                    imageUriList.add(imageUri);
                                    Toast.makeText(activity_add_product.this, "1 image selected", Toast.LENGTH_SHORT).show();
                                }
                            }
                        }
                    }
                }
        );

        btnSelectImage.setOnClickListener(v -> openGalleryToSelectImages());

        btnSave.setOnClickListener(v -> {
            try {
                saveProduct();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(activity_add_product.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void openGalleryToSelectImages() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.setAction(Intent.ACTION_GET_CONTENT); // uses SAF - no READ_EXTERNAL_STORAGE needed on some devices
        galleryLauncher.launch(Intent.createChooser(intent, "Select images"));
    }

    private void saveProduct() {
        // Validate
        String name = etItemName.getText() != null ? etItemName.getText().toString().trim() : "";
        String size = spinnerSize.getText() != null ? spinnerSize.getText().toString().trim() : "";
        String priceStr = etPrice.getText() != null ? etPrice.getText().toString().trim() : "";
        String description = etDescription.getText() != null ? etDescription.getText().toString().trim() : "";

        if (TextUtils.isEmpty(name)) {
            etItemName.setError("Please enter item name");
            etItemName.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(priceStr)) {
            etPrice.setError("Please enter price");
            etPrice.requestFocus();
            return;
        }
        double price;
        try {
            price = Double.parseDouble(priceStr);
        } catch (NumberFormatException nfe) {
            etPrice.setError("Enter valid price");
            etPrice.requestFocus();
            return;
        }
        if (imageUriList.isEmpty()) {
            Toast.makeText(this, "Please select at least one image", Toast.LENGTH_SHORT).show();
            return;
        }

        // status
        int checkedStatusId = chipGroupStatus.getCheckedChipId();
        String status = "Available";
        if (checkedStatusId != -1) {
            Chip chipStatus = findViewById(checkedStatusId);
            if (chipStatus != null) status = chipStatus.getText().toString();
        }

        // colors (multi-select)
        List<String> colors = new ArrayList<>();
        for (int i = 0; i < chipGroupColor.getChildCount(); i++) {
            View child = chipGroupColor.getChildAt(i);
            if (child instanceof Chip) {
                Chip c = (Chip) child;
                if (c.isChecked()) colors.add(c.getText().toString());
            }
        }

        // category (single select)
        int checkedCatId = chipGroupCategory.getCheckedChipId();
        String category = "";
        if (checkedCatId != -1) {
            Chip chipCat = findViewById(checkedCatId);
            if (chipCat != null) category = chipCat.getText().toString();
        }

        // get current user
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "User not signed in", Toast.LENGTH_SHORT).show();
            return;
        }
        String uid = user.getUid();

        // prepare product document id
        String productId = UUID.randomUUID().toString();

        progressDialog.setMessage("Uploading images...");
        progressDialog.show();

        // upload images sequentially and collect URLs
        uploadedImageUrls.clear();
        uploadImageAtIndex(0, uid, productId, name, size, price, status, colors, category, description);
    }

    private void uploadImageAtIndex(int index,
                                    String uid,
                                    String productId,
                                    String name,
                                    String size,
                                    double price,
                                    String status,
                                    List<String> colors,
                                    String category,
                                    String description) {

        if (index >= imageUriList.size()) {
            // all uploaded -> save Firestore doc
            saveProductToFirestore(uid, productId, name, size, price, status, colors, category, description, uploadedImageUrls);
            return;
        }

        Uri fileUri = imageUriList.get(index);
        // storage path: users/{uid}/products/{productId}/img_{index}_{random}.jpg
        String fileName = "img_" + index + "_" + UUID.randomUUID().toString();
        StorageReference imgRef = storageRef.child("users/" + uid + "/products/" + productId + "/" + fileName);

        UploadTask uploadTask = imgRef.putFile(fileUri);
        uploadTask
                .addOnSuccessListener(taskSnapshot -> imgRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    uploadedImageUrls.add(uri.toString());
                    // continue with next
                    uploadImageAtIndex(index + 1, uid, productId, name, size, price, status, colors, category, description);
                }).addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(activity_add_product.this, "Failed to get download URL: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }))
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(activity_add_product.this, "Image upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void saveProductToFirestore(String uid,
                                        String productId,
                                        String name,
                                        String size,
                                        double price,
                                        String status,
                                        List<String> colors,
                                        String category,
                                        String description,
                                        List<String> imageUrls) {

        progressDialog.setMessage("Saving product data...");

        Map<String, Object> product = new HashMap<>();
        product.put("id", productId);
        product.put("userId", uid);
        product.put("name", name);
        product.put("size", size);
        product.put("price", price);
        product.put("status", status);
        product.put("colors", colors);
        product.put("category", category);
        product.put("description", description);
        product.put("imageUrls", imageUrls);
        product.put("createdAt", Timestamp.now());

        // Save under users/{uid}/products/{productId}
        DocumentReference docRef = firestore.collection("users").document(uid)
                .collection("products").document(productId);

        docRef.set(product)
                .addOnCompleteListener(task -> {
                    progressDialog.dismiss();
                    if (task.isSuccessful()) {
                        Toast.makeText(activity_add_product.this, "Product added successfully", Toast.LENGTH_SHORT).show();
                        clearForm();
                    } else {
                        Toast.makeText(activity_add_product.this, "Failed to save product", Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(activity_add_product.this, "Error saving product: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void clearForm() {
        etItemName.setText("");
        spinnerSize.setText("");
        etPrice.setText("");
        etDescription.setText("");
        chipGroupStatus.clearCheck();
        // set default status Available (if there's a chip with that text)
        for (int i = 0; i < chipGroupStatus.getChildCount(); i++) {
            View child = chipGroupStatus.getChildAt(i);
            if (child instanceof Chip) {
                Chip c = (Chip) child;
                if ("Available".equalsIgnoreCase(c.getText().toString())) {
                    c.setChecked(true);
                    break;
                }
            }
        }

        for (int i = 0; i < chipGroupColor.getChildCount(); i++) {
            View child = chipGroupColor.getChildAt(i);
            if (child instanceof Chip) ((Chip) child).setChecked(false);
        }

        for (int i = 0; i < chipGroupCategory.getChildCount(); i++) {
            View child = chipGroupCategory.getChildAt(i);
            if (child instanceof Chip) ((Chip) child).setChecked(false);
        }
        // reset selected images
        imageUriList.clear();
        uploadedImageUrls.clear();
    }
}

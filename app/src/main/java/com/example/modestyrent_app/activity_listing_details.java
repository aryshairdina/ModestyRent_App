// activity_listing_details.java (revised)
package com.example.modestyrent_app;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class activity_listing_details extends AppCompatActivity {

    private static final int MAX_MEDIA = 4; // ⬅️ Max media for edit page

    // Top bar
    private ImageView ld_backIcon;

    // Media slider (same style as add product)
    private ViewPager2 ld_mediaViewPager;
    private MediaPagerAdapter mediaPagerAdapter;
    private TextView ld_tvMediaCount;
    private Button ld_btnPickImage;

    // Form fields
    private Button ld_btnUpdate;
    private TextInputEditText ld_etName, ld_etSize, ld_etPrice, ld_etDescription;
    private ChipGroup ld_chipGroupStatus, ld_chipGroupCategory, ld_chipGroupColor;
    private Chip ld_chipAvailable, ld_chipReserved, ld_chipUnavailable;
    private Chip ld_chipCategoryKurung, ld_chipCategoryJubah, ld_chipCategoryKebaya;
    private Chip ld_chipColorWhite, ld_chipColorBlack, ld_chipColorNavy, ld_chipColorPink;
    private ProgressBar ld_progress;

    // Firebase
    private FirebaseAuth auth;
    private DatabaseReference productsRef;
    private StorageReference storageRootRef;

    private String productId;
    private String ownerUserId = null;

    // Media lists
    private final ArrayList<Uri> selectedMediaUris = new ArrayList<>();      // For preview in ViewPager (remote + local)
    private final ArrayList<Uri> newLocalMediaUris = new ArrayList<>();      // Only *new* local URIs
    private final ArrayList<String> existingImageUrls = new ArrayList<>();   // URLs originally in DB

    // Media picker launcher
    private ActivityResultLauncher<Intent> pickMediaLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_listing_details);

        // Firebase init
        auth = FirebaseAuth.getInstance();
        productsRef = FirebaseDatabase.getInstance().getReference("products");
        storageRootRef = FirebaseStorage.getInstance().getReference();

        // Bind views
        ld_backIcon = findViewById(R.id.ld_backIcon);

        ld_mediaViewPager = findViewById(R.id.ld_mediaViewPager);
        ld_tvMediaCount = findViewById(R.id.ld_tvMediaCount);
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

        // Back
        ld_backIcon.setOnClickListener(v -> finish());

        // Product ID from intent
        productId = getIntent().getStringExtra("productId");
        if (TextUtils.isEmpty(productId)) {
            Toast.makeText(this, "No product selected.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Setup media slider
        mediaPagerAdapter = new MediaPagerAdapter(this, selectedMediaUris);
        ld_mediaViewPager.setAdapter(mediaPagerAdapter);
        ld_mediaViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateMediaCountText();
            }
        });
        updateMediaCountText();

        // Media picker launcher
        pickMediaLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        handlePickedMedia(result.getData());
                    }
                }
        );

        // Disable until product owner is verified
        ld_btnPickImage.setEnabled(false);

        ld_btnPickImage.setOnClickListener(v -> openMediaPicker());
        ld_btnUpdate.setOnClickListener(v -> attemptUpdate());

        // Load product data
        loadProductAndPopulate();
    }

    // --- Media helpers ---

    private void updateMediaCountText() {
        int count = selectedMediaUris.size();
        if (count > 0) {
            ld_tvMediaCount.setVisibility(View.VISIBLE);
            ld_btnPickImage.setText("Change Photos/Video");
            int current = ld_mediaViewPager.getCurrentItem() + 1;
            ld_tvMediaCount.setText(String.format(Locale.getDefault(), "%d / %d", current, count));
        } else {
            ld_tvMediaCount.setVisibility(View.GONE);
            ld_btnPickImage.setText("Add Photos/Video");
        }
    }

    private void openMediaPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        pickMediaLauncher.launch(Intent.createChooser(intent, "Select Photos or Video (Max " + MAX_MEDIA + ")"));
    }

    /**
     * New selection REPLACES old media.
     */
    private void handlePickedMedia(Intent data) {
        // Clear previous selection and new-media tracking
        selectedMediaUris.clear();
        newLocalMediaUris.clear();

        ClipData clip = data.getClipData();
        int added = 0;

        if (clip != null) {
            int count = Math.min(clip.getItemCount(), MAX_MEDIA);
            for (int i = 0; i < count; i++) {
                ClipData.Item item = clip.getItemAt(i);
                if (item == null) continue;
                Uri uri = item.getUri();
                if (uri == null) continue;

                selectedMediaUris.add(uri);
                newLocalMediaUris.add(uri);
                added++;
            }
        } else {
            Uri uri = data.getData();
            if (uri != null) {
                selectedMediaUris.add(uri);
                newLocalMediaUris.add(uri);
                added++;
            }
        }

        mediaPagerAdapter.updateMedia(selectedMediaUris);
        if (!selectedMediaUris.isEmpty()) {
            ld_mediaViewPager.setCurrentItem(0, false);
        }
        updateMediaCountText();

        if (added == 0) {
            Toast.makeText(this, "No media selected", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(
                    this,
                    "Replaced with " + added + " new media item(s) (max " + MAX_MEDIA + ")",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    // --- Loading product ---

    private void setLoading(boolean loading) {
        ld_progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        ld_btnUpdate.setEnabled(!loading);
        ld_btnPickImage.setEnabled(!loading && ownerUserId != null);
    }

    private void loadProductAndPopulate() {
        setLoading(true);
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

                // Auth check
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

                // enable media picking now that ownerUserId is known
                ld_btnPickImage.setEnabled(true);

                // Basic fields
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

                if (name != null) ld_etName.setText(name);
                if (size != null) ld_etSize.setText(size);
                if (description != null) ld_etDescription.setText(description);
                if (price != null) ld_etPrice.setText(String.valueOf(price));

                // Status
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

                // Category
                if (category != null) {
                    String cat = category.toLowerCase().trim();
                    if (cat.contains("kurung")) ld_chipGroupCategory.check(ld_chipCategoryKurung.getId());
                    else if (cat.contains("jubah")) ld_chipGroupCategory.check(ld_chipCategoryJubah.getId());
                    else if (cat.contains("kebaya")) ld_chipGroupCategory.check(ld_chipCategoryKebaya.getId());
                    else ld_chipGroupCategory.clearCheck();
                }

                // Colors
                ld_chipColorWhite.setChecked(false);
                ld_chipColorBlack.setChecked(false);
                ld_chipColorNavy.setChecked(false);
                ld_chipColorPink.setChecked(false);

                if (snapshot.child("colors").exists()) {
                    for (DataSnapshot c : snapshot.child("colors").getChildren()) {
                        String color = c.getValue(String.class);
                        if (color == null) continue;
                        String lower = color.toLowerCase();
                        if (lower.contains("white")) ld_chipColorWhite.setChecked(true);
                        else if (lower.contains("black")) ld_chipColorBlack.setChecked(true);
                        else if (lower.contains("navy")) ld_chipColorNavy.setChecked(true);
                        else if (lower.contains("pink")) ld_chipColorPink.setChecked(true);
                    }
                }

                // Existing media URLs
                existingImageUrls.clear();
                selectedMediaUris.clear();
                newLocalMediaUris.clear();

                if (snapshot.child("imageUrls").exists()) {
                    for (DataSnapshot imgNode : snapshot.child("imageUrls").getChildren()) {
                        String url = imgNode.getValue(String.class);
                        if (!TextUtils.isEmpty(url)) {
                            existingImageUrls.add(url);
                            selectedMediaUris.add(Uri.parse(url)); // Glide can load from this
                        }
                    }
                } else if (snapshot.child("imageUrl").exists()) {
                    String singleUrl = snapshot.child("imageUrl").getValue(String.class);
                    if (!TextUtils.isEmpty(singleUrl)) {
                        existingImageUrls.add(singleUrl);
                        selectedMediaUris.add(Uri.parse(singleUrl));
                    }
                }

                mediaPagerAdapter.updateMedia(selectedMediaUris);
                updateMediaCountText();

                if (selectedMediaUris.isEmpty()) {
                    Toast.makeText(activity_listing_details.this, "No media. You can add photos/videos.", Toast.LENGTH_SHORT).show();
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

    // --- Update product ---

    private void attemptUpdate() {
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

        // If no new media picked -> keep existing imageUrls in DB
        if (newLocalMediaUris.isEmpty()) {
            productsRef.child(productId).updateChildren(updates).addOnCompleteListener(task -> {
                setLoading(false);
                if (task.isSuccessful()) {
                    Toast.makeText(activity_listing_details.this, "Product updated successfully.", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(activity_listing_details.this, "Failed to update product: " + task.getException(), Toast.LENGTH_LONG).show();
                }
            });
        } else {
            uploadNewMediaThenUpdate(updates);
        }
    }

    private void uploadNewMediaThenUpdate(Map<String, Object> updates) {
        if (ownerUserId == null) {
            setLoading(false);
            Toast.makeText(this, "Owner UID not determined. Try again.", Toast.LENGTH_LONG).show();
            return;
        }

        StorageReference rootRef = storageRootRef.child("products").child(ownerUserId);
        List<Task<Uri>> urlTasks = new ArrayList<>();

        for (Uri uri : newLocalMediaUris) {
            String filename = productId + "_" + UUID.randomUUID().toString();
            String extension = ".dat";

            String mimeType = getContentResolver().getType(uri);
            if (mimeType != null) {
                if (mimeType.contains("image")) extension = ".jpg";
                else if (mimeType.contains("video")) extension = ".mp4";
            }

            StorageReference fileRef = rootRef.child(filename + extension);
            UploadTask uploadTask = fileRef.putFile(uri);

            Task<Uri> urlTask = uploadTask.continueWithTask(task -> {
                if (!task.isSuccessful()) {
                    throw task.getException() != null ? task.getException() : new Exception("Upload failed");
                }
                return fileRef.getDownloadUrl();
            });

            urlTasks.add(urlTask);
        }

        Tasks.whenAllSuccess(urlTasks)
                .addOnSuccessListener(objects -> {
                    // ✅ Replace old URLs with only the new uploads
                    List<String> finalUrls = new ArrayList<>();
                    for (Object obj : objects) {
                        if (obj instanceof Uri) {
                            finalUrls.add(((Uri) obj).toString());
                        }
                    }

                    updates.put("imageUrls", finalUrls);

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
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(activity_listing_details.this, "Media upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}

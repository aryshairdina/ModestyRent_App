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

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
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
    private TextInputEditText etCustomColor;
    private TextInputLayout layoutCustomColor;

    private ChipGroup chipGroupStatus, chipGroupColor, chipGroupCategory;
    private Chip chipColorOther;

    private ViewPager2 mediaViewPager;
    private MediaPagerAdapter mediaPagerAdapter;
    private TextView tvMediaCount;

    private final ArrayList<Uri> selectedMediaUris = new ArrayList<>();
    private ActivityResultLauncher<Intent> pickMediaLauncher;

    private FirebaseAuth mAuth;
    private FirebaseStorage storage;
    private DatabaseReference realtimeDb;

    private ProgressDialog progressDialog;

    private static final int MAX_MEDIA = 8;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_product);

        mAuth = FirebaseAuth.getInstance();
        if (mAuth.getCurrentUser() == null) {
            startActivity(new Intent(this, activity_signin.class));
            finish();
            return;
        }

        storage = FirebaseStorage.getInstance();
        realtimeDb = FirebaseDatabase.getInstance().getReference();

        btnSelectImage = findViewById(R.id.btnSelectImage);
        btnSave = findViewById(R.id.btnSave);
        btnBack = findViewById(R.id.btnBack);

        etItemName = findViewById(R.id.etItemName);
        etPrice = findViewById(R.id.etPrice);
        spinnerSize = findViewById(R.id.spinnerSize);
        etDescription = findViewById(R.id.etDescription);

        chipGroupStatus = findViewById(R.id.chipGroupStatus);
        chipGroupColor = findViewById(R.id.chipGroupColor);
        chipGroupCategory = findViewById(R.id.chipGroupCategory);

        chipColorOther = findViewById(R.id.chipColorOther);
        etCustomColor = findViewById(R.id.etCustomColor);
        layoutCustomColor = findViewById(R.id.layoutCustomColor);

        mediaViewPager = findViewById(R.id.mediaViewPager);
        tvMediaCount = findViewById(R.id.tvMediaCount);

        mediaPagerAdapter = new MediaPagerAdapter(this, new ArrayList<>());
        mediaViewPager.setAdapter(mediaPagerAdapter);

        mediaViewPager.registerOnPageChangeCallback(
                new ViewPager2.OnPageChangeCallback() {
                    @Override
                    public void onPageSelected(int position) {
                        updateMediaCountText();
                    }
                });

        chipColorOther.setOnCheckedChangeListener((b, checked) -> {
            layoutCustomColor.setVisibility(checked ? View.VISIBLE : View.GONE);
            if (!checked) etCustomColor.setText("");
        });

        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);

        pickMediaLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                r -> {
                    if (r.getResultCode() == RESULT_OK && r.getData() != null) {
                        handlePickedMedia(r.getData());
                    }
                });

        btnSelectImage.setOnClickListener(v -> openMediaPicker());
        btnSave.setOnClickListener(v -> saveProduct());
        btnBack.setOnClickListener(v -> finish());

        updateMediaCountText();
    }

    private void updateMediaCountText() {
        int count = selectedMediaUris.size();
        if (count > 0) {
            tvMediaCount.setText((mediaViewPager.getCurrentItem() + 1) + " / " + count);
            tvMediaCount.setVisibility(View.VISIBLE);
            btnSelectImage.setVisibility(View.GONE);
        } else {
            tvMediaCount.setVisibility(View.GONE);
            btnSelectImage.setVisibility(View.VISIBLE);
        }
    }

    private void openMediaPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"image/*", "video/*"});
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        pickMediaLauncher.launch(Intent.createChooser(intent, "Select media"));
    }

    private void handlePickedMedia(Intent data) {
        selectedMediaUris.clear();

        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < Math.min(clip.getItemCount(), MAX_MEDIA); i++) {
                selectedMediaUris.add(clip.getItemAt(i).getUri());
            }
        } else if (data.getData() != null) {
            selectedMediaUris.add(data.getData());
        }

        mediaPagerAdapter.updateMedia(selectedMediaUris);
        updateMediaCountText();
    }

    private void saveProduct() {
        String name = etItemName.getText().toString().trim();
        String priceStr = etPrice.getText().toString().trim();
        String size = spinnerSize.getText().toString().trim();
        String desc = etDescription.getText().toString().trim();

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(priceStr)) {
            Toast.makeText(this, "Fill required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        double price = Double.parseDouble(priceStr);

        String status =
                ((Chip) findViewById(chipGroupStatus.getCheckedChipId()))
                        .getText().toString();

        String category =
                ((Chip) findViewById(chipGroupCategory.getCheckedChipId()))
                        .getText().toString();

        List<String> colors = new ArrayList<>();
        for (int i = 0; i < chipGroupColor.getChildCount(); i++) {
            View v = chipGroupColor.getChildAt(i);
            if (!(v instanceof Chip)) continue;

            Chip chip = (Chip) v;
            if (!chip.isChecked()) continue;

            if (chip.getId() == R.id.chipColorOther) {
                String custom = etCustomColor.getText().toString().trim();
                if (TextUtils.isEmpty(custom)) {
                    Toast.makeText(this, "Enter custom color", Toast.LENGTH_SHORT).show();
                    return;
                }
                colors.add(custom);
            } else {
                colors.add(chip.getText().toString());
            }
        }

        if (colors.isEmpty()) {
            Toast.makeText(this, "Select at least one color", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser user = mAuth.getCurrentUser();
        progressDialog.setMessage("Uploading product...");
        progressDialog.show();

        if (selectedMediaUris.isEmpty()) {
            createProduct(user.getUid(), name, price, size, status,
                    colors, category, desc, new ArrayList<>());
        } else {
            uploadMediaThenSave(user.getUid(), name, price, size,
                    status, colors, category, desc);
        }
    }

    private void uploadMediaThenSave(String uid, String name, double price,
                                     String size, String status,
                                     List<String> colors, String category,
                                     String desc) {

        StorageReference root = storage.getReference();
        List<Task<Uri>> tasks = new ArrayList<>();

        for (Uri uri : selectedMediaUris) {
            String ext = ".dat";
            String mime = getContentResolver().getType(uri);
            if (mime != null) {
                if (mime.startsWith("image")) ext = ".jpg";
                else if (mime.startsWith("video")) ext = ".mp4";
            }

            StorageReference ref =
                    root.child("products")
                            .child(uid)
                            .child(UUID.randomUUID() + ext);

            UploadTask upload = ref.putFile(uri);
            Task<Uri> urlTask = upload.continueWithTask(t -> {
                if (!t.isSuccessful()) throw t.getException();
                return ref.getDownloadUrl();
            });
            tasks.add(urlTask);
        }

        Tasks.whenAllSuccess(tasks)
                .addOnSuccessListener(res -> {
                    List<String> urls = new ArrayList<>();
                    for (Object o : res) urls.add(o.toString());

                    createProduct(uid, name, price, size,
                            status, colors, category, desc, urls);
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(this,
                            "Upload failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void createProduct(String uid, String name, double price,
                               String size, String status,
                               List<String> colors, String category,
                               String desc, List<String> imageUrls) {

        DatabaseReference ref = realtimeDb.child("products");
        String id = ref.push().getKey();

        Map<String, Object> product = new HashMap<>();
        product.put("id", id);
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

        ref.child(id).setValue(product)
                .addOnSuccessListener(v -> {
                    progressDialog.dismiss();
                    Toast.makeText(this,
                            "Product added successfully",
                            Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(this,
                            e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }
}

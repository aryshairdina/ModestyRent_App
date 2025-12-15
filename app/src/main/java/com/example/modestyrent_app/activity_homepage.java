package com.example.modestyrent_app;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.slider.RangeSlider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class activity_homepage extends AppCompatActivity {

    private static final String TAG = "activity_homepage";

    private TextView welcomeText;
    private FirebaseAuth mAuth;

    private RecyclerView recyclerProducts;
    private ProductAdapter adapter;

    private final List<Product> allProducts = new ArrayList<>();
    private DatabaseReference productsRef;

    private EditText searchBar;
    private ImageView btnFilter;

    // Filter state
    private String selectedCategory = "All";
    private String selectedSize = "All";
    private String selectedColor = "All";
    private String selectedPriceSort = "None";

    private float allProductsMinPrice = 0f;
    private float allProductsMaxPrice = 0f;

    // NOTIFICATION - FIXED: Use RelativeLayout.LayoutParams instead of ConstraintLayout
    private ImageView notificationIcon;
    private TextView notificationBadge;

    private float filterMinPrice = 0f;
    private float filterMaxPrice = 0f;

    // Store color per productId (from database "color" field)
    private final Map<String, String> productColorMap = new HashMap<>();

    // Top chips group
    private ChipGroup topCategoryGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_homepage);

        welcomeText = findViewById(R.id.welcomeText);
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        searchBar = findViewById(R.id.searchBar);
        btnFilter = findViewById(R.id.btnFilter);

        // INITIALIZE NOTIFICATION VIEWS - FIXED
        notificationIcon = findViewById(R.id.notificationIcon);
        notificationBadge = findViewById(R.id.notificationBadge); // Make sure this ID exists in XML

        if (notificationIcon != null) {
            notificationIcon.setOnClickListener(v -> {
                Intent intent = new Intent(this, activity_notifications.class);
                startActivity(intent);
            });
        }

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser != null) {
            welcomeText.setText("Welcome, " + currentUser.getEmail());

            // SETUP NOTIFICATION BADGE
            setupNotificationBadge(currentUser.getUid());

            String uid = currentUser.getUid();
            DatabaseReference userRef = FirebaseDatabase.getInstance()
                    .getReference("users")
                    .child(uid)
                    .child("fullName");

            userRef.get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    String name = task.getResult().getValue(String.class);
                    if (name != null && !name.isEmpty()) {
                        welcomeText.setText("Welcome, " + name + "!");
                    } else {
                        welcomeText.setText("Welcome, " + currentUser.getEmail() + "!");
                    }
                } else {
                    welcomeText.setText("Welcome, " + currentUser.getEmail() + "!");
                }
            });
        } else {
            Toast.makeText(this, "Please sign in first.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, activity_signin.class));
            finish();
            return;
        }

        if (bottomNav == null) {
            Toast.makeText(this, "BottomNavigationView not found (check layout id)", Toast.LENGTH_LONG).show();
            return;
        }

        Menu menu = bottomNav.getMenu();
        MenuItem homeItem = menu.findItem(R.id.nav_home);
        if (homeItem != null) {
            bottomNav.setSelectedItemId(R.id.nav_home);
        }
        bottomNav.setOnItemSelectedListener(this::handleNavItemSelected);

        setupProductGrid();
        setupSearchAndFilters();

        // --- NEW: wire top category chips to filters ---
        setupCategoryChips();
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (productsRef != null) {
                loadProducts();
            } else {
                setupProductGrid();
            }

            // REFRESH NOTIFICATION BADGE WHEN RETURNING TO HOMEPAGE
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null) {
                setupNotificationBadge(currentUser.getUid());
            }

        } catch (Exception e) {
            Log.w(TAG, "onResume refresh failed: " + e.getMessage(), e);
        }
    }

    private boolean handleNavItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_home) {
            return true;
        }
        if (id == R.id.nav_add_item) {
            startActivity(new Intent(this, activity_add_product.class));
            return true;
        }
        if (id == R.id.nav_profile) {
            startActivity(new Intent(this, activity_profile.class));
            return true;
        }
        if (id == R.id.nav_live) {
            startActivity(new Intent(this, activity_product_reels.class));
            return true;
        }
        if (id == R.id.nav_chat) {
            startActivity(new Intent(this, activity_chat_list.class));
            return true;
        }
        return true;
    }

    // ---------------- Search + Filter ----------------

    private void setupSearchAndFilters() {
        if (searchBar != null) {
            searchBar.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    applySearchAndFilters();
                }
            });
        }

        if (btnFilter != null) {
            btnFilter.setOnClickListener(v -> openFilterDialog());
        }
    }

    /**
     * Hook top category chips (the ChipGroup in activity_homepage.xml) so when user taps a chip
     * the selectedCategory value updates and filtering is immediately applied.
     */
    private void setupCategoryChips() {
        topCategoryGroup = findViewById(R.id.categoryGroup);
        if (topCategoryGroup == null) {
            Log.w(TAG, "Top category ChipGroup (categoryGroup) not found in layout.");
            return;
        }

        // Determine initial selectedCategory from the checked chip (if any)
        int checkedId = topCategoryGroup.getCheckedChipId();
        if (checkedId != View.NO_ID) {
            View v = topCategoryGroup.findViewById(checkedId);
            if (v instanceof Chip) {
                selectedCategory = ((Chip) v).getText().toString();
            } else {
                selectedCategory = "All";
            }
        } else {
            selectedCategory = "All";
        }

        // Ensure initial visual state (color the chips correctly at startup)
        updateTopChipsVisual(checkedId);

        // Single listener that updates visuals and triggers filtering
        topCategoryGroup.setOnCheckedChangeListener((group, checkedChipId) -> {
            // Update visual state for each chip
            updateTopChipsVisual(checkedChipId);

            // Update selectedCategory and apply filters
            selectedCategory = getSelectedChipText(group, "All");
            applySearchAndFilters();
        });
    }

    /**
     * Update chip background/text/stroke colors for top Category chips.
     * selectedChipId may be View.NO_ID (none selected) — in that case we mark "All" checked if present.
     */
    private void updateTopChipsVisual(int selectedChipId) {
        if (topCategoryGroup == null) return;

        // If nothing selected, try to select "All" chip by text as fallback
        if (selectedChipId == View.NO_ID) {
            for (int i = 0; i < topCategoryGroup.getChildCount(); i++) {
                View child = topCategoryGroup.getChildAt(i);
                if (child instanceof Chip) {
                    Chip chip = (Chip) child;
                    if ("All".equalsIgnoreCase(chip.getText().toString())) {
                        chip.setChecked(true); // this will trigger listener if already set, but safe at startup
                        selectedChipId = chip.getId();
                        break;
                    }
                }
            }
        }

        // Colors
        int colorPrimary = ContextCompat.getColor(this, R.color.primary);
        int colorSecondary = ContextCompat.getColor(this, R.color.secondary);
        ColorStateList textChecked = ContextCompat.getColorStateList(this, R.color.background); // text on primary
        ColorStateList textUnchecked = ContextCompat.getColorStateList(this, R.color.neutral);   // text on secondary
        int strokeChecked = ContextCompat.getColor(this, R.color.primary);
        int strokeUnchecked = ContextCompat.getColor(this, R.color.textcolor);

        // Apply colors to each chip
        for (int i = 0; i < topCategoryGroup.getChildCount(); i++) {
            View child = topCategoryGroup.getChildAt(i);
            if (child instanceof Chip) {
                Chip chip = (Chip) child;
                boolean isChecked = (chip.getId() == selectedChipId) || chip.isChecked();
                // background
                chip.setChipBackgroundColor(ColorStateList.valueOf(isChecked ? colorPrimary : colorSecondary));
                // text color (use ColorStateList for API compatibility)
                chip.setTextColor(isChecked ? textChecked : textUnchecked);
                // stroke
                chip.setChipStrokeColor(ColorStateList.valueOf(isChecked ? strokeChecked : strokeUnchecked));
            }
        }
    }


    private void openFilterDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this)
                .inflate(R.layout.dialog_filter_products, null, false);
        builder.setView(view);
        AlertDialog dialog = builder.create();

        ChipGroup categoryGroup = view.findViewById(R.id.filterCategoryGroup);
        ChipGroup sizeGroup = view.findViewById(R.id.filterSizeGroup);
        ChipGroup colorGroup = view.findViewById(R.id.filterColorGroup);
        ChipGroup priceSortGroup = view.findViewById(R.id.filterPriceSortGroup);

        TextView tvMinPrice = view.findViewById(R.id.tvMinPrice);
        TextView tvMaxPrice = view.findViewById(R.id.tvMaxPrice);
        RangeSlider priceRangeSlider = view.findViewById(R.id.priceRangeSlider);

        MaterialButton btnReset = view.findViewById(R.id.btnResetFilter);
        MaterialButton btnApply = view.findViewById(R.id.btnApplyFilter);
        TextView tvClearAll = view.findViewById(R.id.tvClearAll);

        // Price bounds from all products
        float rawMinAll = allProductsMinPrice;
        float rawMaxAll = allProductsMaxPrice;

        if (rawMinAll <= 0 || rawMaxAll <= 0 || rawMinAll >= rawMaxAll) {
            rawMinAll = 0f;
            rawMaxAll = 1000f;
        }

        final float minAll = rawMinAll;
        final float maxAll = rawMaxAll;

        priceRangeSlider.setValueFrom(minAll);
        priceRangeSlider.setValueTo(maxAll);

        float currentMin = (filterMinPrice <= 0) ? minAll : filterMinPrice;
        float currentMax = (filterMaxPrice <= 0) ? maxAll : filterMaxPrice;
        if (currentMin < minAll) currentMin = minAll;
        if (currentMax > maxAll) currentMax = maxAll;

        List<Float> initialValues = new ArrayList<>();
        initialValues.add(currentMin);
        initialValues.add(currentMax);
        priceRangeSlider.setValues(initialValues);

        tvMinPrice.setText(String.format(Locale.getDefault(), "RM %.0f", currentMin));
        tvMaxPrice.setText(String.format(Locale.getDefault(), "RM %.0f", currentMax));

        priceRangeSlider.addOnChangeListener((slider, value, fromUser) -> {
            List<Float> values = slider.getValues();
            float vMin = values.get(0);
            float vMax = values.get(1);
            tvMinPrice.setText(String.format(Locale.getDefault(), "RM %.0f", vMin));
            tvMaxPrice.setText(String.format(Locale.getDefault(), "RM %.0f", vMax));
        });

        // restore chip selection
        restoreChipSelectionByText(categoryGroup, selectedCategory);
        restoreChipSelectionByText(sizeGroup, selectedSize);
        restoreChipSelectionByText(colorGroup, selectedColor);

        switch (selectedPriceSort) {
            case "LowToHigh":
                checkChipById(priceSortGroup, R.id.chipPriceSortLowHigh);
                break;
            case "HighToLow":
                checkChipById(priceSortGroup, R.id.chipPriceSortHighLow);
                break;
            default:
                checkChipById(priceSortGroup, R.id.chipPriceSortNone);
                break;
        }

        // reset / clear
        View.OnClickListener resetListener = v -> {
            selectedCategory = "All";
            selectedSize = "All";
            selectedColor = "All";
            selectedPriceSort = "None";

            checkChipById(categoryGroup, R.id.chipCategoryAll);
            checkChipById(sizeGroup, R.id.chipSizeAll);
            checkChipById(colorGroup, R.id.chipColorAll);
            checkChipById(priceSortGroup, R.id.chipPriceSortNone);

            List<Float> resetValues = new ArrayList<>();
            resetValues.add(minAll);
            resetValues.add(maxAll);
            priceRangeSlider.setValues(resetValues);

            tvMinPrice.setText(String.format(Locale.getDefault(), "RM %.0f", minAll));
            tvMaxPrice.setText(String.format(Locale.getDefault(), "RM %.0f", maxAll));

            filterMinPrice = minAll;
            filterMaxPrice = maxAll;
        };
        btnReset.setOnClickListener(resetListener);
        tvClearAll.setOnClickListener(resetListener);

        // apply
        btnApply.setOnClickListener(v -> {
            selectedCategory = getSelectedChipText(categoryGroup, "All");
            selectedSize = getSelectedChipText(sizeGroup, "All");
            selectedColor = getSelectedChipText(colorGroup, "All");

            int checkedSortId = priceSortGroup.getCheckedChipId();
            if (checkedSortId == R.id.chipPriceSortLowHigh) {
                selectedPriceSort = "LowToHigh";
            } else if (checkedSortId == R.id.chipPriceSortHighLow) {
                selectedPriceSort = "HighToLow";
            } else {
                selectedPriceSort = "None";
            }

            List<Float> values = priceRangeSlider.getValues();
            if (values.size() >= 2) {
                filterMinPrice = values.get(0);
                filterMaxPrice = values.get(1);
            } else {
                filterMinPrice = minAll;
                filterMaxPrice = maxAll;
            }

            // If top chips exist, keep them in sync: check matching chip on top (optional)
            syncTopCategorySelectionWithDialog(selectedCategory);

            applySearchAndFilters();
            dialog.dismiss();
        });

        dialog.show();
    }

    /**
     * Optional helper: when user applies a category via the dialog, keep top chips in sync.
     */
    private void syncTopCategorySelectionWithDialog(String categoryText) {
        if (topCategoryGroup == null || categoryText == null) return;
        for (int i = 0; i < topCategoryGroup.getChildCount(); i++) {
            View child = topCategoryGroup.getChildAt(i);
            if (child instanceof Chip) {
                Chip chip = (Chip) child;
                if (categoryText.equalsIgnoreCase(chip.getText().toString())) {
                    chip.setChecked(true);
                    return;
                }
            }
        }
        // fallback if not found: check "All" chip if exists
        for (int i = 0; i < topCategoryGroup.getChildCount(); i++) {
            View child = topCategoryGroup.getChildAt(i);
            if (child instanceof Chip) {
                Chip chip = (Chip) child;
                if ("All".equalsIgnoreCase(chip.getText().toString())) {
                    chip.setChecked(true);
                    return;
                }
            }
        }
    }

    private void restoreChipSelectionByText(ChipGroup group, String text) {
        if (group == null || text == null) return;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof Chip) {
                Chip chip = (Chip) child;
                if (text.equalsIgnoreCase(chip.getText().toString())) {
                    chip.setChecked(true);
                    return;
                }
            }
        }
    }

    private void checkChipById(ChipGroup group, int chipId) {
        if (group == null) return;
        Chip chip = group.findViewById(chipId);
        if (chip != null) chip.setChecked(true);
    }

    private String getSelectedChipText(ChipGroup group, String defaultValue) {
        if (group == null) return defaultValue;
        int id = group.getCheckedChipId();
        if (id == View.NO_ID) return defaultValue;
        View v = group.findViewById(id);
        if (v instanceof Chip) {
            CharSequence t = ((Chip) v).getText();
            if (t != null) return t.toString();
        }
        return defaultValue;
    }

    private void applySearchAndFilters() {
        String search = "";
        if (searchBar != null && searchBar.getText() != null) {
            search = searchBar.getText().toString().trim().toLowerCase(Locale.getDefault());
        }

        List<Product> filtered = new ArrayList<>();
        for (Product p : allProducts) {
            if (p == null) continue;

            // search
            if (!search.isEmpty()) {
                String name = p.getName() != null ? p.getName().toLowerCase(Locale.getDefault()) : "";
                String desc = p.getDescription() != null ? p.getDescription().toLowerCase(Locale.getDefault()) : "";
                String cat = p.getCategory() != null ? p.getCategory().toLowerCase(Locale.getDefault()) : "";
                if (!name.contains(search) && !desc.contains(search) && !cat.contains(search)) {
                    continue;
                }
            }

            // category filter
            if (!"All".equalsIgnoreCase(selectedCategory)) {
                String cat = p.getCategory() != null ? p.getCategory() : "";
                if (!cat.equalsIgnoreCase(selectedCategory)) {
                    continue;
                }
            }

            // size filter
            if (!"All".equalsIgnoreCase(selectedSize)) {
                String size = p.getSize() != null ? p.getSize() : "";
                if (!size.equalsIgnoreCase(selectedSize)) {
                    continue;
                }
            }

            // color filter (using productColorMap instead of getColor())
            if (!"All".equalsIgnoreCase(selectedColor)) {
                String color = "";
                if (p.getId() != null && productColorMap.containsKey(p.getId())) {
                    color = productColorMap.get(p.getId());
                }
                if (color == null) color = "";
                if (!color.equalsIgnoreCase(selectedColor)) {
                    continue;
                }
            }

            // price range
            double price = p.getPrice();
            if (filterMinPrice > 0 || filterMaxPrice > 0) {
                if (price < filterMinPrice || price > filterMaxPrice) {
                    continue;
                }
            }

            filtered.add(p);
        }

        // sort
        if ("LowToHigh".equals(selectedPriceSort)) {
            Collections.sort(filtered, Comparator.comparingDouble(Product::getPrice));
        } else if ("HighToLow".equals(selectedPriceSort)) {
            Collections.sort(filtered, (a, b) -> Double.compare(b.getPrice(), a.getPrice()));
        }

        if (adapter != null) {
            adapter.setList(filtered);
        }
    }

    // ---------------- Product grid ----------------

    private void setupProductGrid() {
        recyclerProducts = findViewById(R.id.recyclerProducts);
        if (recyclerProducts == null) {
            Toast.makeText(this, "RecyclerView (recyclerProducts) not found in layout.", Toast.LENGTH_LONG).show();
            return;
        }

        recyclerProducts.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new ProductAdapter(this, new ArrayList<>());
        recyclerProducts.setAdapter(adapter);

        DatabaseReference rootRef = FirebaseDatabase.getInstance().getReference();
        DatabaseReference candidate = rootRef.child("ModestyRent - App").child("products");

        candidate.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                productsRef = candidate;
            } else {
                productsRef = rootRef.child("products");
            }
            loadProducts();
        }).addOnFailureListener(e -> {
            productsRef = rootRef.child("products");
            loadProducts();
        });
    }

    private void loadProducts() {
        if (productsRef == null) {
            Toast.makeText(this, "Products DB reference not available.", Toast.LENGTH_SHORT).show();
            return;
        }

        String currentUid = null;
        try {
            FirebaseUser cu = mAuth.getCurrentUser();
            if (cu != null) currentUid = cu.getUid();
        } catch (Exception ignored) {}
        final String finalCurrentUid = currentUid;

        productsRef.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                Toast.makeText(this, "Failed loading products: " +
                                (task.getException() != null ? task.getException().getMessage() : "unknown"),
                        Toast.LENGTH_LONG).show();
                return;
            }

            DataSnapshot snapshot = task.getResult();
            allProducts.clear();
            productColorMap.clear();

            float minPrice = Float.MAX_VALUE;
            float maxPrice = Float.MIN_VALUE;

            if (snapshot.exists()) {
                for (DataSnapshot child : snapshot.getChildren()) {
                    try {
                        Product p = child.getValue(Product.class);
                        if (p == null) p = new Product();

                        if (p.getId() == null || p.getId().isEmpty()) {
                            p.setId(child.getKey());
                        }

                        String ownerId = p.getUserId();
                        if (ownerId == null || ownerId.isEmpty()) {
                            if (child.child("userId").exists()) {
                                Object v = child.child("userId").getValue();
                                ownerId = v != null ? String.valueOf(v) : null;
                            } else if (child.child("userid").exists()) {
                                Object v = child.child("userid").getValue();
                                ownerId = v != null ? String.valueOf(v) : null;
                            } else if (child.child("userUID").exists()) {
                                Object v = child.child("userUID").getValue();
                                ownerId = v != null ? String.valueOf(v) : null;
                            }
                            if (ownerId != null) p.setUserId(ownerId);
                        }

                        if (finalCurrentUid != null && ownerId != null && finalCurrentUid.equals(ownerId)) {
                            continue; // don't show own products
                        }

                        String status = p.getStatus();
                        if (status == null && child.child("status").exists()) {
                            Object sObj = child.child("status").getValue();
                            status = sObj != null ? String.valueOf(sObj) : null;
                            p.setStatus(status);
                        }
                        if (status == null) continue;
                        if (!"available".equalsIgnoreCase(status.trim())) continue;

                        if (p.getPrice() == 0.0) {
                            if (child.child("price / day").exists()) {
                                Object priceObj = child.child("price / day").getValue();
                                if (priceObj != null) {
                                    try {
                                        p.setPrice(Double.parseDouble(String.valueOf(priceObj)));
                                    } catch (NumberFormatException ignored) {}
                                }
                            } else if (child.child("price").exists()) {
                                Object priceObj = child.child("price").getValue();
                                if (priceObj != null) {
                                    try {
                                        p.setPrice(Double.parseDouble(String.valueOf(priceObj)));
                                    } catch (NumberFormatException ignored) {}
                                }
                            }
                        }

                        // image urls fallback
                        if (p.getImageUrls() == null || p.getImageUrls().isEmpty()) {
                            if (child.child("imageUrls").exists()) {
                                ArrayList<String> urls = new ArrayList<>();
                                for (DataSnapshot img : child.child("imageUrls").getChildren()) {
                                    Object v = img.getValue();
                                    if (v != null) urls.add(String.valueOf(v));
                                }
                                if (!urls.isEmpty()) p.setImageUrls(urls);
                            } else if (child.child("imageUrl").exists()) {
                                Object v = child.child("imageUrl").getValue();
                                if (v != null) {
                                    ArrayList<String> urls = new ArrayList<>();
                                    urls.add(String.valueOf(v));
                                    p.setImageUrls(urls);
                                }
                            } else if (child.child("image").exists()) {
                                Object v = child.child("image").getValue();
                                if (v != null) {
                                    ArrayList<String> urls = new ArrayList<>();
                                    urls.add(String.valueOf(v));
                                    p.setImageUrls(urls);
                                }
                            }
                        }

                        // Read color from DB "color" field and store in map
                        String colorDb = child.child("color").getValue(String.class);
                        if (colorDb != null && p.getId() != null) {
                            productColorMap.put(p.getId(), colorDb);
                        }

                        allProducts.add(p);

                        float price = (float) p.getPrice();
                        if (price > 0) {
                            if (price < minPrice) minPrice = price;
                            if (price > maxPrice) maxPrice = price;
                        }

                    } catch (Exception ex) {
                        Log.e(TAG, "Skipping product node due to error", ex);
                    }
                }
            }

            if (minPrice == Float.MAX_VALUE || maxPrice == Float.MIN_VALUE) {
                allProductsMinPrice = 0f;
                allProductsMaxPrice = 1000f;
            } else {
                allProductsMinPrice = minPrice;
                allProductsMaxPrice = maxPrice;
            }

            if (filterMinPrice == 0f && filterMaxPrice == 0f) {
                filterMinPrice = allProductsMinPrice;
                filterMaxPrice = allProductsMaxPrice;
            }

            applySearchAndFilters();

            if (allProducts.isEmpty()) {
                Toast.makeText(this, "No available products found.", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Error loading products: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    // ============== NOTIFICATION METHODS ==============

    private void setupNotificationBadge(String userId) {
        if (userId == null) return;

        DatabaseReference notificationsRef = FirebaseDatabase.getInstance()
                .getReference("notifications")
                .child(userId);

        notificationsRef.orderByChild("read").equalTo(false)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        long unreadCount = snapshot.getChildrenCount();
                        updateNotificationBadge(unreadCount);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Failed to load notifications: " + error.getMessage());
                    }
                });
    }

    private void updateNotificationBadge(long unreadCount) {
        runOnUiThread(() -> {
            if (notificationBadge == null) {
                // Try to find it in layout
                notificationBadge = findViewById(R.id.notificationBadge);

                // If still null, create it programmatically
                if (notificationBadge == null && notificationIcon != null) {
                    createNotificationBadgeProgrammatically();
                }
            }

            if (notificationBadge != null) {
                if (unreadCount > 0) {
                    notificationBadge.setVisibility(View.VISIBLE);
                    if (unreadCount > 99) {
                        notificationBadge.setText("99+");
                    } else {
                        notificationBadge.setText(String.valueOf(unreadCount));
                    }
                } else {
                    notificationBadge.setVisibility(View.GONE);
                }
            }

            // Update icon color
            if (notificationIcon != null) {
                if (unreadCount > 0) {
                    notificationIcon.setColorFilter(ContextCompat.getColor(this, R.color.notification_important));
                    notificationIcon.setImageResource(R.drawable.ic_notifications); // Active icon
                } else {
                    notificationIcon.setColorFilter(ContextCompat.getColor(this, R.color.primary));
                    notificationIcon.setImageResource(R.drawable.ic_notifications); // Normal icon
                }
            }
        });
    }

    private void createNotificationBadgeProgrammatically() {
        if (notificationIcon == null) return;

        // Get parent layout
        ViewGroup parent = (ViewGroup) notificationIcon.getParent();
        if (parent == null) return;

        // Create badge
        notificationBadge = new TextView(this);
        notificationBadge.setId(View.generateViewId());

        // Create background drawable programmatically
        GradientDrawable badgeBackground = new GradientDrawable();
        badgeBackground.setShape(GradientDrawable.OVAL);
        badgeBackground.setColor(ContextCompat.getColor(this, R.color.notification_important));
        badgeBackground.setStroke(2, ContextCompat.getColor(this, R.color.background));

        notificationBadge.setBackground(badgeBackground);
        notificationBadge.setTextColor(Color.WHITE);
        notificationBadge.setTextSize(10);
        notificationBadge.setGravity(android.view.Gravity.CENTER);
        notificationBadge.setPadding(4, 2, 4, 2);
        notificationBadge.setMinWidth(20);
        notificationBadge.setMinHeight(20);

        // Position it on top-right of icon using RelativeLayout.LayoutParams
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );

        // Check if parent is RelativeLayout
        if (parent instanceof RelativeLayout) {
            params.addRule(RelativeLayout.ALIGN_TOP, notificationIcon.getId());
            params.addRule(RelativeLayout.ALIGN_RIGHT, notificationIcon.getId());
            params.topMargin = -8; // Use topMargin instead of params.topMargin
            params.rightMargin = -8; // Use rightMargin instead of params.endMargin

            notificationBadge.setLayoutParams(params);
            notificationBadge.setVisibility(View.GONE);

            // Add to parent
            parent.addView(notificationBadge);
        } else {
            // Fallback: Use simple positioning for other layouts
            params = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(-8, -8, 0, 0); // Top-left margin

            ViewGroup.MarginLayoutParams marginParams = new ViewGroup.MarginLayoutParams(params);
            marginParams.setMargins(-8, -8, 0, 0);
            notificationBadge.setLayoutParams(marginParams);
            notificationBadge.setVisibility(View.GONE);

            // Wrap icon and badge in a FrameLayout
            android.widget.FrameLayout container = new android.widget.FrameLayout(this);
            FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
            );

            // Replace icon with container
            int index = parent.indexOfChild(notificationIcon);
            parent.removeView(notificationIcon);

            container.addView(notificationIcon);
            container.addView(notificationBadge);

            parent.addView(container, index, containerParams);
        }
    }

    // ---------------- Adapter ----------------

    private static class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.VH> {
        private final Context ctx;
        private List<Product> list;

        private final FirebaseAuth auth = FirebaseAuth.getInstance();
        private final DatabaseReference usersRootRef = FirebaseDatabase.getInstance().getReference("users");
        private final DatabaseReference globalRootRef = FirebaseDatabase.getInstance().getReference();
        private String uid;
        private final Set<String> likedIds = new HashSet<>();

        ProductAdapter(Context ctx, List<Product> list) {
            this.ctx = ctx;
            this.list = list != null ? list : new ArrayList<>();

            FirebaseUser u = auth.getCurrentUser();
            if (u != null) {
                uid = u.getUid();
                loadUserLikes();
            } else {
                uid = null;
            }
        }

        void setList(List<Product> list) {
            this.list = list != null ? list : new ArrayList<>();
            notifyDataSetChanged();
        }

        private void loadUserLikes() {
            if (uid == null) return;
            DatabaseReference likesRef = usersRootRef.child(uid).child("likes");
            likesRef.get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    likedIds.clear();
                    DataSnapshot snap = task.getResult();
                    for (DataSnapshot child : snap.getChildren()) {
                        likedIds.add(child.getKey());
                    }
                    try {
                        ((android.app.Activity) ctx).runOnUiThread(this::notifyDataSetChanged);
                    } catch (Exception ignored) {
                        notifyDataSetChanged();
                    }
                }
            });
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(ctx).inflate(R.layout.product_card, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Product p = list.get(position);

            String title = (p.getName() != null && !p.getName().isEmpty()) ? p.getName() : "No title";
            if (holder.tvTitle != null) holder.tvTitle.setText(title);

            double priceVal = p.getPrice();
            String priceText = priceVal > 0 ? String.format(Locale.getDefault(), "%.2f", priceVal) : "-";
            if (holder.tvPrice != null) holder.tvPrice.setText(priceText);
            if (holder.tvPerDay != null) holder.tvPerDay.setVisibility(View.VISIBLE);

            String img = null;
            if (p.getImageUrls() != null && !p.getImageUrls().isEmpty()) img = p.getImageUrls().get(0);
            if (holder.imgProduct != null) {
                if (img != null && !img.isEmpty()) {
                    Glide.with(ctx)
                            .load(img)
                            .centerCrop()
                            .placeholder(R.drawable.ic_launcher_foreground)
                            .into(holder.imgProduct);
                } else {
                    holder.imgProduct.setImageResource(R.drawable.ic_launcher_foreground);
                }
            }

            boolean isLiked = p.getId() != null && likedIds.contains(p.getId());
            if (holder.btnLove != null) {
                holder.btnLove.setImageResource(isLiked ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
                holder.btnLove.setOnClickListener(v -> {
                    if (auth.getCurrentUser() == null) {
                        Toast.makeText(ctx, "Please sign in to like products", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    toggleLike(p);
                });
            }

            holder.itemView.setOnClickListener(v -> {
                Context c = v.getContext();
                if (p.getId() != null && !p.getId().isEmpty()) {
                    Intent i = new Intent(c, activity_product_details.class);
                    i.putExtra("productId", p.getId());
                    c.startActivity(i);
                } else {
                    Toast.makeText(c, "Product id not available", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public int getItemCount() {
            return list != null ? list.size() : 0;
        }

        private void toggleLike(Product product) {
            if (uid == null || product == null || product.getId() == null) return;

            String pid = product.getId();
            DatabaseReference likeRef = usersRootRef.child(uid).child("likes").child(pid);
            DatabaseReference inverseRef = globalRootRef.child("likes").child(pid).child(uid);

            if (likedIds.contains(pid)) {
                likeRef.removeValue().addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        likedIds.remove(pid);
                        notifyChangedForProduct(pid);
                        Toast.makeText(ctx, "Removed from My Likes", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(ctx, "Failed to remove like", Toast.LENGTH_SHORT).show();
                    }
                });
                inverseRef.removeValue();
            } else {
                java.util.Map<String, Object> payload = new java.util.HashMap<>();
                payload.put("addedAt", System.currentTimeMillis());
                payload.put("productId", pid);
                payload.put("name", product.getName() != null ? product.getName() : "");
                payload.put("price", product.getPrice());
                String image0 = (product.getImageUrls() != null && !product.getImageUrls().isEmpty())
                        ? product.getImageUrls().get(0) : "";
                payload.put("image", image0);

                likeRef.setValue(payload).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        likedIds.add(pid);
                        notifyChangedForProduct(pid);
                        Toast.makeText(ctx, "Added to My Likes", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(ctx, "Failed to add like", Toast.LENGTH_SHORT).show();
                    }
                });

                inverseRef.setValue(true);
            }
        }

        private void notifyChangedForProduct(String productId) {
            for (int i = 0; i < list.size(); i++) {
                Product p = list.get(i);
                if (p != null && productId.equals(p.getId())) {
                    final int idx = i;
                    try {
                        ((android.app.Activity) ctx).runOnUiThread(() -> notifyItemChanged(idx));
                    } catch (Exception ignored) {
                        notifyItemChanged(idx);
                    }
                    break;
                }
            }
        }

        static class VH extends RecyclerView.ViewHolder {
            ImageView imgProduct;
            TextView tvTitle, tvPrice, tvPerDay;
            ImageButton btnLove;

            VH(@NonNull View itemView) {
                super(itemView);
                try { imgProduct = itemView.findViewById(R.id.imgProduct); } catch (Exception e) { imgProduct = null; }
                try { tvTitle = itemView.findViewById(R.id.tvTitle); } catch (Exception e) { tvTitle = null; }
                try { tvPrice = itemView.findViewById(R.id.tvPrice); } catch (Exception e) { tvPrice = null; }
                try { tvPerDay = itemView.findViewById(R.id.tvPerDay); } catch (Exception e) { tvPerDay = null; }
                try { btnLove = itemView.findViewById(R.id.btnLove); } catch (Exception e) { btnLove = null; }
            }
        }
    }
}
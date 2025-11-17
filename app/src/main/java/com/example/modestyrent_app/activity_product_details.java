package com.example.modestyrent_app;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.DatabaseReference;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class activity_product_details extends AppCompatActivity {

    public static final String EXTRA_PRODUCT_ID = "productId";

    private ViewPager2 viewPagerImages;
    private TabLayout tabLayoutIndicator;
    private TextView tvItemTitle, tvShortDesc, tvCategory, tvSize, tvPricePerDay,
            tvOwnerName, tvFromDate, tvToDate, tvDaysCount, tvTotalPrice, tvOwnerAvatar;
    private CalendarView calendarAvailability;
    private Button btnBook, btnChatOwner, btnViewProfile;
    private FloatingActionButton fabChat;

    private String productId;
    private Product product;
    private long startDateMillis = -1;
    private long endDateMillis = -1;
    private double unitPrice = 0.0;
    private ArrayList<String> imageUrls = new ArrayList<>();
    private String ownerId = "";

    private DatabaseReference productsRef;
    private DatabaseReference usersRef;

    private boolean waitingForEnd = false; // if user tapped start already

    private final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_details);

        viewPagerImages = findViewById(R.id.viewPagerImages);
        tabLayoutIndicator = findViewById(R.id.tabLayoutIndicator);
        tvItemTitle = findViewById(R.id.tvItemTitle);
        tvShortDesc = findViewById(R.id.tvShortDesc);
        tvCategory = findViewById(R.id.tvCategory);
        tvSize = findViewById(R.id.tvSize);
        tvPricePerDay = findViewById(R.id.tvPricePerDay);
        tvOwnerName = findViewById(R.id.tvOwnerName);
        tvOwnerAvatar = findViewById(R.id.tvOwnerAvatar);
        calendarAvailability = findViewById(R.id.calendarAvailability);
        btnBook = findViewById(R.id.btnBook);
        btnChatOwner = findViewById(R.id.btnChatOwner);
        btnViewProfile = findViewById(R.id.btnViewProfile);
        fabChat = findViewById(R.id.fabChat);
        tvFromDate = findViewById(R.id.tvFromDate);
        tvToDate = findViewById(R.id.tvToDate);
        tvDaysCount = findViewById(R.id.tvDaysCount);
        tvTotalPrice = findViewById(R.id.tvTotalPrice);

        productId = getIntent().getStringExtra(EXTRA_PRODUCT_ID);
        if (TextUtils.isEmpty(productId)) {
            Toast.makeText(this, "Product not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ImageView backIcon = findViewById(R.id.backIcon);
        backIcon.setOnClickListener(v -> finish());

        FirebaseDatabase database = FirebaseDatabase.getInstance();
        productsRef = database.getReference("products").child(productId);
        usersRef = database.getReference("users");

        loadProduct();

        // Calendar taps: first tap = start, second tap = end (or overwrite)
        calendarAvailability.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            // month is 0-based
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(year, month, dayOfMonth, 0, 0, 0);
            long tapped = cal.getTimeInMillis();

            if (!waitingForEnd) {
                // choose start
                startDateMillis = tapped;
                endDateMillis = -1;
                waitingForEnd = true;
                tvFromDate.setText(DATE_FORMAT.format(startDateMillis));
                tvToDate.setText("—");
                tvDaysCount.setText("0");
                tvTotalPrice.setText("RM 0");
                Toast.makeText(activity_product_details.this, "Start date selected. Now tap end date.", Toast.LENGTH_SHORT).show();
            } else {
                // choose end
                endDateMillis = tapped;
                // if user chose end before start, swap
                if (endDateMillis < startDateMillis) {
                    long tmp = startDateMillis;
                    startDateMillis = endDateMillis;
                    endDateMillis = tmp;
                }
                waitingForEnd = false;
                tvFromDate.setText(DATE_FORMAT.format(startDateMillis));
                tvToDate.setText(DATE_FORMAT.format(endDateMillis));
                updateEstimateFromRange();
            }
        });

        btnBook.setOnClickListener(v -> {
            if (startDateMillis <= 0 || endDateMillis <= 0) {
                Toast.makeText(activity_product_details.this, "Please select start and end dates", Toast.LENGTH_SHORT).show();
                return;
            }
            int days = calculateDaysInclusive(startDateMillis, endDateMillis);
            Intent intent = new Intent(activity_product_details.this, activity_checkout.class);
            intent.putExtra("productId", productId);
            intent.putExtra("productName", product != null ? product.getName() : "");
            intent.putExtra("ownerId", ownerId);
            intent.putExtra("startDateMillis", startDateMillis);
            intent.putExtra("endDateMillis", endDateMillis);
            intent.putExtra("days", days);
            intent.putExtra("unitPrice", unitPrice);
            startActivity(intent);
        });

        btnChatOwner.setOnClickListener(v -> openChatWithOwner());
        fabChat.setOnClickListener(v -> openChatWithOwner());

        btnViewProfile.setOnClickListener(v -> {
            Intent i = new Intent(activity_product_details.this, activity_owner_profile.class);
            i.putExtra("ownerId", ownerId);
            startActivity(i);
        });
    }

    private void loadProduct() {
        productsRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                DataSnapshot snap = task.getResult();
                product = snap.getValue(Product.class);
                if (product != null) {
                    tvItemTitle.setText(product.getName() != null ? product.getName() : "");
                    tvShortDesc.setText(product.getDescription() != null ? product.getDescription() : "");
                    tvCategory.setText((product.getCategory() != null ? product.getCategory() : "") + " • " + (product.getSize() != null ? product.getSize() : ""));
                    tvSize.setText(product.getSize() != null ? product.getSize() : "—");

                    unitPrice = product.getPrice();
                    tvPricePerDay.setText(String.format(Locale.US, "RM %.2f / day", unitPrice));
                    tvTotalPrice.setText("RM 0");

                    ownerId = product.getUserId() != null ? product.getUserId() : "";

                    if (product.getImageUrls() != null) {
                        imageUrls.clear();
                        imageUrls.addAll(product.getImageUrls());
                    }
                    setupImageSlider();

                    // Load owner data from Firebase
                    loadOwnerData();
                }
            } else {
                Toast.makeText(activity_product_details.this, "Failed to load product", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadOwnerData() {
        if (!ownerId.isEmpty()) {
            usersRef.child(ownerId).get().addOnCompleteListener(uTask -> {
                if (uTask.isSuccessful() && uTask.getResult().exists()) {
                    DataSnapshot usnap = uTask.getResult();
                    String ownerName = usnap.child("fullName").getValue(String.class);

                    // Set owner name
                    if (ownerName != null && !ownerName.isEmpty()) {
                        tvOwnerName.setText(ownerName);

                        // Create avatar with first two letters
                        createAvatarFromName(ownerName);
                    } else {
                        // Fallback if fullName doesn't exist, try name field
                        ownerName = usnap.child("name").getValue(String.class);
                        if (ownerName != null && !ownerName.isEmpty()) {
                            tvOwnerName.setText(ownerName);
                            createAvatarFromName(ownerName);
                        } else {
                            tvOwnerName.setText("Owner");
                            createAvatarFromName("Owner");
                        }
                    }
                } else {
                    // Fallback if user data not found
                    tvOwnerName.setText("Owner");
                    createAvatarFromName("Owner");
                }
            });
        } else {
            // Fallback if no owner ID
            tvOwnerName.setText("Owner");
            createAvatarFromName("Owner");
        }
    }

    private void createAvatarFromName(String fullName) {
        // Get first two letters from the name
        String avatarText = getInitialsFromName(fullName);

        // Set the text to the avatar TextView
        tvOwnerAvatar.setText(avatarText);
    }

    private String getInitialsFromName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return "OO"; // Owner Initials
        }

        String[] nameParts = fullName.trim().split("\\s+");

        if (nameParts.length == 1) {
            // Single word name - take first two characters
            return nameParts[0].substring(0, Math.min(2, nameParts[0].length())).toUpperCase();
        } else {
            // Multiple words - take first character from first two words
            String firstInitial = nameParts[0].substring(0, 1).toUpperCase();
            String secondInitial = nameParts[1].substring(0, 1).toUpperCase();
            return firstInitial + secondInitial;
        }
    }

    private void updateEstimateFromRange() {
        if (startDateMillis <= 0 || endDateMillis <= 0) {
            return;
        }
        int days = calculateDaysInclusive(startDateMillis, endDateMillis);
        tvDaysCount.setText(String.valueOf(days));
        double estimate = unitPrice * days;
        tvTotalPrice.setText(String.format(Locale.US, "RM %.2f", estimate));
    }

    private int calculateDaysInclusive(long startMs, long endMs) {
        long diff = endMs - startMs;
        // convert ms -> days
        long daysBetween = TimeUnit.MILLISECONDS.toDays(diff);
        return (int) daysBetween + 1; // inclusive
    }

    private void setupImageSlider() {
        ImageAdapter adapter = new ImageAdapter(this, imageUrls);
        viewPagerImages.setAdapter(adapter);

        tabLayoutIndicator.removeAllTabs();
        for (int i = 0; i < imageUrls.size(); i++) {
            tabLayoutIndicator.addTab(tabLayoutIndicator.newTab());
        }
        viewPagerImages.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) {
                super.onPageSelected(position);
                if (position < tabLayoutIndicator.getTabCount()) {
                    tabLayoutIndicator.selectTab(tabLayoutIndicator.getTabAt(position));
                }
            }
        });

        tabLayoutIndicator.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                viewPagerImages.setCurrentItem(tab.getPosition(), true);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void openChatWithOwner() {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (currentUserId == null) {
            Toast.makeText(this, "Please login to chat", Toast.LENGTH_SHORT).show();
            return;
        }
        String chatId = generateChatId(currentUserId, ownerId);

        Intent i = new Intent(activity_product_details.this, activity_chat_owner.class);
        i.putExtra("chatId", chatId);
        i.putExtra("ownerId", ownerId);
        i.putExtra("productId", productId);
        startActivity(i);
    }

    private String generateChatId(String a, String b) {
        if (a == null || b == null) return "";
        if (a.compareTo(b) < 0) return a + "_" + b;
        else return b + "_" + a;
    }
}
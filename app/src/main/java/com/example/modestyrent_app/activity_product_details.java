package com.example.modestyrent_app;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.ImageView;
import android.widget.NumberPicker;
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

import java.util.ArrayList;
import java.util.Locale;

public class activity_product_details extends AppCompatActivity {

    public static final String EXTRA_PRODUCT_ID = "productId";

    private ViewPager2 viewPagerImages;
    private TabLayout tabLayoutIndicator;
    private TextView tvItemTitle, tvShortDesc, tvFullDesc, tvCategory, tvSize, tvPricePerDay, tvPriceInfo, tvOwnerName;
    private ImageView ivOwnerAvatar;
    private CalendarView calendarAvailability;
    private NumberPicker npDays;
    private Button btnBook, btnChatOwner, btnViewProfile;
    private FloatingActionButton fabChat;

    private String productId;
    private Product product;
    private long selectedDateMillis = -1;
    private double unitPrice = 0.0;
    private ArrayList<String> imageUrls = new ArrayList<>();
    private String ownerId = "";

    private DatabaseReference productsRef;
    private DatabaseReference usersRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_details);

        viewPagerImages = findViewById(R.id.viewPagerImages);
        tabLayoutIndicator = findViewById(R.id.tabLayoutIndicator);
        tvItemTitle = findViewById(R.id.tvItemTitle);
        tvShortDesc = findViewById(R.id.tvShortDesc);
        tvFullDesc = findViewById(R.id.tvFullDesc);
        tvCategory = findViewById(R.id.tvCategory);
        tvSize = findViewById(R.id.tvSize);
        tvPricePerDay = findViewById(R.id.tvPricePerDay);
        tvPriceInfo = findViewById(R.id.tvPriceInfo);
        tvOwnerName = findViewById(R.id.tvOwnerName);
        ivOwnerAvatar = findViewById(R.id.ivOwnerAvatar);
        calendarAvailability = findViewById(R.id.calendarAvailability);
        npDays = findViewById(R.id.npDays);
        btnBook = findViewById(R.id.btnBook);
        btnChatOwner = findViewById(R.id.btnChatOwner);
        btnViewProfile = findViewById(R.id.btnViewProfile);
        fabChat = findViewById(R.id.fabChat);

        npDays.setMinValue(1);
        npDays.setMaxValue(14);
        npDays.setValue(1);

        productId = getIntent().getStringExtra(EXTRA_PRODUCT_ID);
        if (TextUtils.isEmpty(productId)) {
            Toast.makeText(this, "Product not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        FirebaseDatabase database = FirebaseDatabase.getInstance();
        productsRef = database.getReference("products").child(productId);
        usersRef = database.getReference("users");

        loadProduct();

        calendarAvailability.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(year, month, dayOfMonth, 0, 0, 0);
            selectedDateMillis = cal.getTimeInMillis();
        });

        npDays.setOnValueChangedListener((picker, oldVal, newVal) -> updateEstimate());

        btnBook.setOnClickListener(v -> {
            if (selectedDateMillis <= 0) {
                Toast.makeText(activity_product_details.this, "Please choose a date from the calendar", Toast.LENGTH_SHORT).show();
                return;
            }
            int days = npDays.getValue();
            Intent intent = new Intent(activity_product_details.this, activity_checkout.class);
            intent.putExtra("productId", productId);
            intent.putExtra("productName", product != null ? product.getName() : "");
            intent.putExtra("ownerId", ownerId);
            intent.putExtra("selectedDateMillis", selectedDateMillis);
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
                    tvFullDesc.setText(product.getDescription() != null ? product.getDescription() : "");
                    tvCategory.setText((product.getCategory() != null ? product.getCategory() : "") + " • " + (product.getSize() != null ? product.getSize() : ""));
                    tvSize.setText(product.getSize() != null ? product.getSize() : "—");

                    unitPrice = product.getPrice();
                    tvPricePerDay.setText(String.format(Locale.US, "RM %.2f / day", unitPrice));
                    updateEstimate();

                    ownerId = product.getUserId() != null ? product.getUserId() : "";

                    if (product.getImageUrls() != null) {
                        imageUrls.clear();
                        imageUrls.addAll(product.getImageUrls());
                    }
                    setupImageSlider();

                    if (!ownerId.isEmpty()) {
                        usersRef.child(ownerId).get().addOnCompleteListener(uTask -> {
                            if (uTask.isSuccessful() && uTask.getResult().exists()) {
                                DataSnapshot usnap = uTask.getResult();
                                String ownerName = usnap.child("name").getValue(String.class);
                                tvOwnerName.setText(ownerName != null ? ownerName : "Owner");
                            }
                        });
                    }
                }
            } else {
                Toast.makeText(activity_product_details.this, "Failed to load product", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateEstimate() {
        int days = npDays.getValue();
        double estimate = unitPrice * days;
        tvPriceInfo.setText(String.format(Locale.US, "Estimate: RM %.2f", estimate));
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

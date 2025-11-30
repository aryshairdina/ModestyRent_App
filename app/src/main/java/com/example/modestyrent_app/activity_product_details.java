package com.example.modestyrent_app;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

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
    private DatabaseReference reviewsRef;

    private boolean waitingForEnd = false; // if user tapped start already

    private final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    // ⭐ Reviews UI
    private MaterialCardView reviewsCard;
    private TextView tvReviewSummary;
    private TextView tvNoReviews;
    private LinearLayout reviewsContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_details);

        // --- Find views ---
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

        // Reviews views
        reviewsCard = findViewById(R.id.reviewsCard);
        tvReviewSummary = findViewById(R.id.tvReviewSummary);
        tvNoReviews = findViewById(R.id.tvNoReviews);
        reviewsContainer = findViewById(R.id.reviewsContainer);

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
        reviewsRef = database.getReference("reviews");

        // Load product & owner
        loadProduct();

        // Calendar taps: first tap = start, second tap = end (or overwrite)
        calendarAvailability.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
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
                    tvCategory.setText((product.getCategory() != null ? product.getCategory() : "") + " • " +
                            (product.getSize() != null ? product.getSize() : ""));
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

                    // Load owner data
                    loadOwnerData();

                    // ⭐ Load reviews for this product
                    loadReviewsForProduct();
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

                    if (ownerName != null && !ownerName.isEmpty()) {
                        tvOwnerName.setText(ownerName);
                        createAvatarFromName(ownerName);
                    } else {
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
                    tvOwnerName.setText("Owner");
                    createAvatarFromName("Owner");
                }
            });
        } else {
            tvOwnerName.setText("Owner");
            createAvatarFromName("Owner");
        }
    }

    private void createAvatarFromName(String fullName) {
        String avatarText = getInitialsFromName(fullName);
        tvOwnerAvatar.setText(avatarText);
    }

    private String getInitialsFromName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return "OO";
        }

        String[] nameParts = fullName.trim().split("\\s+");

        if (nameParts.length == 1) {
            return nameParts[0].substring(0, Math.min(2, nameParts[0].length())).toUpperCase();
        } else {
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
        long daysBetween = TimeUnit.MILLISECONDS.toDays(diff);
        return (int) daysBetween + 1;
    }

    private void setupImageSlider() {
        ImageAdapter adapter = new ImageAdapter(this, imageUrls);
        viewPagerImages.setAdapter(adapter);

        tabLayoutIndicator.removeAllTabs();
        for (int i = 0; i < imageUrls.size(); i++) {
            tabLayoutIndicator.addTab(tabLayoutIndicator.newTab());
        }
        viewPagerImages.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
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

    // ⭐ Load all reviews for this product
    private void loadReviewsForProduct() {
        if (reviewsRef == null) return;

        // Hide first
        if (reviewsCard != null) {
            reviewsCard.setVisibility(android.view.View.GONE);
        }

        reviewsRef.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null || !task.getResult().exists()) {
                showNoReviewsState();
                return;
            }

            DataSnapshot root = task.getResult();
            reviewsContainer.removeAllViews();

            double totalRating = 0;
            int count = 0;

            for (DataSnapshot bookingSnap : root.getChildren()) {
                for (DataSnapshot reviewSnap : bookingSnap.getChildren()) {
                    String pId = reviewSnap.child("productId").getValue(String.class);
                    if (pId == null || !pId.equals(productId)) continue;

                    String reviewStatus = reviewSnap.child("reviewStatus").getValue(String.class);
                    if (reviewStatus != null && !"active".equals(reviewStatus)) continue;

                    Long ratingLong = reviewSnap.child("rating").getValue(Long.class);
                    int rating = ratingLong != null ? ratingLong.intValue() : 0;
                    String reviewText = reviewSnap.child("reviewText").getValue(String.class);
                    Long reviewDateMillis = reviewSnap.child("reviewDate").getValue(Long.class);
                    Boolean isAnonymousObj = reviewSnap.child("isAnonymous").getValue(Boolean.class);
                    boolean isAnonymous = isAnonymousObj != null && isAnonymousObj;
                    String renterId = reviewSnap.child("renterId").getValue(String.class);

                    totalRating += rating;
                    count++;

                    // Inflate item layout for each review
                    android.view.View itemView = getLayoutInflater()
                            .inflate(R.layout.item_product_review, reviewsContainer, false);

                    TextView tvReviewerName = itemView.findViewById(R.id.tvReviewerName);
                    TextView tvReviewDateItem = itemView.findViewById(R.id.tvReviewDateItem);
                    TextView tvReviewTextItem = itemView.findViewById(R.id.tvReviewTextItem);
                    ImageView star1 = itemView.findViewById(R.id.star1);
                    ImageView star2 = itemView.findViewById(R.id.star2);
                    ImageView star3 = itemView.findViewById(R.id.star3);
                    ImageView star4 = itemView.findViewById(R.id.star4);
                    ImageView star5 = itemView.findViewById(R.id.star5);

                    // Set review text & date
                    if (tvReviewTextItem != null) {
                        tvReviewTextItem.setText(reviewText != null && !reviewText.isEmpty() ? reviewText : "-");
                    }
                    if (tvReviewDateItem != null) {
                        if (reviewDateMillis != null) {
                            tvReviewDateItem.setText(formatDateTime(reviewDateMillis));
                        } else {
                            tvReviewDateItem.setText("");
                        }
                    }

                    // Set stars using ic_star_filled
                    ImageView[] stars = new ImageView[]{star1, star2, star3, star4, star5};
                    setStarRating(stars, rating);

                    // Set reviewer name (Anonymous or actual name)
                    if (tvReviewerName != null) {
                        if (isAnonymous) {
                            tvReviewerName.setText("Anonymous");
                        } else {
                            tvReviewerName.setText("Borrower");
                            if (renterId != null && !renterId.isEmpty()) {
                                final TextView nameView = tvReviewerName;
                                usersRef.child(renterId).child("fullName").get()
                                        .addOnCompleteListener(nameTask -> {
                                            if (nameTask.isSuccessful() && nameTask.getResult().exists()) {
                                                String fullName = nameTask.getResult().getValue(String.class);
                                                if (fullName != null && !fullName.isEmpty()) {
                                                    nameView.setText(fullName);
                                                }
                                            }
                                        });
                            }
                        }
                    }

                    reviewsContainer.addView(itemView);
                }
            }

            if (count == 0) {
                showNoReviewsState();
            } else {
                if (reviewsCard != null) {
                    reviewsCard.setVisibility(android.view.View.VISIBLE);
                }
                if (tvNoReviews != null) {
                    tvNoReviews.setVisibility(android.view.View.GONE);
                }
                double avg = totalRating / count;
                if (tvReviewSummary != null) {
                    tvReviewSummary.setText(
                            String.format(Locale.getDefault(),
                                    "⭐ %.1f (%d review%s)",
                                    avg, count, count == 1 ? "" : "s")
                    );
                }
            }
        });
    }

    private void showNoReviewsState() {
        if (reviewsCard != null) {
            reviewsCard.setVisibility(android.view.View.VISIBLE);
        }
        if (tvNoReviews != null) {
            tvNoReviews.setVisibility(android.view.View.VISIBLE);
            tvNoReviews.setText("No reviews yet. Be the first to rent & review! 😊");
        }
        if (tvReviewSummary != null) {
            tvReviewSummary.setText("⭐ 0.0 (0 reviews)");
        }
        if (reviewsContainer != null) {
            reviewsContainer.removeAllViews();
        }
    }

    private void setStarRating(ImageView[] stars, int rating) {
        if (stars == null) return;
        for (int i = 0; i < stars.length; i++) {
            ImageView star = stars[i];
            if (star == null) continue;
            star.setImageResource(R.drawable.ic_star_filled);
            if (i < rating) {
                star.setAlpha(1.0f);   // filled
            } else {
                star.setAlpha(0.2f);   // "empty"
            }
        }
    }

    private String formatDateTime(Long timestamp) {
        if (timestamp == null) return "";
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
        return sdf.format(new java.util.Date(timestamp));
    }
}

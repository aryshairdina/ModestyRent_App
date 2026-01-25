package com.example.modestyrent_app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class activity_rentals_details_borrower extends AppCompatActivity {

    private String bookingId, productId, ownerId, currentUserId;
    private String deliveryOption = "";
    private String productNameStr = "";
    private String bookingNumberStr = "";
    private String ownerName = "";

    private ImageView backButton;
    private LinearLayout statusTimeline;
    private TextView productName, bookingNumber, rentalPeriod;
    private TextView rentalAmount, depositAmount, totalAmount;
    private TextView deliveryOptionText, deliveryAddress, ownerContact;

    // Borrower Action Buttons
    private MaterialButton btnPrimaryAction, btnSecondaryAction, btnLeaveReview;
    private LinearLayout borrowerActionsLayout;

    // Review summary views
    private MaterialCardView reviewSummaryCard;
    private TextView tvReviewText, tvReviewDate;
    private ImageView ivReviewPhoto;

    // ⭐ 5 review stars
    private ImageView reviewStar1, reviewStar2, reviewStar3, reviewStar4, reviewStar5;

    // New: Late return warning
    private CardView cvLateReturnWarning;
    private TextView tvLateWarningTitle, tvLateWarningDays, tvLateWarningPenalty;
    private LinearLayout llLatePenaltyInfo;
    private ImageView ivWarningIcon;
    private TextView tvLateWarningMessage, tvCompletedPenaltyMessage;
    private LinearLayout llPenaltyPolicy;

    // New: Refund Completed Section
    private CardView cvRefundCompleted;
    private ImageView ivRefundIcon;
    private TextView tvRefundTitle, tvRefundMessage, tvRefundThankYou;
    private LinearLayout llRefundDetails;
    private TextView tvRefundAmount, tvRefundMethod;
    private MaterialButton btnViewTransactionDetails;

    private DatabaseReference bookingsRef, productsRef, usersRef, reviewsRef;
    private FirebaseAuth mAuth;
    private NotificationManager notificationManager;

    // Constants for penalty calculation
    private static final double PENALTY_PER_DAY = 5.0; // RM 5 per day
    private static final int PENALTY_CAP = 50; // Maximum penalty

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rentals_details_borrower);

        // Initialize Notification Manager
        notificationManager = new NotificationManager(this);

        // Get intent data
        Intent intent = getIntent();
        bookingId = intent.getStringExtra("bookingId");
        productId = intent.getStringExtra("productId");
        ownerId = intent.getStringExtra("ownerId");

        if (bookingId == null) {
            Toast.makeText(this, "Booking ID not provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        mAuth = FirebaseAuth.getInstance();
        currentUserId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;

        initializeViews();
        setupFirebase();
        loadBookingDetails();
    }

    private void initializeViews() {
        backButton = findViewById(R.id.backButton);
        statusTimeline = findViewById(R.id.statusTimeline);

        productName = findViewById(R.id.productName);
        bookingNumber = findViewById(R.id.bookingNumber);
        rentalPeriod = findViewById(R.id.rentalPeriod);

        rentalAmount = findViewById(R.id.rentalAmount);
        depositAmount = findViewById(R.id.depositAmount);
        totalAmount = findViewById(R.id.totalAmount);

        deliveryOptionText = findViewById(R.id.deliveryOption);
        deliveryAddress = findViewById(R.id.deliveryAddress);
        ownerContact = findViewById(R.id.ownerContact);

        // Borrower buttons
        btnPrimaryAction = findViewById(R.id.btnPrimaryAction);
        btnSecondaryAction = findViewById(R.id.btnSecondaryAction);
        btnLeaveReview = findViewById(R.id.btnLeaveReview);
        borrowerActionsLayout = findViewById(R.id.borrowerActionsLayout);

        // Review summary views
        reviewSummaryCard = findViewById(R.id.reviewSummaryCard);
        tvReviewText = findViewById(R.id.tvReviewText);
        tvReviewDate = findViewById(R.id.tvReviewDate);
        ivReviewPhoto = findViewById(R.id.ivReviewPhoto);

        // ⭐ find review stars
        reviewStar1 = findViewById(R.id.reviewStar1);
        reviewStar2 = findViewById(R.id.reviewStar2);
        reviewStar3 = findViewById(R.id.reviewStar3);
        reviewStar4 = findViewById(R.id.reviewStar4);
        reviewStar5 = findViewById(R.id.reviewStar5);

        // New: Late return warning views
        cvLateReturnWarning = findViewById(R.id.cvLateReturnWarning);
        tvLateWarningTitle = findViewById(R.id.tvLateWarningTitle);
        tvLateWarningDays = findViewById(R.id.tvLateWarningDays);
        tvLateWarningPenalty = findViewById(R.id.tvLateWarningPenalty);
        llLatePenaltyInfo = findViewById(R.id.llLatePenaltyInfo);
        ivWarningIcon = findViewById(R.id.ivWarningIcon);
        tvLateWarningMessage = findViewById(R.id.tvLateWarningMessage);
        tvCompletedPenaltyMessage = findViewById(R.id.tvCompletedPenaltyMessage);
        llPenaltyPolicy = findViewById(R.id.llPenaltyPolicy);

        // New: Refund Completed views
        cvRefundCompleted = findViewById(R.id.cvRefundCompleted);
        ivRefundIcon = findViewById(R.id.ivRefundIcon);
        tvRefundTitle = findViewById(R.id.tvRefundTitle);
        tvRefundMessage = findViewById(R.id.tvRefundMessage);
        tvRefundThankYou = findViewById(R.id.tvRefundThankYou);
        llRefundDetails = findViewById(R.id.llRefundDetails);
        tvRefundAmount = findViewById(R.id.tvRefundAmount);
        btnViewTransactionDetails = findViewById(R.id.btnViewTransactionDetails);

        backButton.setOnClickListener(v -> finish());
        setupButtonListeners();
    }

    private void setupFirebase() {
        bookingsRef = FirebaseDatabase.getInstance().getReference("bookings");
        productsRef = FirebaseDatabase.getInstance().getReference("products");
        usersRef = FirebaseDatabase.getInstance().getReference("users");
        reviewsRef = FirebaseDatabase.getInstance().getReference("reviews");
    }

    private void setupButtonListeners() {
        btnPrimaryAction.setOnClickListener(v -> handleBorrowerPrimaryAction());
        btnSecondaryAction.setOnClickListener(v -> startReturnProcess());
        btnLeaveReview.setOnClickListener(v -> leaveReview());

        // New: Transaction details button
        if (btnViewTransactionDetails != null) {
            btnViewTransactionDetails.setOnClickListener(v -> {
                Toast.makeText(this, "Transaction details will be shown here", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void loadBookingDetails() {
        bookingsRef.child(bookingId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    try {
                        productId = getStringValue(snapshot, "productId");
                        ownerId = getStringValue(snapshot, "ownerId");
                        deliveryOption = getStringValue(snapshot, "deliveryOption");

                        // Store product name and booking number
                        productNameStr = getStringValue(snapshot, "productName");
                        bookingNumberStr = getStringValue(snapshot, "bookingNumber");

                        // Load owner name
                        loadOwnerInfo(ownerId);

                        updateBookingUI(snapshot);
                        loadDeliveryInformation(snapshot, ownerId);
                        checkLateReturn(snapshot); // Check for late return

                        // Check and show refund completed section
                        checkAndShowRefundCompleted(snapshot);

                    } catch (Exception e) {
                        Toast.makeText(activity_rentals_details_borrower.this, "Error loading booking details", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(activity_rentals_details_borrower.this, "Booking not found", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(activity_rentals_details_borrower.this, "Failed to load booking", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkAndShowRefundCompleted(DataSnapshot bookingSnapshot) {
        String status = getStringValue(bookingSnapshot, "status");
        Double refundAmount = getDoubleValue(bookingSnapshot, "refundAmount");
        String refundStatus = getStringValue(bookingSnapshot, "refundStatus");
        String paymentMethod = getStringValue(bookingSnapshot, "paymentMethod");

        // Show refund completed section if status is "Completed"
        if ("Completed".equals(status) && cvRefundCompleted != null) {
            cvRefundCompleted.setVisibility(View.VISIBLE);

            // If refund amount exists, show details
            if (refundAmount != null && refundAmount > 0) {
                llRefundDetails.setVisibility(View.VISIBLE);
                tvRefundAmount.setText(String.format("RM %.2f", refundAmount));
            } else {
                llRefundDetails.setVisibility(View.GONE);
            }

            // Set the main refund message
            tvRefundMessage.setText("The refund has already been returned by the owner. Please check your bank account.");
            tvRefundThankYou.setText("Thank you for choosing us!");

        } else if (cvRefundCompleted != null) {
            cvRefundCompleted.setVisibility(View.GONE);
        }
    }

    private void loadOwnerInfo(String ownerId) {
        if (ownerId != null) {
            usersRef.child(ownerId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        ownerName = getStringValue(snapshot, "fullName");
                        if (ownerName == null) {
                            ownerName = getStringValue(snapshot, "name");
                        }
                        if (ownerName == null) {
                            ownerName = "Owner";
                        }
                    } else {
                        ownerName = "Owner";
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    ownerName = "Owner";
                }
            });
        }
    }

    private void updateBookingUI(DataSnapshot bookingSnapshot) {
        productName.setText(getStringValue(bookingSnapshot, "productName"));
        bookingNumber.setText("#" + getStringValue(bookingSnapshot, "bookingNumber"));

        Long startDate = getLongValue(bookingSnapshot, "startDate");
        Long endDate = getLongValue(bookingSnapshot, "endDate");
        Integer rentalDays = getIntegerValue(bookingSnapshot, "rentalDays");

        if (startDate != null && endDate != null) {
            String periodText = formatDate(startDate) + " - " + formatDate(endDate);
            if (rentalDays != null) {
                periodText += " (" + rentalDays + " days)";
            }
            rentalPeriod.setText(periodText);
        }

        Double rentalAmt = getDoubleValue(bookingSnapshot, "rentalAmount");
        Double depositAmt = getDoubleValue(bookingSnapshot, "depositAmount");
        Double totalAmt = getDoubleValue(bookingSnapshot, "totalAmount");

        if (rentalAmt != null) rentalAmount.setText(String.format("RM %.2f", rentalAmt));
        if (depositAmt != null) depositAmount.setText(String.format("RM %.2f", depositAmt));
        if (totalAmt != null) totalAmount.setText(String.format("RM %.2f", totalAmt));

        String deliveryOpt = getStringValue(bookingSnapshot, "deliveryOption");
        deliveryOptionText.setText(deliveryOpt != null ? deliveryOpt : "Pickup");

        String status = getStringValue(bookingSnapshot, "status");
        String deliveryStatus = getStringValue(bookingSnapshot, "deliveryStatus");

        // Check review flag on booking
        Boolean reviewedFlag = bookingSnapshot.child("reviewed").getValue(Boolean.class);
        boolean hasReview = reviewedFlag != null && reviewedFlag;

        updateStatusTimeline(bookingSnapshot);
        updateBorrowerActions(status, deliveryStatus, deliveryOption, hasReview);

        if (hasReview) {
            loadReviewSummary();
        } else {
            if (reviewSummaryCard != null) {
                reviewSummaryCard.setVisibility(View.GONE);
            }
        }
    }

    private void handleBorrowerPrimaryAction() {
        bookingsRef.child(bookingId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String status = getStringValue(snapshot, "status");
                String deliveryOption = getStringValue(snapshot, "deliveryOption");
                String deliveryStatus = getStringValue(snapshot, "deliveryStatus");

                if ("Delivery".equals(deliveryOption) && "OutForDelivery".equals(deliveryStatus)) {
                    markAsReceived();
                } else if ("Pickup".equals(deliveryOption) && "ReadyForPickup".equals(deliveryStatus)) {
                    markAsPickedUp();
                } else {
                    Toast.makeText(activity_rentals_details_borrower.this, "Action not available in current status", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(activity_rentals_details_borrower.this, "Failed to verify status", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void markAsPickedUp() {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "OnRent");
        updates.put("deliveryStatus", "");
        updates.put("pickupTime", System.currentTimeMillis());

        bookingsRef.child(bookingId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Item marked as picked up", Toast.LENGTH_SHORT).show();
                    loadBookingDetails();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update status", Toast.LENGTH_SHORT).show();
                });
    }

    private void markAsReceived() {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "OnRent");
        updates.put("deliveryStatus", "");
        updates.put("deliveryTime", System.currentTimeMillis());

        bookingsRef.child(bookingId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Item marked as received", Toast.LENGTH_SHORT).show();
                    loadBookingDetails();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update status", Toast.LENGTH_SHORT).show();
                });
    }

    private void startReturnProcess() {
        // First update status to ReturnRequested
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "ReturnRequested");
        updates.put("returnTime", System.currentTimeMillis());

        bookingsRef.child(bookingId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Return process started", Toast.LENGTH_SHORT).show();

                    // Send notification to owner
                    Map<String, Object> extraData = new HashMap<>();
                    extraData.put("productName", productNameStr);
                    extraData.put("bookingNumber", bookingNumberStr);
                    extraData.put("borrowerName", "You"); // You can replace with actual borrower name if available

                    notificationManager.sendOwnerNotification(
                            "borrower_return",
                            bookingId,
                            productId,
                            currentUserId, // borrowerId
                            productNameStr,
                            extraData
                    );

                    // Open return activity
                    Intent intent = new Intent(this, activity_arrange_return.class);
                    intent.putExtra("bookingId", bookingId);
                    intent.putExtra("productId", productId);
                    intent.putExtra("ownerId", ownerId);
                    startActivity(intent);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to start return process", Toast.LENGTH_SHORT).show();
                });
    }

    private void leaveReview() {
        Intent intent = new Intent(this, activity_leave_review.class);
        intent.putExtra("bookingId", bookingId);
        intent.putExtra("productId", productId);
        intent.putExtra("ownerId", ownerId);
        intent.putExtra("productName", productNameStr);
        intent.putExtra("ownerName", ownerName);
        startActivity(intent);
    }

    private void checkLateReturn(DataSnapshot bookingSnapshot) {
        String status = getStringValue(bookingSnapshot, "status");

        // Check if we should show warning for these statuses:
        boolean shouldCheckLate = "OnRent".equals(status) ||
                "ReturnRequested".equals(status) ||
                "AwaitingInspection".equals(status) ||
                "Completed".equals(status);

        if (shouldCheckLate) {
            // For Completed status, check if there was a late penalty applied
            if ("Completed".equals(status)) {
                checkCompletedLatePenalty(bookingSnapshot);
            } else {
                // For other statuses, calculate based on current date
                Long endDate = getLongValue(bookingSnapshot, "endDate");
                if (endDate != null) {
                    calculateAndShowLateWarning(endDate, status);

                    // Send notification if late
                    long currentTime = System.currentTimeMillis();
                    if (currentTime > endDate) {
                        Map<String, Object> extraData = new HashMap<>();
                        long daysLate = (currentTime - endDate) / (1000 * 60 * 60 * 24);
                        extraData.put("daysLate", daysLate);
                        extraData.put("penaltyAmount", Math.min(daysLate * PENALTY_PER_DAY, PENALTY_CAP));

                    }
                } else {
                    hideLateReturnWarning();
                }
            }
        } else {
            hideLateReturnWarning();
        }
    }

    private void calculateAndShowLateWarning(Long endDate, String status) {
        // Create calendar for due date (end of rental day)
        Calendar dueDateCal = Calendar.getInstance();
        dueDateCal.setTimeInMillis(endDate);
        dueDateCal.set(Calendar.HOUR_OF_DAY, 23);
        dueDateCal.set(Calendar.MINUTE, 59);
        dueDateCal.set(Calendar.SECOND, 59);
        dueDateCal.set(Calendar.MILLISECOND, 999);

        Calendar now = Calendar.getInstance();

        // Check if current time is AFTER the due date
        if (now.getTimeInMillis() > dueDateCal.getTimeInMillis()) {
            // Calculate days late based on calendar days
            Calendar dueDateOnly = Calendar.getInstance();
            dueDateOnly.setTimeInMillis(endDate);
            dueDateOnly.set(Calendar.HOUR_OF_DAY, 0);
            dueDateOnly.set(Calendar.MINUTE, 0);
            dueDateOnly.set(Calendar.SECOND, 0);
            dueDateOnly.set(Calendar.MILLISECOND, 0);

            Calendar nowOnly = Calendar.getInstance();
            nowOnly.set(Calendar.HOUR_OF_DAY, 0);
            nowOnly.set(Calendar.MINUTE, 0);
            nowOnly.set(Calendar.SECOND, 0);
            nowOnly.set(Calendar.MILLISECOND, 0);

            long diffInMillis = nowOnly.getTimeInMillis() - dueDateOnly.getTimeInMillis();
            long daysLate = diffInMillis / (1000 * 60 * 60 * 24);

            if (daysLate > 0) {
                double penaltyAmount = Math.min(daysLate * PENALTY_PER_DAY, PENALTY_CAP);

                // Show warning with different message based on status
                showLateReturnWarning(daysLate, penaltyAmount, status);

                // Update button text to emphasize urgency if still OnRent
                if ("OnRent".equals(status) && btnSecondaryAction != null && btnSecondaryAction.getVisibility() == View.VISIBLE) {
                    btnSecondaryAction.setText("RETURN NOW - Avoid More Penalty");
                    btnSecondaryAction.setBackgroundColor(getColor(R.color.error));
                    btnSecondaryAction.setTextColor(Color.WHITE);
                }
            } else {
                hideLateReturnWarning();
            }
        } else {
            hideLateReturnWarning();
        }
    }

    private void checkCompletedLatePenalty(DataSnapshot bookingSnapshot) {
        // For completed rentals, check if late penalty was applied
        Double latePenalty = getDoubleValue(bookingSnapshot, "latePenalty");
        Long daysLate = getLongValue(bookingSnapshot, "daysLate");
        Double refundAmount = getDoubleValue(bookingSnapshot, "refundAmount");
        Double originalDeposit = getDoubleValue(bookingSnapshot, "depositAmount");

        // First check: Was a late penalty applied?
        if (latePenalty != null && latePenalty > 0 && daysLate != null && daysLate > 0) {
            // Show final penalty warning for completed rental
            showCompletedLatePenaltyWarning(daysLate, latePenalty, refundAmount, originalDeposit);
        }
        // Second check: Was the rental completed WITHOUT penalty?
        else if ("Completed".equals(getStringValue(bookingSnapshot, "status"))) {
            hideLateReturnWarning();
        }
    }

    private void showLateReturnWarning(long daysLate, double penaltyAmount, String status) {
        if (cvLateReturnWarning == null) {
            return;
        }

        runOnUiThread(() -> {
            cvLateReturnWarning.setVisibility(View.VISIBLE);
            cvLateReturnWarning.setCardBackgroundColor(getColor(R.color.error_light));

            if (llLatePenaltyInfo != null) {
                llLatePenaltyInfo.setVisibility(View.VISIBLE);
            }

            // Set icon color
            if (ivWarningIcon != null) {
                ivWarningIcon.setColorFilter(getColor(R.color.error));
            }

            // Different title based on status
            String title = "";
            String message = "";
            boolean showUrgentMessage = false;

            if ("OnRent".equals(status)) {
                title = "⚠️ LATE RETURN DETECTED";
                message = "Return the item immediately to avoid additional charges!";
                showUrgentMessage = true;
            } else if ("ReturnRequested".equals(status) || "AwaitingInspection".equals(status)) {
                title = "⚠️ LATE RETURN - UNDER REVIEW";
                message = "Your return is being inspected. Penalty will be applied if confirmed late.";
                showUrgentMessage = false;
            }

            if (tvLateWarningTitle != null) {
                tvLateWarningTitle.setText(title);
                tvLateWarningTitle.setTextColor(getColor(R.color.error));
            }

            if (tvLateWarningDays != null) {
                tvLateWarningDays.setText("Late by: " + daysLate + " day(s)");
                tvLateWarningDays.setTextColor(getColor(R.color.error));
            }

            if (tvLateWarningPenalty != null) {
                String penaltyText;
                if ("OnRent".equals(status)) {
                    penaltyText = "Current penalty: RM " + String.format("%.2f", penaltyAmount);
                } else {
                    penaltyText = "Pending penalty: RM " + String.format("%.2f", penaltyAmount);
                }
                tvLateWarningPenalty.setText(penaltyText);
                tvLateWarningPenalty.setTextColor(getColor(R.color.error));
            }

            // Show/hide messages
            if (tvLateWarningMessage != null) {
                tvLateWarningMessage.setText(message);
                tvLateWarningMessage.setVisibility(showUrgentMessage ? View.VISIBLE : View.GONE);
            }

            if (tvCompletedPenaltyMessage != null) {
                tvCompletedPenaltyMessage.setVisibility(View.GONE);
            }

            // Update policy card color
            if (llPenaltyPolicy != null) {
                llPenaltyPolicy.setBackgroundColor(getColor(R.color.error_very_light));
            }
        });
    }

    private void showCompletedLatePenaltyWarning(long daysLate, double penaltyAmount, Double refundAmount, Double originalDeposit) {
        if (cvLateReturnWarning == null) {
            return;
        }

        runOnUiThread(() -> {
            cvLateReturnWarning.setVisibility(View.VISIBLE);
            cvLateReturnWarning.setCardBackgroundColor(getColor(R.color.warning_light));

            if (llLatePenaltyInfo != null) {
                llLatePenaltyInfo.setVisibility(View.VISIBLE);
            }

            // Set icon color to warning orange
            if (ivWarningIcon != null) {
                ivWarningIcon.setColorFilter(getColor(R.color.warning));
            }

            // Calculate refund percentage
            String refundText = "";
            if (refundAmount != null && originalDeposit != null && originalDeposit > 0) {
                double refundPercentage = (refundAmount / originalDeposit) * 100;
                refundText = String.format("Refunded: RM %.2f (%.0f%% of deposit)", refundAmount, refundPercentage);
            } else if (refundAmount != null) {
                refundText = String.format("Refunded: RM %.2f", refundAmount);
            }

            if (tvLateWarningTitle != null) {
                tvLateWarningTitle.setText("⚠️ LATE RETURN PENALTY APPLIED");
                tvLateWarningTitle.setTextColor(getColor(R.color.warning_dark));
            }

            if (tvLateWarningDays != null) {
                tvLateWarningDays.setText("Late return: " + daysLate + " day(s)");
                tvLateWarningDays.setTextColor(getColor(R.color.warning_dark));
            }

            if (tvLateWarningPenalty != null) {
                tvLateWarningPenalty.setText("Penalty charged: RM " + String.format("%.2f", penaltyAmount));
                tvLateWarningPenalty.setTextColor(getColor(R.color.warning_dark));
            }

            // Show/hide messages
            if (tvLateWarningMessage != null) {
                tvLateWarningMessage.setVisibility(View.GONE);
            }

            if (tvCompletedPenaltyMessage != null) {
                tvCompletedPenaltyMessage.setText(refundText);
                tvCompletedPenaltyMessage.setVisibility(View.VISIBLE);
            }

            // Update policy card color for completed
            if (llPenaltyPolicy != null) {
                llPenaltyPolicy.setBackgroundColor(getColor(R.color.warning_very_light));
            }
        });
    }

    private void hideLateReturnWarning() {
        runOnUiThread(() -> {
            if (cvLateReturnWarning != null) {
                cvLateReturnWarning.setVisibility(View.GONE);
                // Reset card color
                cvLateReturnWarning.setCardBackgroundColor(getColor(R.color.error_light));
            }

            if (llLatePenaltyInfo != null) {
                llLatePenaltyInfo.setVisibility(View.GONE);
            }

            // Reset button appearance if it was changed
            if (btnSecondaryAction != null) {
                btnSecondaryAction.setText("Start Return");
                btnSecondaryAction.setBackgroundColor(getColor(R.color.background));
                btnSecondaryAction.setTextColor(getColor(R.color.primary));
            }
        });
    }

    // ============= DELIVERY INFORMATION METHODS =============

    private void loadDeliveryInformation(DataSnapshot bookingSnapshot, String ownerId) {
        String deliveryOpt = getStringValue(bookingSnapshot, "deliveryOption");

        if ("Delivery".equals(deliveryOpt)) {
            String deliveryAddressText = getStringValue(bookingSnapshot, "deliveryAddress");
            deliveryAddress.setText(deliveryAddressText != null ? deliveryAddressText : "Address not available");
            loadOwnerContactInfo(ownerId);
        } else {
            loadOwnerAddressForPickup(ownerId);
            loadOwnerContactInfo(ownerId);
        }
    }

    private void loadOwnerAddressForPickup(String ownerId) {
        if (ownerId != null) {
            usersRef.child(ownerId).child("address").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String ownerAddress = snapshot.getValue(String.class);
                        deliveryAddress.setText(ownerAddress != null ? ownerAddress : "Address not available");
                    } else {
                        deliveryAddress.setText("Address not available");
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    deliveryAddress.setText("Address not available");
                }
            });
        }
    }

    private void loadOwnerContactInfo(String ownerId) {
        if (ownerId != null) {
            usersRef.child(ownerId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String ownerName = getStringValue(snapshot, "fullName");
                        String ownerPhone = getStringValue(snapshot, "phone");

                        if (ownerName != null && ownerPhone != null) {
                            ownerContact.setText("Owner: " + ownerName + " (" + ownerPhone + ")");
                        } else {
                            ownerContact.setText("Owner information not available");
                        }
                    } else {
                        ownerContact.setText("Owner information not available");
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    ownerContact.setText("Owner information not available");
                }
            });
        }
    }

    // ============= STATUS TIMELINE METHODS =============

    private void updateStatusTimeline(DataSnapshot bookingSnapshot) {
        statusTimeline.removeAllViews();

        Long bookingDate = getLongValue(bookingSnapshot, "bookingDate");
        Long paymentDate = getLongValue(bookingSnapshot, "paymentDate");
        String status = getStringValue(bookingSnapshot, "status");
        String deliveryStatus = getStringValue(bookingSnapshot, "deliveryStatus");

        // Get ALL timestamp fields
        Long preparationTime = getLongValue(bookingSnapshot, "preparationTime");
        Long deliveryLeaveTime = getLongValue(bookingSnapshot, "deliveryLeaveTime");
        Long deliveryTime = getLongValue(bookingSnapshot, "deliveryTime");
        Long pickupTime = getLongValue(bookingSnapshot, "pickupTime");
        Long readyForPickupTime = getLongValue(bookingSnapshot, "readyForPickupTime");
        Long returnTime = getLongValue(bookingSnapshot, "returnTime");
        Long inspectionTime = getLongValue(bookingSnapshot, "inspectionTime");
        Long completionTime = getLongValue(bookingSnapshot, "completionTime");

        // If completionTime doesn't exist but status is "Completed", use current time
        if ("Completed".equals(status) && completionTime == null) {
            completionTime = System.currentTimeMillis();
        }

        // If inspectionTime doesn't exist but status is "Completed", use completionTime
        if ("Completed".equals(status) && inspectionTime == null && completionTime != null) {
            inspectionTime = completionTime;
        }

        // If returnTime doesn't exist but status is "Completed" or "AwaitingInspection", use inspectionTime
        if (("AwaitingInspection".equals(status) || "Completed".equals(status)) && returnTime == null && inspectionTime != null) {
            returnTime = inspectionTime;
        }

        if ("Delivery".equals(deliveryOption)) {
            String[] statusFlow = {"Confirmed", "Preparing Delivery", "Out for Delivery", "On Rent", "Return", "Inspection", "Completed"};
            String[] statusDescriptions = {
                    "Booking confirmed and payment received",
                    "Owner is preparing the item for delivery",
                    "Item is out for delivery to borrower",
                    "Item is with borrower during rental period",
                    "Borrower has returned the item",
                    "Owner is inspecting the returned item",
                    "Rental completed successfully"
            };
            String[] statusIcons = {"ic_check", "ic_preparing", "ic_delivery", "ic_onrent", "ic_return", "ic_inspection", "ic_completed"};
            String[] statusDates = {
                    formatDateTime(paymentDate != null ? paymentDate : bookingDate),
                    formatDateTime(preparationTime),
                    formatDateTime(deliveryLeaveTime),
                    formatDateTime(deliveryTime),
                    formatDateTime(returnTime),
                    formatDateTime(inspectionTime),
                    formatDateTime(completionTime)
            };

            for (int i = 0; i < statusFlow.length; i++) {
                String currentStatus = statusFlow[i];
                View statusItem = createStatusItem(currentStatus, statusDescriptions[i], statusIcons[i], statusDates[i],
                        status, deliveryStatus, i == statusFlow.length - 1);
                statusTimeline.addView(statusItem);
            }
        } else {
            String[] statusFlow = {"Confirmed", "Preparing Pickup", "Ready for Pickup", "On Rent", "Return", "Inspection", "Completed"};
            String[] statusDescriptions = {
                    "Booking confirmed and payment received",
                    "Owner is preparing the item for pickup",
                    "Item is ready for pickup by borrower",
                    "Item is with borrower during rental period",
                    "Borrower has returned the item",
                    "Owner is inspecting the returned item",
                    "Rental completed successfully"
            };
            String[] statusIcons = {"ic_check", "ic_preparing", "ic_ready_pickup", "ic_onrent", "ic_return", "ic_inspection", "ic_completed"};
            String[] statusDates = {
                    formatDateTime(paymentDate != null ? paymentDate : bookingDate),
                    formatDateTime(preparationTime),
                    formatDateTime(readyForPickupTime),
                    formatDateTime(pickupTime),
                    formatDateTime(returnTime),
                    formatDateTime(inspectionTime),
                    formatDateTime(completionTime)
            };

            for (int i = 0; i < statusFlow.length; i++) {
                String currentStatus = statusFlow[i];
                View statusItem = createStatusItem(currentStatus, statusDescriptions[i], statusIcons[i], statusDates[i],
                        status, deliveryStatus, i == statusFlow.length - 1);
                statusTimeline.addView(statusItem);
            }
        }
    }

    private View createStatusItem(String statusText, String description, String iconName, String date,
                                  String currentStatus, String deliveryStatus, boolean isLast) {
        View statusItem = LayoutInflater.from(this).inflate(R.layout.item_status_timeline, statusTimeline, false);

        ImageView statusIcon = statusItem.findViewById(R.id.statusIcon);
        TextView statusTextView = statusItem.findViewById(R.id.statusText);
        TextView statusDescriptionView = statusItem.findViewById(R.id.statusDescription);
        TextView statusDateView = statusItem.findViewById(R.id.statusDate);
        View statusIndicator = statusItem.findViewById(R.id.statusIndicator);
        View connectorLine = statusItem.findViewById(R.id.connectorLine);

        int iconResource = getResources().getIdentifier(iconName, "drawable", getPackageName());
        if (iconResource == 0) {
            iconResource = R.drawable.ic_check;
        }
        statusIcon.setImageResource(iconResource);

        statusTextView.setText(statusText);
        statusDescriptionView.setText(description);

        // Handle date display - show "Pending" for null/empty dates
        if (date == null || date.isEmpty() || "null".equals(date) || "N/A".equals(date)) {
            statusDateView.setText("Pending");
        } else {
            statusDateView.setText(date);
        }

        if (isLast) {
            connectorLine.setVisibility(View.GONE);
        }

        boolean isCompleted = isStatusCompleted(statusText, currentStatus, deliveryStatus);
        boolean isCurrent = isCurrentStatus(statusText, currentStatus, deliveryStatus);

        if (isCompleted) {
            statusIndicator.setBackgroundResource(R.drawable.circle_completed);
            statusIcon.setColorFilter(getColor(R.color.primary));
            statusTextView.setTextColor(getColor(R.color.primary));
            statusDescriptionView.setTextColor(getColor(R.color.textcolor));
            statusDateView.setTextColor(getColor(R.color.textcolor));

            // If date shows "Pending" but status is completed, show current date
            if ("Pending".equals(statusDateView.getText().toString()) && "Completed".equals(currentStatus)) {
                SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
                statusDateView.setText(sdf.format(new Date()));
            }
        } else if (isCurrent) {
            statusIndicator.setBackgroundResource(R.drawable.circle_current);
            statusIcon.setColorFilter(getColor(R.color.primary));
            statusTextView.setTextColor(getColor(R.color.primary));
            statusDescriptionView.setTextColor(getColor(R.color.primary));
            statusDateView.setTextColor(getColor(R.color.primary));
        } else {
            statusIndicator.setBackgroundResource(R.drawable.circle_pending);
            statusIcon.setColorFilter(getColor(R.color.textcolor));
            statusTextView.setTextColor(getColor(R.color.textcolor));
            statusDescriptionView.setTextColor(getColor(R.color.textcolor));
            statusDateView.setTextColor(getColor(R.color.textcolor));
            statusDateView.setText("Pending");
        }

        return statusItem;
    }

    private boolean isStatusCompleted(String timelineStatus, String currentStatus, String deliveryStatus) {
        // If current status is "Completed", ALL timeline statuses should be completed
        if ("Completed".equals(currentStatus)) {
            return true; // All statuses are completed
        }

        if ("Delivery".equals(deliveryOption)) {
            switch (timelineStatus) {
                case "Confirmed":
                    return true; // Always completed once booking is confirmed
                case "Preparing Delivery":
                    return "PreparingDelivery".equals(currentStatus) ||
                            "OutForDelivery".equals(deliveryStatus) ||
                            "OnRent".equals(currentStatus) ||
                            "ReturnRequested".equals(currentStatus) ||
                            "AwaitingInspection".equals(currentStatus) ||
                            "Completed".equals(currentStatus);
                case "Out for Delivery":
                    return "OutForDelivery".equals(deliveryStatus) ||
                            "OnRent".equals(currentStatus) ||
                            "ReturnRequested".equals(currentStatus) ||
                            "AwaitingInspection".equals(currentStatus) ||
                            "Completed".equals(currentStatus);
                case "On Rent":
                    return "OnRent".equals(currentStatus) ||
                            "ReturnRequested".equals(currentStatus) ||
                            "AwaitingInspection".equals(currentStatus) ||
                            "Completed".equals(currentStatus);
                case "Return":
                    return "AwaitingInspection".equals(currentStatus) ||
                            "Completed".equals(currentStatus);
                case "Inspection":
                    return "Completed".equals(currentStatus); // Fixed: Only completed when status is "Completed"
                case "Completed":
                    return "Completed".equals(currentStatus);
                default:
                    return false;
            }
        } else {
            switch (timelineStatus) {
                case "Confirmed":
                    return true; // Always completed once booking is confirmed
                case "Preparing Pickup":
                    return "PreparingPickup".equals(currentStatus) ||
                            "ReadyForPickup".equals(deliveryStatus) ||
                            "OnRent".equals(currentStatus) ||
                            "ReturnRequested".equals(currentStatus) ||
                            "AwaitingInspection".equals(currentStatus) ||
                            "Completed".equals(currentStatus);
                case "Ready for Pickup":
                    return "ReadyForPickup".equals(deliveryStatus) ||
                            "OnRent".equals(currentStatus) ||
                            "ReturnRequested".equals(currentStatus) ||
                            "AwaitingInspection".equals(currentStatus) ||
                            "Completed".equals(currentStatus);
                case "On Rent":
                    return "OnRent".equals(currentStatus) ||
                            "ReturnRequested".equals(currentStatus) ||
                            "AwaitingInspection".equals(currentStatus) ||
                            "Completed".equals(currentStatus);
                case "Return":
                    return "AwaitingInspection".equals(currentStatus) ||
                            "Completed".equals(currentStatus);
                case "Inspection":
                    return "Completed".equals(currentStatus); // Fixed: Only completed when status is "Completed"
                case "Completed":
                    return "Completed".equals(currentStatus);
                default:
                    return false;
            }
        }
    }

    private boolean isCurrentStatus(String timelineStatus, String currentStatus, String deliveryStatus) {
        if ("Delivery".equals(deliveryOption)) {
            return (timelineStatus.equals("Preparing Delivery") && "PreparingDelivery".equals(currentStatus)) ||
                    (timelineStatus.equals("Out for Delivery") && "OutForDelivery".equals(deliveryStatus)) ||
                    (timelineStatus.equals("On Rent") && "OnRent".equals(currentStatus)) ||
                    (timelineStatus.equals("Return") && "ReturnRequested".equals(currentStatus)) ||
                    (timelineStatus.equals("Inspection") && "AwaitingInspection".equals(currentStatus)) ||
                    (timelineStatus.equals("Completed") && "Completed".equals(currentStatus));
        } else {
            return (timelineStatus.equals("Preparing Pickup") && "PreparingPickup".equals(currentStatus)) ||
                    (timelineStatus.equals("Ready for Pickup") && "ReadyForPickup".equals(deliveryStatus)) ||
                    (timelineStatus.equals("On Rent") && "OnRent".equals(currentStatus)) ||
                    (timelineStatus.equals("Return") && "ReturnRequested".equals(currentStatus)) ||
                    (timelineStatus.equals("Inspection") && "AwaitingInspection".equals(currentStatus)) ||
                    (timelineStatus.equals("Completed") && "Completed".equals(currentStatus));
        }
    }

    private void updateBorrowerActions(String status, String deliveryStatus, String deliveryOption, boolean hasReview) {
        btnPrimaryAction.setVisibility(View.GONE);
        btnSecondaryAction.setVisibility(View.GONE);
        btnLeaveReview.setVisibility(View.GONE);
        borrowerActionsLayout.setVisibility(View.GONE);

        if (status == null) return;

        boolean hasAction = false;

        if ("Delivery".equals(deliveryOption)) {
            if ("OutForDelivery".equals(deliveryStatus) && !"OnRent".equals(status)) {
                btnPrimaryAction.setText("Mark as Received");
                btnPrimaryAction.setVisibility(View.VISIBLE);
                hasAction = true;
            } else if ("OnRent".equals(status)) {
                btnSecondaryAction.setText("Start Return");
                btnSecondaryAction.setVisibility(View.VISIBLE);
                hasAction = true;
            } else if ("Completed".equals(status) && !hasReview) {
                btnLeaveReview.setText("Leave Review");
                btnLeaveReview.setVisibility(View.VISIBLE);
                hasAction = true;
            }
        } else if ("Pickup".equals(deliveryOption)) {
            if ("ReadyForPickup".equals(deliveryStatus) && !"OnRent".equals(status)) {
                btnPrimaryAction.setText("Mark as Picked Up");
                btnPrimaryAction.setVisibility(View.VISIBLE);
                hasAction = true;
            } else if ("OnRent".equals(status)) {
                btnSecondaryAction.setText("Start Return");
                btnSecondaryAction.setVisibility(View.VISIBLE);
                hasAction = true;
            } else if ("Completed".equals(status) && !hasReview) {
                btnLeaveReview.setText("Leave Review");
                btnLeaveReview.setVisibility(View.VISIBLE);
                hasAction = true;
            }
        }

        if (hasAction) {
            borrowerActionsLayout.setVisibility(View.VISIBLE);
        }
    }

    // ============= REVIEW METHODS =============

    private void loadReviewSummary() {
        if (reviewsRef == null || bookingId == null || currentUserId == null) return;

        reviewsRef.child(bookingId)
                .orderByChild("renterId")
                .equalTo(currentUserId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            if (reviewSummaryCard != null) reviewSummaryCard.setVisibility(View.GONE);
                            return;
                        }

                        DataSnapshot reviewSnap = null;
                        for (DataSnapshot child : snapshot.getChildren()) {
                            reviewSnap = child;
                            break;
                        }
                        if (reviewSnap == null) {
                            if (reviewSummaryCard != null) reviewSummaryCard.setVisibility(View.GONE);
                            return;
                        }

                        Long ratingLong = reviewSnap.child("rating").getValue(Long.class);
                        Long ratingScoreLong = reviewSnap.child("ratingScore").getValue(Long.class);
                        String reviewText = reviewSnap.child("reviewText").getValue(String.class);
                        Long reviewDateMillis = reviewSnap.child("reviewDate").getValue(Long.class);
                        String photoUrl = reviewSnap.child("photoUrl").getValue(String.class);

                        int rating = ratingLong != null ? ratingLong.intValue() : 0;
                        int ratingScore = ratingScoreLong != null ? ratingScoreLong.intValue() : rating * 20;

                        // ⭐ update stars + text
                        updateReviewStars(rating);

                        if (tvReviewText != null) {
                            tvReviewText.setText(reviewText != null ? reviewText : "-");
                        }
                        if (tvReviewDate != null) {
                            if (reviewDateMillis != null) {
                                tvReviewDate.setText("Reviewed on " + formatDateTime(reviewDateMillis));
                            } else {
                                tvReviewDate.setText("");
                            }
                        }

                        if (ivReviewPhoto != null) {
                            if (photoUrl != null && !photoUrl.isEmpty()) {
                                ivReviewPhoto.setVisibility(View.VISIBLE);
                                Glide.with(activity_rentals_details_borrower.this)
                                        .load(photoUrl)
                                        .placeholder(R.drawable.ic_image_placeholder)
                                        .into(ivReviewPhoto);
                            } else {
                                ivReviewPhoto.setVisibility(View.GONE);
                            }
                        }

                        if (reviewSummaryCard != null) {
                            reviewSummaryCard.setVisibility(View.VISIBLE);
                        }

                        btnLeaveReview.setVisibility(View.GONE);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (reviewSummaryCard != null) reviewSummaryCard.setVisibility(View.GONE);
                    }
                });
    }

    private void updateReviewStars(int rating) {
        ImageView[] stars = new ImageView[]{reviewStar1, reviewStar2, reviewStar3, reviewStar4, reviewStar5};

        for (int i = 0; i < stars.length; i++) {
            ImageView star = stars[i];
            if (star == null) continue;

            if (i < rating) {
                // filled (gold)
                star.setColorFilter(Color.parseColor("#FFD700"));
            } else {
                // grey / inactive
                star.setColorFilter(Color.parseColor("#D6D3CF"));
            }
        }
    }

    // ============= HELPER METHODS =============

    private String getStringValue(DataSnapshot snapshot, String key) {
        DataSnapshot child = snapshot.child(key);
        return child.exists() && child.getValue() != null ? child.getValue().toString() : null;
    }

    private Long getLongValue(DataSnapshot snapshot, String key) {
        DataSnapshot child = snapshot.child(key);
        if (child.exists()) {
            Object value = child.getValue();
            if (value instanceof Long) return (Long) value;
            if (value instanceof String) {
                try {
                    return Long.parseLong((String) value);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private Double getDoubleValue(DataSnapshot snapshot, String key) {
        DataSnapshot child = snapshot.child(key);
        if (child.exists()) {
            Object value = child.getValue();
            if (value instanceof Double) return (Double) value;
            if (value instanceof Long) return ((Long) value).doubleValue();
            if (value instanceof String) {
                try {
                    return Double.parseDouble((String) value);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private Integer getIntegerValue(DataSnapshot snapshot, String key) {
        DataSnapshot child = snapshot.child(key);
        if (child.exists()) {
            Object value = child.getValue();
            if (value instanceof Integer) return (Integer) value;
            if (value instanceof Long) return ((Long) value).intValue();
            if (value instanceof String) {
                try {
                    return Integer.parseInt((String) value);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private String formatDate(Long timestamp) {
        if (timestamp == null || timestamp == 0) return "N/A";
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    private String formatDateTime(Long timestamp) {
        if (timestamp == null || timestamp == 0) {
            return "Pending";
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        } catch (Exception e) {
            return "Pending";
        }
    }
}
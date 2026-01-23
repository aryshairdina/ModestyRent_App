package com.example.modestyrent_app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.text.SimpleDateFormat;
import java.util.*;
import com.example.modestyrent_app.NotificationHelper;

public class activity_inspection extends AppCompatActivity {

    private String bookingId, productId, renterId, ownerId, currentProductName, currentOwnerName, currentBorrowerName;

    // UI Components
    private ImageView backButton;
    private TextView tvProductName, tvBorrowerName, tvRentalPeriod, tvReturnMethod;
    private RadioGroup rgItemCondition;
    private RadioButton rbExcellent, rbGood, rbFair, rbPoor;
    private EditText etDamageNotes;
    private Slider sliderRepairCost;
    private TextView tvRepairCost, tvOriginalDeposit, tvDeductions, tvRefundAmount, tvFinalRefund;
    private MaterialButton btnCompleteInspection;

    // New UI Components for late return indicator
    private CardView cvDueDateIndicator;
    private TextView tvDueDateStatus, tvDueDateLabel, tvDueDateValue, tvLatePenaltyAmount, tvRepairCostDeduction, tvLatePenaltyDeduction;

    private LinearLayout llLatePenaltySection, llLatePenaltyDeduction;

    // Database References
    private DatabaseReference bookingsRef, usersRef, productsRef;
    private FirebaseAuth mAuth;

    // Calculation fields
    private double originalDepositAmount = 50.0;
    private double latePenaltyAmount = 0.0;
    private double repairCostAmount = 0.0;
    private boolean needsRefundPayment = false;
    private boolean isLateReturn = false;
    private long daysLate = 0;

    // Constants
    private static final double PENALTY_PER_DAY = 5.0; // RM 5 per day
    private static final int PENALTY_CAP = 50; // Maximum penalty (optional)
    private static final String TAG = "InspectionActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inspection);

        // Get intent data
        Intent intent = getIntent();
        bookingId = intent.getStringExtra("bookingId");
        productId = intent.getStringExtra("productId");
        renterId = intent.getStringExtra("renterId");

        // Debug logging
        Log.d(TAG, "Received from intent:");
        Log.d(TAG, "- bookingId: " + bookingId);
        Log.d(TAG, "- productId: " + productId);
        Log.d(TAG, "- renterId: " + renterId);

        if (bookingId == null || bookingId.isEmpty()) {
            Toast.makeText(this, "Booking ID not provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        mAuth = FirebaseAuth.getInstance();
        ownerId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;

        initializeViews();
        setupFirebase();
        loadBookingDetails();
        setupListeners();
    }

    private void initializeViews() {
        backButton = findViewById(R.id.backButton);
        tvProductName = findViewById(R.id.tvProductName);
        tvBorrowerName = findViewById(R.id.tvBorrowerName);
        tvRentalPeriod = findViewById(R.id.tvRentalPeriod);
        tvReturnMethod = findViewById(R.id.tvReturnMethod);

        // Condition radio buttons
        rgItemCondition = findViewById(R.id.rgItemCondition);
        rbExcellent = findViewById(R.id.rbExcellent);
        rbGood = findViewById(R.id.rbGood);
        rbFair = findViewById(R.id.rbFair);
        rbPoor = findViewById(R.id.rbPoor);

        // Damage and repair
        etDamageNotes = findViewById(R.id.etDamageNotes);
        sliderRepairCost = findViewById(R.id.sliderRepairCost);
        tvRepairCost = findViewById(R.id.tvRepairCost);
        tvOriginalDeposit = findViewById(R.id.tvOriginalDeposit);
        tvDeductions = findViewById(R.id.tvDeductions);
        tvRefundAmount = findViewById(R.id.tvRefundAmount);
        tvFinalRefund = findViewById(R.id.tvFinalRefund);
        btnCompleteInspection = findViewById(R.id.btnCompleteInspection);

        // New views for late return indicator
        cvDueDateIndicator = findViewById(R.id.cvDueDateIndicator);
        tvDueDateStatus = findViewById(R.id.tvDueDateStatus);
        tvDueDateLabel = findViewById(R.id.tvDueDateLabel);
        tvDueDateValue = findViewById(R.id.tvDueDateValue);
        tvLatePenaltyAmount = findViewById(R.id.tvLatePenaltyAmount);
        llLatePenaltySection = findViewById(R.id.llLatePenaltySection);

        // Add these lines after initializing other views
        tvRepairCostDeduction = findViewById(R.id.tvRepairCostDeduction);
        tvLatePenaltyDeduction = findViewById(R.id.tvLatePenaltyDeduction);
        llLatePenaltyDeduction = findViewById(R.id.llLatePenaltyDeduction);

        backButton.setOnClickListener(v -> finish());
    }

    private void setupFirebase() {
        bookingsRef = FirebaseDatabase.getInstance().getReference("bookings");
        usersRef = FirebaseDatabase.getInstance().getReference("users");
        productsRef = FirebaseDatabase.getInstance().getReference("products");
    }

    private void setupListeners() {
        // Repair cost slider listener
        sliderRepairCost.addOnChangeListener((slider, value, fromUser) -> {
            repairCostAmount = value;
            tvRepairCost.setText(String.format("RM %.2f", value));
            calculateRefundAmount();
        });

        // Condition radio buttons listener
        CompoundButton.OnCheckedChangeListener conditionListener = (buttonView, isChecked) -> {
            if (!isChecked) return;

            if (buttonView != rbExcellent) rbExcellent.setChecked(false);
            if (buttonView != rbGood) rbGood.setChecked(false);
            if (buttonView != rbFair) rbFair.setChecked(false);
            if (buttonView != rbPoor) rbPoor.setChecked(false);

            rgItemCondition.check(buttonView.getId());
        };

        rbExcellent.setOnCheckedChangeListener(conditionListener);
        rbGood.setOnCheckedChangeListener(conditionListener);
        rbFair.setOnCheckedChangeListener(conditionListener);
        rbPoor.setOnCheckedChangeListener(conditionListener);

        // Complete inspection button
        btnCompleteInspection.setOnClickListener(v -> {
            if (!validateItemCondition()) return;

            if (needsRefundPayment) {
                openRefundPaymentPage();
            } else {
                completeInspectionWithoutPayment();
            }
        });
    }

    private void loadBookingDetails() {
        bookingsRef.child(bookingId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    try {
                        updateBookingUI(snapshot);

                        // If renterId wasn't passed in intent, try to get it from booking data
                        if (renterId == null || renterId.isEmpty()) {
                            renterId = getStringValue(snapshot, "renterId");
                            Log.d(TAG, "Extracted renterId from booking: " + renterId);
                        }

                        // Get ownerId from booking if not already set
                        String bookingOwnerId = getStringValue(snapshot, "ownerId");
                        if (bookingOwnerId != null && !bookingOwnerId.isEmpty()) {
                            ownerId = bookingOwnerId;
                        }

                        // Get product name for notifications
                        currentProductName = getStringValue(snapshot, "productName");
                        if (currentProductName == null) {
                            currentProductName = "the product";
                        }

                        // Load borrower info if we have renterId
                        if (renterId != null && !renterId.isEmpty()) {
                            loadBorrowerInfo();
                        } else {
                            tvBorrowerName.setText("Unknown Borrower");
                            Log.w(TAG, "No renterId available to load borrower info");
                        }

                        // Load owner info for notifications
                        loadOwnerInfoForNotifications();

                        checkAndCalculateLatePenalty(snapshot);
                    } catch (Exception e) {
                        Toast.makeText(activity_inspection.this, "Error loading booking details", Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    }
                } else {
                    Toast.makeText(activity_inspection.this, "Booking not found", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(activity_inspection.this, "Failed to load booking", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateBookingUI(DataSnapshot bookingSnapshot) {
        // Basic booking info
        String productName = getStringValue(bookingSnapshot, "productName");
        if (productName != null && !productName.isEmpty()) {
            tvProductName.setText(productName);
            currentProductName = productName;
        } else {
            tvProductName.setText("Product name not available");
            currentProductName = "the product";
        }

        // Rental period
        Long startDate = getLongValue(bookingSnapshot, "startDate");
        Long endDate = getLongValue(bookingSnapshot, "endDate");

        if (startDate != null && endDate != null) {
            String periodText = formatDate(startDate) + " - " + formatDate(endDate);
            tvRentalPeriod.setText(periodText);
        } else {
            tvRentalPeriod.setText("Date not available");
        }

        // Return method
        String returnMethod = getStringValue(bookingSnapshot, "returnMethod");
        if ("DropOff".equals(returnMethod)) {
            tvReturnMethod.setText("Drop-off at Owner");
        } else if ("OwnerPickup".equals(returnMethod)) {
            tvReturnMethod.setText("Owner Pickup");
        } else {
            tvReturnMethod.setText("Not specified");
        }

        // Deposit from booking
        Double depositAmount = getDoubleValue(bookingSnapshot, "depositAmount");
        if (depositAmount != null) {
            originalDepositAmount = depositAmount;
            tvOriginalDeposit.setText(String.format("RM %.2f", depositAmount));
        } else {
            // Try to get it from another field or use default
            originalDepositAmount = 50.0;
            tvOriginalDeposit.setText("RM 50.00");
        }
    }

    private void checkAndCalculateLatePenalty(DataSnapshot bookingSnapshot) {
        Long endDate = getLongValue(bookingSnapshot, "endDate");

        if (endDate != null) {
            // Create calendar instances
            Calendar dueDateCal = Calendar.getInstance();
            dueDateCal.setTimeInMillis(endDate);

            // Set due date to END of the due day (23:59:59)
            dueDateCal.set(Calendar.HOUR_OF_DAY, 23);
            dueDateCal.set(Calendar.MINUTE, 59);
            dueDateCal.set(Calendar.SECOND, 59);
            dueDateCal.set(Calendar.MILLISECOND, 999);

            Calendar now = Calendar.getInstance();

            // DEBUG: Log the dates for troubleshooting
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault());
            Log.d(TAG, "Due Date: " + sdf.format(dueDateCal.getTime()));
            Log.d(TAG, "Current Date: " + sdf.format(now.getTime()));
            Log.d(TAG, "Due Date in ms: " + dueDateCal.getTimeInMillis());
            Log.d(TAG, "Current Date in ms: " + now.getTimeInMillis());

            // Check if current time is AFTER the due date (including time of day)
            if (now.getTimeInMillis() > dueDateCal.getTimeInMillis()) {
                // Calculate days late
                // We calculate based on calendar days, not 24-hour periods
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
                daysLate = diffInMillis / (1000 * 60 * 60 * 24);

                Log.d(TAG, "Days Late: " + daysLate);

                if (daysLate > 0) {
                    // Late return
                    isLateReturn = true;
                    latePenaltyAmount = Math.min(daysLate * PENALTY_PER_DAY, PENALTY_CAP);

                    // Update UI for late return (RED indicator)
                    updateDueDateIndicator(false, daysLate, endDate);

                    // Show late penalty section
                    llLatePenaltySection.setVisibility(View.VISIBLE);
                    tvLatePenaltyAmount.setText(String.format("RM %.2f", latePenaltyAmount));

                    String message = "Late return detected: " + daysLate + " day(s) late\n";
                    message += "Due: " + formatDate(endDate) + "\n";
                    message += "Returned: " + formatDate(now.getTimeInMillis());
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();

                    Log.d(TAG, "LATE RETURN: " + daysLate + " days, Penalty: RM " + latePenaltyAmount);
                } else {
                    // This should not happen, but just in case
                    isLateReturn = false;
                    latePenaltyAmount = 0.0;
                    updateDueDateIndicator(true, 0, endDate);
                    llLatePenaltySection.setVisibility(View.GONE);
                    Log.d(TAG, "Not late (daysLate <= 0)");
                }
            } else {
                // On time or early return (GREEN indicator)
                isLateReturn = false;
                latePenaltyAmount = 0.0;

                updateDueDateIndicator(true, 0, endDate);
                llLatePenaltySection.setVisibility(View.GONE);
                Log.d(TAG, "Return is ON TIME");
            }

            // Initial calculation
            calculateRefundAmount();
        } else {
            // No end date found
            tvDueDateStatus.setText("Date Not Available");
            tvDueDateLabel.setText("Due Date:");
            tvDueDateValue.setText("N/A");
            cvDueDateIndicator.setCardBackgroundColor(Color.parseColor("#FF9800")); // Orange for unknown
        }
    }

    private void updateDueDateIndicator(boolean isOnTime, long daysLate, Long dueDate) {
        if (isOnTime) {
            // Green indicator for on-time return
            cvDueDateIndicator.setCardBackgroundColor(Color.parseColor("#4CAF50")); // Green
            tvDueDateStatus.setText("On Time");
            tvDueDateLabel.setText("Due Date:");
            tvDueDateValue.setText(formatDate(dueDate));
        } else {
            // Red indicator for late return
            cvDueDateIndicator.setCardBackgroundColor(Color.parseColor("#F44336")); // Red
            tvDueDateStatus.setText("LATE RETURN");
            tvDueDateLabel.setText("Late by:");
            tvDueDateValue.setText(daysLate + " day(s)");
        }
    }

    private void loadBorrowerInfo() {
        if (renterId != null && !renterId.isEmpty()) {
            usersRef.child(renterId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String borrowerName = getStringValue(snapshot, "fullName");
                        if (borrowerName != null && !borrowerName.isEmpty()) {
                            tvBorrowerName.setText(borrowerName);
                            currentBorrowerName = borrowerName;
                            Log.d(TAG, "Borrower name loaded: " + borrowerName);
                        } else {
                            // Try alternative field names
                            borrowerName = getStringValue(snapshot, "name");
                            if (borrowerName != null && !borrowerName.isEmpty()) {
                                tvBorrowerName.setText(borrowerName);
                                currentBorrowerName = borrowerName;
                            } else {
                                // Try username or email as fallback
                                String username = getStringValue(snapshot, "username");
                                String email = getStringValue(snapshot, "email");

                                if (username != null && !username.isEmpty()) {
                                    tvBorrowerName.setText(username);
                                    currentBorrowerName = username;
                                } else if (email != null && !email.isEmpty()) {
                                    // Show first part of email
                                    String[] parts = email.split("@");
                                    if (parts.length > 0) {
                                        tvBorrowerName.setText(parts[0]);
                                        currentBorrowerName = parts[0];
                                    } else {
                                        tvBorrowerName.setText("Unknown");
                                        currentBorrowerName = "Unknown";
                                    }
                                } else {
                                    tvBorrowerName.setText("Unknown");
                                    currentBorrowerName = "Unknown";
                                }
                            }
                        }
                    } else {
                        tvBorrowerName.setText("User not found");
                        currentBorrowerName = "User not found";
                        Log.w(TAG, "User not found with ID: " + renterId);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    tvBorrowerName.setText("Error loading");
                    currentBorrowerName = "Error loading";
                    Log.e(TAG, "Failed to load user info: " + error.getMessage());
                }
            });
        } else {
            tvBorrowerName.setText("Unknown");
            currentBorrowerName = "Unknown";
            Log.w(TAG, "Cannot load borrower info - renterId is null or empty");
        }
    }

    private void loadOwnerInfoForNotifications() {
        if (ownerId != null && !ownerId.isEmpty()) {
            usersRef.child(ownerId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        currentOwnerName = getStringValue(snapshot, "fullName");
                        if (currentOwnerName == null || currentOwnerName.isEmpty()) {
                            currentOwnerName = getStringValue(snapshot, "name");
                            if (currentOwnerName == null || currentOwnerName.isEmpty()) {
                                String username = getStringValue(snapshot, "username");
                                if (username != null && !username.isEmpty()) {
                                    currentOwnerName = username;
                                } else {
                                    currentOwnerName = "Owner";
                                }
                            }
                        }
                        Log.d(TAG, "Owner name loaded for notifications: " + currentOwnerName);
                    } else {
                        currentOwnerName = "Owner";
                        Log.w(TAG, "Owner not found with ID: " + ownerId);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    currentOwnerName = "Owner";
                    Log.e(TAG, "Failed to load owner info: " + error.getMessage());
                }
            });
        } else {
            currentOwnerName = "Owner";
            Log.w(TAG, "Cannot load owner info - ownerId is null or empty");
        }
    }

    private void calculateRefundAmount() {
        // Get current repair cost from slider
        repairCostAmount = (double) sliderRepairCost.getValue();

        // Update repair cost deduction display
        tvRepairCostDeduction.setText(String.format("RM %.2f", repairCostAmount));

        // Calculate total deductions (repair cost + late penalty)
        double totalDeductions = repairCostAmount + latePenaltyAmount;

        // Update late penalty UI if applicable
        if (isLateReturn && latePenaltyAmount > 0) {
            llLatePenaltyDeduction.setVisibility(View.VISIBLE);
            tvLatePenaltyDeduction.setText(String.format("RM %.2f", latePenaltyAmount));
        } else {
            llLatePenaltyDeduction.setVisibility(View.GONE);
        }

        // Calculate refund amount
        double refundAmount = Math.max(0, originalDepositAmount - totalDeductions);

        // Update UI
        tvDeductions.setText(String.format("RM %.2f", totalDeductions));
        tvRefundAmount.setText(String.format("RM %.2f", refundAmount));
        tvFinalRefund.setText(String.format("RM %.2f", refundAmount));

        // Update button text based on refund amount
        if (refundAmount > 0) {
            needsRefundPayment = true;
            btnCompleteInspection.setText("Pay Refund");
        } else {
            needsRefundPayment = false;
            btnCompleteInspection.setText("Complete Rental");
        }

        Log.d(TAG, "Refund calculated: RM " + refundAmount +
                " (Deposit: " + originalDepositAmount +
                ", Repair: " + repairCostAmount +
                ", Penalty: " + latePenaltyAmount + ")");
    }

    private boolean validateItemCondition() {
        int checkedId = rgItemCondition.getCheckedRadioButtonId();
        if (checkedId == -1) {
            Toast.makeText(this, "Please select item condition", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private String getSelectedCondition() {
        int checkedId = rgItemCondition.getCheckedRadioButtonId();
        if (checkedId == R.id.rbExcellent) return "Excellent";
        if (checkedId == R.id.rbGood) return "Good";
        if (checkedId == R.id.rbFair) return "Fair";
        if (checkedId == R.id.rbPoor) return "Poor";
        return "";
    }

    /**
     * Case 1: No refund (refundAmount == 0) – directly complete rental.
     */
    private void completeInspectionWithoutPayment() {
        String itemCondition = getSelectedCondition();
        String damageNotes = etDamageNotes.getText().toString().trim();
        double refundAmount = 0.0;

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "Completed");
        updates.put("inspectionTime", System.currentTimeMillis());
        updates.put("completionTime", System.currentTimeMillis()); // Add completion time
        updates.put("itemCondition", itemCondition);
        updates.put("damageNotes", damageNotes);
        updates.put("repairCost", repairCostAmount);

        // CRITICAL: Always save penalty data if it exists (even if 0)
        updates.put("latePenalty", latePenaltyAmount);
        updates.put("daysLate", daysLate);
        updates.put("totalDeductions", repairCostAmount + latePenaltyAmount);
        updates.put("refundAmount", refundAmount);
        updates.put("depositReturned", true);
        updates.put("depositReturnDate", System.currentTimeMillis());

        // Also save the borrower ID for reference
        if (renterId != null && !renterId.isEmpty()) {
            updates.put("renterId", renterId);
        }

        Log.d(TAG, "Saving to Firebase:");
        Log.d(TAG, "- latePenalty: " + latePenaltyAmount);
        Log.d(TAG, "- daysLate: " + daysLate);
        Log.d(TAG, "- isLateReturn: " + isLateReturn);
        Log.d(TAG, "- refundAmount: " + refundAmount);
        Log.d(TAG, "- renterId: " + renterId);

        bookingsRef.child(bookingId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    // 🔔 SEND RENTAL COMPLETION NOTIFICATION
                    String completionMessage;
                    if (isLateReturn && latePenaltyAmount > 0) {
                        completionMessage = "Rental completed with late penalty: RM " +
                                String.format("%.2f", latePenaltyAmount);
                    } else {
                        completionMessage = "Rental completed successfully!";
                    }

                    // Only send notification if we have renterId
                    if (renterId != null && !renterId.isEmpty()) {
                        // 🔔 SEND COMPREHENSIVE NOTIFICATIONS
                        sendInspectionCompleteNotifications(
                                completionMessage,
                                itemCondition,
                                repairCostAmount,
                                latePenaltyAmount,
                                daysLate,
                                refundAmount
                        );


                        // 🔔 UPDATE PRODUCT STATUS TO AVAILABLE
                        updateProductStatusToAvailable();

                        // 🔔 SEND FCM NOTIFICATION
                        sendFCMInspectionCompleteNotification(completionMessage);
                    } else {
                        Log.w(TAG, "Cannot send notification - renterId is null");
                    }

                    String message = "Rental completed successfully";
                    if (isLateReturn && latePenaltyAmount > 0) {
                        message += "\nLate penalty applied: RM " + String.format("%.2f", latePenaltyAmount);
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    Log.d(TAG, "Firebase updated successfully");

                    Intent intent = new Intent(this, activity_rentals_details_owner.class);
                    intent.putExtra("bookingId", bookingId);
                    intent.putExtra("productId", productId);
                    intent.putExtra("ownerId", mAuth.getCurrentUser().getUid());
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to complete rental", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Failed to update Firebase: " + e.getMessage());
                });
    }

    // 🔔 NEW METHOD: Send comprehensive inspection completion notifications
    private void sendInspectionCompleteNotifications(String completionMessage,
                                                     String itemCondition,
                                                     double repairCost,
                                                     double penalty,
                                                     long daysLate,
                                                     double refundAmount) {

        // Use the stored names (loaded earlier)
        String ownerName = currentOwnerName != null ? currentOwnerName : "Owner";
        String borrowerName = currentBorrowerName != null ? currentBorrowerName : "Borrower";
        String productName = currentProductName != null ? currentProductName : "the product";

        // 🔔 NOTIFICATION FOR BORROWER: Inspection completed
        String borrowerTitle = "Inspection Completed";
        String borrowerMsg = "Owner " + ownerName + " has completed inspection of " + productName + ". ";

        if (penalty > 0) {
            borrowerMsg += "Late penalty applied: RM " + String.format("%.2f", penalty);
        } else if (repairCost > 0) {
            borrowerMsg += "Repair cost deducted: RM " + String.format("%.2f", repairCost);
        } else {
            borrowerMsg += "Item returned in good condition.";
        }

        if (refundAmount > 0) {
            borrowerMsg += " Refund: RM " + String.format("%.2f", refundAmount);
        }

        NotificationHelper.sendNotification(
                renterId,
                borrowerTitle,
                borrowerMsg,
                "inspection_completed",
                bookingId
        );

        // 🔔 NOTIFICATION FOR OWNER: Confirmation
        String ownerTitle = "Inspection Saved";
        String ownerMsg = "Inspection for " + productName + " completed. ";
        ownerMsg += "Condition: " + itemCondition + ". ";

        if (penalty > 0) {
            ownerMsg += "Late penalty: RM " + String.format("%.2f", penalty);
        }
        if (repairCost > 0) {
            ownerMsg += " Repair cost: RM " + String.format("%.2f", repairCost);
        }

        NotificationHelper.sendNotification(
                ownerId,
                ownerTitle,
                ownerMsg,
                "inspection_saved",
                bookingId
        );

        // 🔔 SEND LATE RETURN NOTIFICATION IF APPLICABLE
        if (isLateReturn && penalty > 0) {
            NotificationHelper.sendLateReturnNotification(
                    bookingId,
                    (int) daysLate,
                    penalty,
                    renterId
            );

            // Also notify owner about late penalty
            NotificationHelper.sendNotification(
                    ownerId,
                    "Late Penalty Applied",
                    "Late penalty of RM " + String.format("%.2f", penalty) +
                            " applied to " + borrowerName + "'s booking.",
                    "penalty_applied",
                    bookingId
            );
        }

        // 🔔 SEND REFUND NOTIFICATION IF NO REFUND NEEDED
        if (refundAmount == 0) {
            NotificationHelper.sendNotification(
                    renterId,
                    "Deposit Fully Used",
                    "Your deposit has been fully used for repairs/penalty.",
                    "deposit_used",
                    bookingId
            );
        }

        Log.d(TAG, "Notifications sent:");
        Log.d(TAG, "- To borrower: " + borrowerName);
        Log.d(TAG, "- To owner: " + ownerName);
        Log.d(TAG, "- Product: " + productName);
    }

    // 🔔 NEW METHOD: Update product status to available
    private void updateProductStatusToAvailable() {
        if (productId != null) {
            productsRef.child(productId).child("status").setValue("Available")
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Product status updated to Available: " + productId);

                        // 🔔 NOTIFICATION FOR OWNER: Product available
                        NotificationHelper.sendNotification(
                                ownerId,
                                "Product Available",
                                "Your product is now available for rent again.",
                                "product_available",
                                productId
                        );
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to update product status: " + e.getMessage());
                    });
        }
    }

    // 🔔 NEW METHOD: Send FCM notification
    private void sendFCMInspectionCompleteNotification(String completionMessage) {
        // This would call your FCM service
        // For now, we'll log it
        Log.d(TAG, "FCM Inspection Notification would be sent:");
        Log.d(TAG, "- Booking: " + bookingId);
        Log.d(TAG, "- Renter: " + renterId);
        Log.d(TAG, "- Message: " + completionMessage);
    }

    /**
     * Case 2: Refund > 0 – go to dummy payment page.
     */
    private void openRefundPaymentPage() {
        String itemCondition = getSelectedCondition();
        String damageNotes = etDamageNotes.getText().toString().trim();

        // Parse refund amount from UI
        String refundText = tvFinalRefund.getText().toString().replace("RM ", "").trim();
        double refundAmount = 0.0;
        try {
            refundAmount = Double.parseDouble(refundText);
        } catch (NumberFormatException ignored) {}

        Log.d(TAG, "Opening refund payment page:");
        Log.d(TAG, "- latePenalty: " + latePenaltyAmount);
        Log.d(TAG, "- daysLate: " + daysLate);
        Log.d(TAG, "- refundAmount: " + refundAmount);
        Log.d(TAG, "- isLateReturn: " + isLateReturn);
        Log.d(TAG, "- renterId: " + renterId);

        // 🔔 SEND REFUND PENDING NOTIFICATION
        sendRefundPendingNotification(refundAmount);

        Intent intent = new Intent(this, activity_refund_payment.class);
        intent.putExtra("bookingId", bookingId);
        intent.putExtra("productId", productId);
        intent.putExtra("renterId", renterId != null ? renterId : "");
        intent.putExtra("itemCondition", itemCondition);
        intent.putExtra("damageNotes", damageNotes);
        intent.putExtra("repairCost", repairCostAmount);
        intent.putExtra("latePenalty", latePenaltyAmount);
        intent.putExtra("daysLate", daysLate);
        intent.putExtra("isLateReturn", isLateReturn); // Add this flag
        intent.putExtra("refundAmount", refundAmount);
        startActivity(intent);
    }

    // 🔔 NEW METHOD: Send refund pending notification
    private void sendRefundPendingNotification(double refundAmount) {
        // Use stored product name
        String productName = currentProductName != null ? currentProductName : "the product";

        // 🔔 NOTIFICATION FOR BORROWER: Refund pending
        String borrowerMessage = "Refund of RM " + String.format("%.2f", refundAmount) +
                " pending for " + productName + ". Proceed to payment page.";

        NotificationHelper.sendNotification(
                renterId,
                "Refund Available",
                borrowerMessage,
                "refund_pending",
                bookingId
        );

        // 🔔 NOTIFICATION FOR OWNER: Refund processing
        String ownerMessage = "Refund of RM " + String.format("%.2f", refundAmount) +
                " needs to be processed for " + productName + ".";

        NotificationHelper.sendNotification(
                ownerId,
                "Refund Required",
                ownerMessage,
                "refund_required",
                bookingId
        );

        Log.d(TAG, "Refund notifications sent for RM " + refundAmount);
    }

    // Helper methods
    private String getStringValue(DataSnapshot snapshot, String key) {
        DataSnapshot child = snapshot.child(key);
        return child.exists() && child.getValue() != null ? child.getValue().toString() : null;
    }

    private Long getLongValue(DataSnapshot snapshot, String key) {
        DataSnapshot child = snapshot.child(key);
        if (child.exists()) {
            Object value = child.getValue();
            if (value instanceof Long) return (Long) value;
            if (value instanceof Integer) return ((Integer) value).longValue();
            if (value instanceof String) {
                try { return Long.parseLong((String) value); } catch (NumberFormatException e) { return null; }
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
                try { return Double.parseDouble((String) value); } catch (NumberFormatException e) { return null; }
            }
        }
        return null;
    }

    private String formatDate(Long timestamp) {
        if (timestamp == null) return "N/A";
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
}
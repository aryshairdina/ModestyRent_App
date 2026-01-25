package com.example.modestyrent_app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.text.SimpleDateFormat;
import java.util.*;

public class activity_inspection extends AppCompatActivity {

    private String bookingId, productId, renterId, ownerId;

    // UI Components
    private ImageView backButton;
    private TextView tvProductName, tvBorrowerName, tvRentalPeriod, tvReturnMethod;
    private RadioGroup rgItemCondition;
    private RadioButton rbExcellent, rbGood, rbFair, rbPoor;
    private EditText etDamageNotes;
    private TextView tvOriginalDeposit, tvDeductions, tvRefundAmount, tvFinalRefund;
    private MaterialButton btnCompleteInspection;

    // New UI Components for late return indicator
    private CardView cvDueDateIndicator;
    private TextView tvDueDateStatus, tvDueDateLabel, tvDueDateValue, tvLatePenaltyAmount, tvConditionDeduction, tvLatePenaltyDeduction;
    private LinearLayout llLatePenaltySection, llLatePenaltyDeduction, llConditionDeduction;

    // Database References
    private DatabaseReference bookingsRef, usersRef, productsRef;
    private FirebaseAuth mAuth;

    // Calculation fields
    private double originalDepositAmount = 150.0;
    private double latePenaltyAmount = 0.0;
    private double conditionRefundAmount = 150.0; // Default to excellent
    private double repairCostAmount = 0.0; // This will store condition-based deduction
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

        // Damage notes
        etDamageNotes = findViewById(R.id.etDamageNotes);

        // Deposit calculation views
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

        // Condition deduction views
        tvConditionDeduction = findViewById(R.id.tvConditionDeduction);
        tvLatePenaltyDeduction = findViewById(R.id.tvLatePenaltyDeduction);
        llLatePenaltyDeduction = findViewById(R.id.llLatePenaltyDeduction);
        llConditionDeduction = findViewById(R.id.llConditionDeduction);

        backButton.setOnClickListener(v -> finish());
    }

    private void setupFirebase() {
        bookingsRef = FirebaseDatabase.getInstance().getReference("bookings");
        usersRef = FirebaseDatabase.getInstance().getReference("users");
        productsRef = FirebaseDatabase.getInstance().getReference("products");
    }

    private void setupListeners() {
        // Condition radio buttons listener - KEEP YOUR ORIGINAL LOGIC
        CompoundButton.OnCheckedChangeListener conditionListener = (buttonView, isChecked) -> {
            if (!isChecked) return;

            // Uncheck others
            if (buttonView != rbExcellent) rbExcellent.setChecked(false);
            if (buttonView != rbGood) rbGood.setChecked(false);
            if (buttonView != rbFair) rbFair.setChecked(false);
            if (buttonView != rbPoor) rbPoor.setChecked(false);

            // Update RadioGroup
            rgItemCondition.check(buttonView.getId());

            // Calculate refund based on selected condition
            updateConditionDeduction(buttonView.getId());
            calculateRefundAmount();
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

    private void updateConditionDeduction(int checkedId) {
        // Set refund amount based on condition
        if (checkedId == rbExcellent.getId()) {
            conditionRefundAmount = 150.0;
            repairCostAmount = 0.0; // No deduction for excellent
        } else if (checkedId == rbGood.getId()) {
            conditionRefundAmount = 120.0;
            repairCostAmount = 30.0; // RM30 deduction for good condition
        } else if (checkedId == rbFair.getId()) {
            conditionRefundAmount = 80.0;
            repairCostAmount = 70.0; // RM70 deduction for fair condition
        } else if (checkedId == rbPoor.getId()) {
            conditionRefundAmount = 0.0;
            repairCostAmount = 150.0; // Full deposit deduction for poor condition
        } else {
            conditionRefundAmount = 150.0; // Default to excellent
            repairCostAmount = 0.0;
        }

        // Update condition deduction display
        double conditionDeduction = originalDepositAmount - conditionRefundAmount;
        if (tvConditionDeduction != null) {
            tvConditionDeduction.setText(String.format("RM %.2f", repairCostAmount));
        }
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
                        }

                        // Get ownerId from booking if not already set
                        String bookingOwnerId = getStringValue(snapshot, "ownerId");
                        if (bookingOwnerId != null && !bookingOwnerId.isEmpty()) {
                            ownerId = bookingOwnerId;
                        }

                        // Load borrower info if we have renterId
                        if (renterId != null && !renterId.isEmpty()) {
                            loadBorrowerInfo();
                        } else {
                            tvBorrowerName.setText("Unknown Borrower");
                        }

                        checkAndCalculateLatePenalty(snapshot);
                    } catch (Exception e) {
                        Toast.makeText(activity_inspection.this, "Error loading booking details", Toast.LENGTH_SHORT).show();
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
        } else {
            tvProductName.setText("Product name not available");
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
            originalDepositAmount = 150.0;
            tvOriginalDeposit.setText("RM 150.00");
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
                } else {
                    // This should not happen, but just in case
                    isLateReturn = false;
                    latePenaltyAmount = 0.0;
                    updateDueDateIndicator(true, 0, endDate);
                    llLatePenaltySection.setVisibility(View.GONE);
                }
            } else {
                // On time or early return (GREEN indicator)
                isLateReturn = false;
                latePenaltyAmount = 0.0;

                updateDueDateIndicator(true, 0, endDate);
                llLatePenaltySection.setVisibility(View.GONE);
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
                        } else {
                            // Try alternative field names
                            borrowerName = getStringValue(snapshot, "name");
                            if (borrowerName != null && !borrowerName.isEmpty()) {
                                tvBorrowerName.setText(borrowerName);
                            } else {
                                // Try username or email as fallback
                                String username = getStringValue(snapshot, "username");
                                String email = getStringValue(snapshot, "email");

                                if (username != null && !username.isEmpty()) {
                                    tvBorrowerName.setText(username);
                                } else if (email != null && !email.isEmpty()) {
                                    // Show first part of email
                                    String[] parts = email.split("@");
                                    if (parts.length > 0) {
                                        tvBorrowerName.setText(parts[0]);
                                    } else {
                                        tvBorrowerName.setText("Unknown");
                                    }
                                } else {
                                    tvBorrowerName.setText("Unknown");
                                }
                            }
                        }
                    } else {
                        tvBorrowerName.setText("User not found");
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    tvBorrowerName.setText("Error loading");
                }
            });
        } else {
            tvBorrowerName.setText("Unknown");
        }
    }

    private void calculateRefundAmount() {
        // Show condition deduction section
        llConditionDeduction.setVisibility(View.VISIBLE);

        // Calculate condition deduction (which is repairCostAmount)
        double conditionDeduction = repairCostAmount;

        // Calculate total deductions (condition deduction + late penalty)
        double totalDeductions = conditionDeduction + latePenaltyAmount;

        // Update condition deduction display (show as repair cost)
        tvConditionDeduction.setText(String.format("RM %.2f", repairCostAmount));

        // Update late penalty UI if applicable
        if (isLateReturn && latePenaltyAmount > 0) {
            llLatePenaltyDeduction.setVisibility(View.VISIBLE);
            tvLatePenaltyDeduction.setText(String.format("RM %.2f", latePenaltyAmount));
        } else {
            llLatePenaltyDeduction.setVisibility(View.GONE);
        }

        // Calculate refund amount (what will be returned to renter)
        // Refund = Original Deposit - Total Deductions
        double refundAmount = originalDepositAmount - totalDeductions;

        // Ensure refund is not negative
        if (refundAmount < 0) {
            refundAmount = 0;
        }

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
        updates.put("conditionRefundAmount", conditionRefundAmount); // Save condition-based refund
        updates.put("repairCost", repairCostAmount); // Save repair cost (which is condition deduction)

        // CRITICAL: Always save penalty data if it exists (even if 0)
        updates.put("latePenalty", latePenaltyAmount);
        updates.put("daysLate", daysLate);
        updates.put("conditionDeduction", repairCostAmount); // Same as repairCostAmount
        updates.put("totalDeductions", repairCostAmount + latePenaltyAmount); // Total deductions
        updates.put("refundAmount", refundAmount);
        updates.put("depositReturned", true);
        updates.put("depositReturnDate", System.currentTimeMillis());

        // Also save the borrower ID for reference
        if (renterId != null && !renterId.isEmpty()) {
            updates.put("renterId", renterId);
        }

        bookingsRef.child(bookingId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    String message = "Rental completed successfully";
                    if (isLateReturn && latePenaltyAmount > 0) {
                        message += "\nLate penalty applied: RM " + String.format("%.2f", latePenaltyAmount);
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();

                    Intent intent = new Intent(this, activity_rentals_details_owner.class);
                    intent.putExtra("bookingId", bookingId);
                    intent.putExtra("productId", productId);
                    intent.putExtra("ownerId", mAuth.getCurrentUser().getUid());
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to complete rental", Toast.LENGTH_SHORT).show();
                });
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

        Intent intent = new Intent(this, activity_refund_payment.class);
        intent.putExtra("bookingId", bookingId);
        intent.putExtra("productId", productId);
        intent.putExtra("renterId", renterId != null ? renterId : "");
        intent.putExtra("itemCondition", itemCondition);
        intent.putExtra("damageNotes", damageNotes);
        intent.putExtra("conditionRefundAmount", conditionRefundAmount);
        intent.putExtra("repairCost", repairCostAmount);
        intent.putExtra("latePenalty", latePenaltyAmount);
        intent.putExtra("daysLate", daysLate);
        intent.putExtra("isLateReturn", isLateReturn); // Add this flag
        intent.putExtra("refundAmount", refundAmount);
        startActivity(intent);
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
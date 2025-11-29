package com.example.modestyrent_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.text.SimpleDateFormat;
import java.util.*;

public class activity_inspection extends AppCompatActivity {

    private String bookingId, productId, renterId;

    private ImageView backButton;
    private TextView tvProductName, tvBorrowerName, tvRentalPeriod, tvReturnMethod;
    private RadioGroup rgItemCondition;
    private RadioButton rbExcellent, rbGood, rbFair, rbPoor;
    private EditText etDamageNotes;
    private Slider sliderRepairCost;
    private TextView tvRepairCost, tvOriginalDeposit, tvDeductions, tvRefundAmount, tvFinalRefund;
    private MaterialButton btnCompleteInspection;

    private DatabaseReference bookingsRef, usersRef, productsRef;
    private FirebaseAuth mAuth;

    // New fields
    private double originalDepositAmount = 50.0;   // default, will be overridden by DB
    private boolean needsRefundPayment = false;    // true if refundAmount > 0

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inspection);

        // Get intent data
        Intent intent = getIntent();
        bookingId = intent.getStringExtra("bookingId");
        productId = intent.getStringExtra("productId");
        renterId = intent.getStringExtra("renterId");

        if (bookingId == null) {
            Toast.makeText(this, "Booking ID not provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        mAuth = FirebaseAuth.getInstance();

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

        rgItemCondition = findViewById(R.id.rgItemCondition);
        rbExcellent = findViewById(R.id.rbExcellent);
        rbGood = findViewById(R.id.rbGood);
        rbFair = findViewById(R.id.rbFair);
        rbPoor = findViewById(R.id.rbPoor);

        etDamageNotes = findViewById(R.id.etDamageNotes);
        sliderRepairCost = findViewById(R.id.sliderRepairCost);
        tvRepairCost = findViewById(R.id.tvRepairCost);
        tvOriginalDeposit = findViewById(R.id.tvOriginalDeposit);
        tvDeductions = findViewById(R.id.tvDeductions);
        tvRefundAmount = findViewById(R.id.tvRefundAmount);
        tvFinalRefund = findViewById(R.id.tvFinalRefund);
        btnCompleteInspection = findViewById(R.id.btnCompleteInspection);

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
            tvRepairCost.setText(String.format("RM %.2f", value));
            calculateRefundAmount();
        });

        // Ensure ONLY ONE condition radio is selected at a time
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

        // Main action button
        btnCompleteInspection.setOnClickListener(v -> {
            if (!validateItemCondition()) return;

            // Decide: pay refund OR complete rental directly
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
                        loadBorrowerInfo();
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
        tvProductName.setText(getStringValue(bookingSnapshot, "productName"));

        // Rental period
        Long startDate = getLongValue(bookingSnapshot, "startDate");
        Long endDate = getLongValue(bookingSnapshot, "endDate");

        if (startDate != null && endDate != null) {
            String periodText = formatDate(startDate) + " - " + formatDate(endDate);
            tvRentalPeriod.setText(periodText);
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
            originalDepositAmount = 50.0;
            tvOriginalDeposit.setText("RM 50.00");
        }

        // Initial calculation
        calculateRefundAmount();
    }

    private void loadBorrowerInfo() {
        if (renterId != null) {
            usersRef.child(renterId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String borrowerName = getStringValue(snapshot, "fullName");
                        if (borrowerName != null) {
                            tvBorrowerName.setText(borrowerName);
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    // ignore
                }
            });
        }
    }

    private void calculateRefundAmount() {
        // Only use repair cost as deduction
        double repairCost = (double) sliderRepairCost.getValue();

        double totalDeductions = repairCost;
        double refundAmount = Math.max(0, originalDepositAmount - totalDeductions);

        tvDeductions.setText(String.format("RM %.2f", totalDeductions));
        tvRefundAmount.setText(String.format("RM %.2f", refundAmount));
        tvFinalRefund.setText(String.format("RM %.2f", refundAmount));

        // Decide which action is needed
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
        double repairCost = (double) sliderRepairCost.getValue();

        double refundAmount = 0.0; // by definition in this case

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "Completed");
        updates.put("inspectionTime", System.currentTimeMillis());
        updates.put("itemCondition", itemCondition);
        updates.put("damageNotes", damageNotes);
        updates.put("repairCost", repairCost);
        updates.put("refundAmount", refundAmount);
        updates.put("depositReturned", true);
        updates.put("depositReturnDate", System.currentTimeMillis());

        bookingsRef.child(bookingId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Rental completed successfully", Toast.LENGTH_SHORT).show();

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
        double repairCost = (double) sliderRepairCost.getValue();

        // Parse refund amount from UI
        String refundText = tvFinalRefund.getText().toString().replace("RM ", "").trim();
        double refundAmount = 0.0;
        try {
            refundAmount = Double.parseDouble(refundText);
        } catch (NumberFormatException ignored) {}

        Intent intent = new Intent(this, activity_refund_payment.class);
        intent.putExtra("bookingId", bookingId);
        intent.putExtra("productId", productId);
        intent.putExtra("renterId", renterId);
        intent.putExtra("itemCondition", itemCondition);
        intent.putExtra("damageNotes", damageNotes);
        intent.putExtra("repairCost", repairCost);
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
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(timestamp));
    }
}

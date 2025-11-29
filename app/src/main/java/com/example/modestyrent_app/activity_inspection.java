package com.example.modestyrent_app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;
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
    private SwitchMaterial switchCleaningRequired;
    private Slider sliderRepairCost;
    private TextView tvRepairCost, tvOriginalDeposit, tvDeductions, tvRefundAmount, tvFinalRefund;
    private MaterialButton btnCompleteInspection;

    private DatabaseReference bookingsRef, usersRef, productsRef;
    private FirebaseAuth mAuth;

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
        switchCleaningRequired = findViewById(R.id.switchCleaningRequired);
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

        // Item condition radio group listener
        rgItemCondition.setOnCheckedChangeListener((group, checkedId) -> {
            calculateRefundAmount();
        });

        // Cleaning required switch listener
        switchCleaningRequired.setOnCheckedChangeListener((buttonView, isChecked) -> {
            calculateRefundAmount();
        });

        // Complete inspection button
        btnCompleteInspection.setOnClickListener(v -> completeInspection());
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

        // Set original deposit
        Double depositAmount = getDoubleValue(bookingSnapshot, "depositAmount");
        if (depositAmount != null) {
            tvOriginalDeposit.setText(String.format("RM %.2f", depositAmount));
        }
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
                    // Handle error
                }
            });
        }
    }

    private void calculateRefundAmount() {
        // Get original deposit amount
        double originalDeposit = 50.00; // Default, should come from booking data

        // Calculate deductions
        double repairCost = (double) sliderRepairCost.getValue();
        double cleaningCost = switchCleaningRequired.isChecked() ? 10.00 : 0.00;

        // Additional deductions based on item condition
        int checkedId = rgItemCondition.getCheckedRadioButtonId();
        double conditionDeduction = 0.00;

        if (checkedId == R.id.rbPoor) {
            conditionDeduction = 20.00;
        } else if (checkedId == R.id.rbFair) {
            conditionDeduction = 10.00;
        }

        double totalDeductions = repairCost + cleaningCost + conditionDeduction;
        double refundAmount = Math.max(0, originalDeposit - totalDeductions);

        // Update UI
        tvDeductions.setText(String.format("RM %.2f", totalDeductions));
        tvRefundAmount.setText(String.format("RM %.2f", refundAmount));
        tvFinalRefund.setText(String.format("RM %.2f", refundAmount));
    }

    private void completeInspection() {
        int checkedId = rgItemCondition.getCheckedRadioButtonId();

        if (checkedId == -1) {
            Toast.makeText(this, "Please select item condition", Toast.LENGTH_SHORT).show();
            return;
        }

        String itemCondition = "";
        if (checkedId == R.id.rbExcellent) {
            itemCondition = "Excellent";
        } else if (checkedId == R.id.rbGood) {
            itemCondition = "Good";
        } else if (checkedId == R.id.rbFair) {
            itemCondition = "Fair";
        } else if (checkedId == R.id.rbPoor) {
            itemCondition = "Poor";
        }

        String damageNotes = etDamageNotes.getText().toString().trim();
        boolean cleaningRequired = switchCleaningRequired.isChecked();
        double repairCost = (double) sliderRepairCost.getValue();
        double refundAmount = Double.parseDouble(tvFinalRefund.getText().toString().replace("RM ", ""));

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "Completed");
        updates.put("inspectionTime", System.currentTimeMillis());
        updates.put("itemCondition", itemCondition);
        updates.put("damageNotes", damageNotes);
        updates.put("cleaningRequired", cleaningRequired);
        updates.put("repairCost", repairCost);
        updates.put("refundAmount", refundAmount);
        updates.put("depositReturned", true);
        updates.put("depositReturnDate", System.currentTimeMillis());

        bookingsRef.child(bookingId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Inspection completed successfully", Toast.LENGTH_SHORT).show();

                    // Navigate back to rental details
                    Intent intent = new Intent(this, activity_rentals_details_owner.class);
                    intent.putExtra("bookingId", bookingId);
                    intent.putExtra("productId", productId);
                    intent.putExtra("ownerId", mAuth.getCurrentUser().getUid());
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to complete inspection", Toast.LENGTH_SHORT).show();
                });
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
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
}
package com.example.modestyrent_app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

public class activity_refund_payment extends AppCompatActivity {

    private String bookingId, productId, renterId;
    private String itemCondition, damageNotes;
    private double repairCost, refundAmount, latePenalty;
    private long daysLate;
    private boolean isLateReturn;

    private ImageView backButton;
    private TextView tvRefundAmount, tvPaymentSummary, tvPaymentMethodLabel, tvBottomRefundAmount;
    private RadioGroup rgPaymentMethod;
    private RadioButton rbOnlineBanking, rbCard, rbEwallet;
    private MaterialButton btnConfirmPayment;

    private DatabaseReference bookingsRef, productsRef;
    private FirebaseAuth mAuth;
    private NotificationManager notificationManager;

    private static final String TAG = "RefundPayment";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_refund_payment);

        // Initialize Notification Manager
        notificationManager = new NotificationManager(this);

        // Get data from inspection screen
        Intent intent = getIntent();
        bookingId = intent.getStringExtra("bookingId");
        productId = intent.getStringExtra("productId");
        renterId = intent.getStringExtra("renterId");
        itemCondition = intent.getStringExtra("itemCondition");
        damageNotes = intent.getStringExtra("damageNotes");
        repairCost = intent.getDoubleExtra("repairCost", 0.0);
        refundAmount = intent.getDoubleExtra("refundAmount", 0.0);

        // CRITICAL: Get late penalty data
        latePenalty = intent.getDoubleExtra("latePenalty", 0.0);
        daysLate = intent.getLongExtra("daysLate", 0);
        isLateReturn = intent.getBooleanExtra("isLateReturn", false);

        if (bookingId == null) {
            Toast.makeText(this, "Booking ID not provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        mAuth = FirebaseAuth.getInstance();
        bookingsRef = FirebaseDatabase.getInstance().getReference("bookings");
        productsRef = FirebaseDatabase.getInstance().getReference("products");

        // Debug logging
        Log.d(TAG, "Refund Payment Activity Started:");
        Log.d(TAG, "- Booking ID: " + bookingId);
        Log.d(TAG, "- Product ID: " + productId);
        Log.d(TAG, "- Renter ID: " + renterId);
        Log.d(TAG, "- Refund Amount: RM " + refundAmount);
        Log.d(TAG, "- Late Penalty: RM " + latePenalty);
        Log.d(TAG, "- Days Late: " + daysLate);
        Log.d(TAG, "- Is Late Return: " + isLateReturn);
        Log.d(TAG, "- Repair Cost: RM " + repairCost);

        initializeViews();
        setupUI();
        setupListeners();
    }

    private void initializeViews() {
        backButton = findViewById(R.id.backButton);
        tvRefundAmount = findViewById(R.id.tvRefundAmount);
        tvPaymentSummary = findViewById(R.id.tvPaymentSummary);
        tvPaymentMethodLabel = findViewById(R.id.tvPaymentMethodLabel);
        rgPaymentMethod = findViewById(R.id.rgPaymentMethod);
        rbOnlineBanking = findViewById(R.id.rbOnlineBanking);
        rbCard = findViewById(R.id.rbCard);
        rbEwallet = findViewById(R.id.rbEwallet);
        btnConfirmPayment = findViewById(R.id.btnConfirmPayment);
        tvBottomRefundAmount = findViewById(R.id.tvBottomRefundAmount);

        backButton.setOnClickListener(v -> finish());
    }

    private void setupUI() {
        tvRefundAmount.setText(String.format("RM %.2f", refundAmount));
        tvBottomRefundAmount.setText(String.format("RM %.2f", refundAmount));

        // Update payment summary to include penalty information
        String summaryText;
        if (isLateReturn && latePenalty > 0) {
            summaryText = String.format(
                    "You will refund RM %.2f to the borrower.\n\nBreakdown:\n" +
                            "- Damage/repair cost: RM %.2f\n" +
                            "- Late return penalty (%d days): RM %.2f\n" +
                            "- Total deductions: RM %.2f",
                    refundAmount,
                    repairCost,
                    daysLate,
                    latePenalty,
                    (repairCost + latePenalty)
            );
        } else {
            summaryText = String.format(
                    "You will refund RM %.2f to the borrower.\n" +
                            "Damage/repair cost: RM %.2f",
                    refundAmount, repairCost
            );
        }
        tvPaymentSummary.setText(summaryText);
    }

    private void setupListeners() {
        // Make sure ONLY ONE payment method can be selected at a time
        CompoundButton.OnCheckedChangeListener paymentListener = (buttonView, isChecked) -> {
            if (!isChecked) return;

            // Manually uncheck others
            if (buttonView != rbOnlineBanking) rbOnlineBanking.setChecked(false);
            if (buttonView != rbCard) rbCard.setChecked(false);
            if (buttonView != rbEwallet) rbEwallet.setChecked(false);

            // Sync with RadioGroup so getCheckedRadioButtonId() works
            rgPaymentMethod.check(buttonView.getId());
        };

        rbOnlineBanking.setOnCheckedChangeListener(paymentListener);
        rbCard.setOnCheckedChangeListener(paymentListener);
        rbEwallet.setOnCheckedChangeListener(paymentListener);

        btnConfirmPayment.setOnClickListener(v -> {
            if (!validatePaymentMethod()) return;
            processRefundPayment();
        });
    }

    private boolean validatePaymentMethod() {
        int checkedId = rgPaymentMethod.getCheckedRadioButtonId();
        if (checkedId == -1) {
            Toast.makeText(this, "Please select a payment method", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void processRefundPayment() {
        // Disable button to prevent double-click
        btnConfirmPayment.setEnabled(false);
        btnConfirmPayment.setText("Processing...");

        // Calculate total deductions
        double totalDeductions = repairCost + latePenalty;

        // Get current user ID (owner)
        String currentUserId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : "";

        // FIRST: Get product name from products collection using productId
        if (productId != null && !productId.isEmpty()) {
            productsRef.child(productId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot productSnapshot) {
                    String productName = "the item"; // Default value

                    if (productSnapshot.exists()) {
                        // Try different field names for product name
                        if (productSnapshot.child("name").exists()) {
                            productName = productSnapshot.child("name").getValue(String.class);
                            Log.d(TAG, "Got product name from 'name' field: " + productName);
                        } else if (productSnapshot.child("productName").exists()) {
                            productName = productSnapshot.child("productName").getValue(String.class);
                            Log.d(TAG, "Got product name from 'productName' field: " + productName);
                        } else if (productSnapshot.child("title").exists()) {
                            productName = productSnapshot.child("title").getValue(String.class);
                            Log.d(TAG, "Got product name from 'title' field: " + productName);
                        }

                        if (productName == null || productName.isEmpty()) {
                            productName = "the item";
                            Log.d(TAG, "Product name empty, using default");
                        }

                        Log.d(TAG, "Final product name: " + productName);
                    } else {
                        Log.w(TAG, "Product not found in products collection with ID: " + productId);

                        // Fallback: Try to get from bookings collection
                        getProductNameFromBooking(productName);
                        return;
                    }

                    // Continue with refund processing
                    completeRefundProcessing(productName, currentUserId);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(activity_refund_payment.this,
                            "Failed to load product details", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Database error loading product: " + error.getMessage());
                    btnConfirmPayment.setEnabled(true);
                    btnConfirmPayment.setText("Confirm Payment");
                }
            });
        } else {
            Toast.makeText(this, "Product ID not available", Toast.LENGTH_SHORT).show();
            btnConfirmPayment.setEnabled(true);
            btnConfirmPayment.setText("Confirm Payment");
        }
    }

    private void getProductNameFromBooking(String fallbackName) {
        // Fallback method: Get product name from booking data
        bookingsRef.child(bookingId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot bookingSnapshot) {
                String productName = fallbackName;

                if (bookingSnapshot.exists()) {
                    // Try to get product name from booking
                    String bookingProductName = bookingSnapshot.child("productName").getValue(String.class);
                    if (bookingProductName != null && !bookingProductName.isEmpty()) {
                        productName = bookingProductName;
                        Log.d(TAG, "Got product name from booking: " + productName);
                    }
                }

                // Get current user ID (owner)
                String currentUserId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : "";

                // Continue with refund processing
                completeRefundProcessing(productName, currentUserId);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Use fallback name if booking also fails
                String currentUserId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : "";
                completeRefundProcessing(fallbackName, currentUserId);
            }
        });
    }

    private void completeRefundProcessing(String productName, String currentUserId) {
        // Calculate total deductions
        double totalDeductions = repairCost + latePenalty;

        // Simulate successful refund payment
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "Completed");
        updates.put("inspectionTime", System.currentTimeMillis());
        updates.put("itemCondition", itemCondition);
        updates.put("damageNotes", damageNotes);
        updates.put("repairCost", repairCost);

        // CRITICAL: Save late penalty data to Firebase
        updates.put("latePenalty", latePenalty);
        updates.put("daysLate", daysLate);
        updates.put("totalDeductions", totalDeductions);

        updates.put("refundAmount", refundAmount);
        updates.put("depositReturned", true);
        updates.put("depositReturnDate", System.currentTimeMillis());
        updates.put("refundStatus", "completed");
        updates.put("refundDate", System.currentTimeMillis());

        // Debug logging
        Log.d(TAG, "Processing refund with:");
        Log.d(TAG, "- Product Name: " + productName);
        Log.d(TAG, "- Current User ID (owner): " + currentUserId);
        Log.d(TAG, "- Renter ID (notification receiver): " + renterId);
        Log.d(TAG, "- Refund Amount: RM " + refundAmount);

        bookingsRef.child(bookingId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    // SEND NOTIFICATION TO RENTER
                    if (renterId != null && !renterId.isEmpty()) {
                        Map<String, Object> extraData = new HashMap<>();
                        extraData.put("refundAmount", refundAmount);
                        extraData.put("repairCost", repairCost);
                        extraData.put("latePenalty", latePenalty);
                        extraData.put("daysLate", daysLate);
                        extraData.put("totalDeductions", totalDeductions);
                        extraData.put("refundDate", System.currentTimeMillis());
                        extraData.put("productName", productName);

                        Log.d(TAG, "Sending notification to renter ID: " + renterId);

                        // Send notification to renter (borrower)
                        notificationManager.sendBorrowerNotification(
                                "completed_refund",
                                bookingId,
                                productId,
                                renterId,        // Renter receives the notification
                                productName,     // Product name for the message
                                extraData
                        );

                        Log.d(TAG, "Notification sent successfully");
                    } else {
                        Log.w(TAG, "Cannot send notification: renterId is null or empty");
                    }

                    String message = "Refund paid and rental completed";
                    if (isLateReturn && latePenalty > 0) {
                        message += "\nLate penalty of RM " + String.format("%.2f", latePenalty) +
                                " applied for " + daysLate + " day(s) late";
                    }
                    Toast.makeText(activity_refund_payment.this, message, Toast.LENGTH_LONG).show();

                    Log.d(TAG, "Firebase updated successfully with refund data");

                    // Navigate back to owner's rental details
                    Intent intent = new Intent(activity_refund_payment.this,
                            activity_rentals_details_owner.class);
                    intent.putExtra("bookingId", bookingId);
                    intent.putExtra("productId", productId);
                    intent.putExtra("ownerId", currentUserId);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(activity_refund_payment.this,
                            "Failed to process refund: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Failed to update Firebase: " + e.getMessage());
                    btnConfirmPayment.setEnabled(true);
                    btnConfirmPayment.setText("Confirm Payment");
                });
    }

    // Optional helper
    private String formatDate(Long timestamp) {
        if (timestamp == null) return "N/A";
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(timestamp));
    }
}
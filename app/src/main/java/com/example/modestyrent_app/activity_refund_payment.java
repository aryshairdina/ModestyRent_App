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

    private DatabaseReference bookingsRef;
    private FirebaseAuth mAuth;

    private static final String TAG = "RefundPayment";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_refund_payment);

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

        // Debug logging
        Log.d(TAG, "Refund Payment Activity Started:");
        Log.d(TAG, "- Booking ID: " + bookingId);
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
        // 🔒 Make sure ONLY ONE payment method can be selected at a time
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

        // Debug logging
        Log.d(TAG, "Saving to Firebase:");
        Log.d(TAG, "- Status: Completed");
        Log.d(TAG, "- latePenalty: RM " + latePenalty);
        Log.d(TAG, "- daysLate: " + daysLate);
        Log.d(TAG, "- totalDeductions: RM " + totalDeductions);
        Log.d(TAG, "- refundAmount: RM " + refundAmount);
        Log.d(TAG, "- isLateReturn: " + isLateReturn);

        bookingsRef.child(bookingId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    String message = "Refund paid and rental completed";
                    if (isLateReturn && latePenalty > 0) {
                        message += "\nLate penalty of RM " + String.format("%.2f", latePenalty) +
                                " applied for " + daysLate + " day(s) late";
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();

                    Log.d(TAG, "Firebase updated successfully with penalty data");

                    Intent intent = new Intent(this, activity_rentals_details_owner.class);
                    intent.putExtra("bookingId", bookingId);
                    intent.putExtra("productId", productId);
                    intent.putExtra("ownerId",
                            mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to process refund", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Failed to update Firebase: " + e.getMessage());
                });
    }

    // Optional helper
    private String formatDate(Long timestamp) {
        if (timestamp == null) return "N/A";
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(timestamp));
    }
}
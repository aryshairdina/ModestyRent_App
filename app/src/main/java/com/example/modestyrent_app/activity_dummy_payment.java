package com.example.modestyrent_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class activity_dummy_payment extends AppCompatActivity {

    private TextView tvPaymentAmount, tvPaymentMethod, tvBookingReference;
    private MaterialButton btnPayNow, btnCancelPayment;

    private String bookingId, bookingNumber, paymentMethod;
    private double totalAmount;
    private DatabaseReference bookingsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dummy_payment);

        // Get data from intent
        getIntentData();
        initializeViews();
        setupClickListeners();
        updateUI();
        initializeFirebase();
    }

    private void getIntentData() {
        bookingId = getIntent().getStringExtra("bookingId");
        bookingNumber = getIntent().getStringExtra("bookingNumber");
        paymentMethod = getIntent().getStringExtra("paymentMethod");
        totalAmount = getIntent().getDoubleExtra("totalAmount", 0.0);

        // Validate required data
        if (bookingId == null || bookingId.isEmpty()) {
            Toast.makeText(this, "Booking information not available", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
    }

    private void initializeFirebase() {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference root = database.getReference();
        DatabaseReference candidate = root.child("ModestyRent - App").child("bookings");

        candidate.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                bookingsRef = candidate;
            } else {
                bookingsRef = root.child("bookings");
            }
        }).addOnFailureListener(e -> {
            bookingsRef = root.child("bookings");
        });
    }

    private void initializeViews() {
        tvPaymentAmount = findViewById(R.id.tvPaymentAmount);
        tvPaymentMethod = findViewById(R.id.tvPaymentMethod);
        tvBookingReference = findViewById(R.id.tvBookingReference);
        btnPayNow = findViewById(R.id.btnPayNow);
        btnCancelPayment = findViewById(R.id.btnCancelPayment);
    }

    private void setupClickListeners() {
        btnPayNow.setOnClickListener(v -> processPayment());

        btnCancelPayment.setOnClickListener(v -> {
            // Go back to checkout
            finish();
        });
    }

    private void updateUI() {
        // Set payment amount
        tvPaymentAmount.setText(String.format(Locale.getDefault(), "RM %.2f", totalAmount));

        // Set payment method
        if (paymentMethod != null) {
            tvPaymentMethod.setText(paymentMethod);
        } else {
            tvPaymentMethod.setText("QR Banking");
        }

        // Set booking reference
        if (bookingNumber != null) {
            tvBookingReference.setText(bookingNumber);
        } else {
            tvBookingReference.setText("Pending...");
        }
    }

    private void processPayment() {
        // Simulate payment processing
        btnPayNow.setEnabled(false);
        btnPayNow.setText("Processing...");

        // Simulate network delay
        new android.os.Handler().postDelayed(
                () -> {
                    // Update booking status to "paid" in Firebase
                    updateBookingStatus();

                    // Payment successful - navigate to booking confirmation
                    Intent intent = new Intent(activity_dummy_payment.this, activity_booking.class);
                    intent.putExtra("bookingId", bookingId);
                    startActivity(intent);
                    finish();
                },
                2000 // 2 seconds delay to simulate payment processing
        );
    }

    private void updateBookingStatus() {
        if (bookingsRef != null && bookingId != null) {
            // Update booking status to "paid" and add payment timestamp
            bookingsRef.child(bookingId).child("status").setValue("pending");
            bookingsRef.child(bookingId).child("paymentStatus").setValue("paid");
            bookingsRef.child(bookingId).child("paymentDate").setValue(System.currentTimeMillis());

            SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault());
            String paymentDateStr = dateFormat.format(System.currentTimeMillis());
            bookingsRef.child(bookingId).child("paymentDateString").setValue(paymentDateStr);
        }
    }
}
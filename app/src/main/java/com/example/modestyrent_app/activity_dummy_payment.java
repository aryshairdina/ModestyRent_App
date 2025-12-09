package com.example.modestyrent_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Random;

public class activity_dummy_payment extends AppCompatActivity {

    private TextView tvPaymentAmount, tvPaymentMethod, tvBookingReference;
    private MaterialButton btnPayNow, btnCancelPayment;

    // Data passed from checkout
    private String productId, ownerId, productName;
    private String renterName, renterPhone, deliveryAddress, deliveryOption, paymentMethod;
    private long startDateMillis, endDateMillis;
    private int days;
    private double unitPrice, rentalAmount, depositAmount, totalAmount;

    private String currentUserId;

    private DatabaseReference bookingsRef;
    private DatabaseReference productsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dummy_payment);

        getIntentData();
        initializeViews();
        initializeFirebase();
        updateUI();
        setupClickListeners();
    }

    private void getIntentData() {
        Intent intent = getIntent();
        productId = intent.getStringExtra("productId");
        ownerId = intent.getStringExtra("ownerId");
        productName = intent.getStringExtra("productName");
        renterName = intent.getStringExtra("renterName");
        renterPhone = intent.getStringExtra("renterPhone");
        deliveryAddress = intent.getStringExtra("deliveryAddress");
        deliveryOption = intent.getStringExtra("deliveryOption");
        paymentMethod = intent.getStringExtra("paymentMethod");

        startDateMillis = intent.getLongExtra("startDateMillis", -1);
        endDateMillis = intent.getLongExtra("endDateMillis", -1);
        days = intent.getIntExtra("days", 1);
        unitPrice = intent.getDoubleExtra("unitPrice", 0.0);
        rentalAmount = intent.getDoubleExtra("rentalAmount", 0.0);
        depositAmount = intent.getDoubleExtra("depositAmount", 0.0);
        totalAmount = intent.getDoubleExtra("totalAmount", 0.0);

        if (productId == null || ownerId == null || productName == null) {
            Toast.makeText(this, "Booking information not available", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void initializeFirebase() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please login to continue", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentUserId = user.getUid();

        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference root = database.getReference();
        DatabaseReference candidateBookings = root.child("ModestyRent - App").child("bookings");

        candidateBookings.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                bookingsRef = candidateBookings;
            } else {
                bookingsRef = root.child("bookings");
            }
        }).addOnFailureListener(e -> bookingsRef = root.child("bookings"));

        productsRef = root.child("products");
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
            // Just close and go back to checkout; no booking created yet
            finish();
        });
    }

    private void updateUI() {
        tvPaymentAmount.setText(String.format(Locale.getDefault(), "RM %.2f", totalAmount));
        tvPaymentMethod.setText(paymentMethod != null ? paymentMethod : "Online Banking");
        // Just show product name as reference
        tvBookingReference.setText(productName != null ? productName : "Booking");
    }

    private void processPayment() {
        btnPayNow.setEnabled(false);
        btnPayNow.setText("Processing...");

        // Simple fake delay
        new android.os.Handler().postDelayed(() -> {
            if (bookingsRef == null) {
                Toast.makeText(this, "Please wait, payment system not ready", Toast.LENGTH_SHORT).show();
                btnPayNow.setEnabled(true);
                btnPayNow.setText("Pay Now");
                return;
            }

            createBookingAfterPayment();

        }, 2000); // 2 seconds
    }

    private String generateBookingNumber() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyMMddHHmmss", Locale.getDefault());
        String timestamp = dateFormat.format(System.currentTimeMillis());
        Random random = new Random();
        int randomDigits = random.nextInt(900) + 100;
        return "MR" + timestamp + randomDigits;
    }

    private void createBookingAfterPayment() {
        String bookingId = bookingsRef.push().getKey();
        if (bookingId == null) {
            Toast.makeText(this, "Failed to create booking", Toast.LENGTH_SHORT).show();
            btnPayNow.setEnabled(true);
            btnPayNow.setText("Pay Now");
            return;
        }

        String bookingNumber = generateBookingNumber();

        Booking booking = new Booking();
        booking.setBookingId(bookingId);
        booking.setBookingNumber(bookingNumber);
        booking.setProductId(productId);
        booking.setOwnerId(ownerId);
        booking.setRenterId(currentUserId);
        booking.setProductName(productName);
        booking.setRenterName(renterName);
        booking.setRenterPhone(renterPhone);
        booking.setDeliveryAddress(deliveryAddress);
        booking.setDeliveryOption(deliveryOption);
        booking.setPaymentMethod(paymentMethod != null ? paymentMethod : "QR Banking");
        booking.setStartDate(startDateMillis);
        booking.setEndDate(endDateMillis);
        booking.setRentalDays(days);
        booking.setUnitPrice(unitPrice);
        booking.setRentalAmount(rentalAmount);
        booking.setDepositAmount(depositAmount);
        booking.setTotalAmount(totalAmount);
        booking.setStatus("Confirmed");
        booking.setBookingDate(System.currentTimeMillis());
        booking.setPaymentStatus("paid");
        booking.setPaymentDate(System.currentTimeMillis());

        bookingsRef.child(bookingId).setValue(booking)
                .addOnSuccessListener(aVoid -> {
                    // Update product status to Unavailable
                    if (productsRef != null && productId != null) {
                        productsRef.child(productId).child("status").setValue("Unavailable");
                    }

                    // Go to booking confirmation page
                    Intent intent = new Intent(activity_dummy_payment.this, activity_booking.class);
                    intent.putExtra("bookingId", bookingId);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to save booking: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnPayNow.setEnabled(true);
                    btnPayNow.setText("Pay Now");
                });
    }
}

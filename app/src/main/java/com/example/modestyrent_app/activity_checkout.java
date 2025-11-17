package com.example.modestyrent_app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Random;

public class activity_checkout extends AppCompatActivity {

    private TextView tvProductName, tvProductSize, tvRentalPeriod, tvTotalPrice, tvRentalFee,
            tvDaysCount, tvUnitPrice, tvFinalTotal, tvDeposit, tvSubtotal;
    private TextInputEditText etFullName, etPhone, etAddress;
    private RadioGroup rgDeliveryOption, rgPaymentMethod;
    private RadioButton rbPickup, rbDelivery, rbQRBanking, rbCOD;
    private Button btnConfirmCheckout;
    private ImageView backIcon;

    private String productId, ownerId, productName;
    private long startDateMillis, endDateMillis;
    private int days;
    private double unitPrice, totalAmount, depositAmount, subtotalAmount;
    private String currentUserId;

    private DatabaseReference usersRef, productsRef, bookingsRef;
    private FirebaseAuth mAuth;

    private final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);

        initializeViews();
        getIntentData();
        initializeFirebase();
        loadUserData();
        setupClickListeners();
        updateUI();
    }

    private void initializeViews() {
        // TextViews
        tvProductName = findViewById(R.id.tvProductName);
        tvProductSize = findViewById(R.id.tvProductSize);
        tvRentalPeriod = findViewById(R.id.tvRentalPeriod);
        tvTotalPrice = findViewById(R.id.tvTotalPrice);
        tvRentalFee = findViewById(R.id.tvRentalFee);
        tvDaysCount = findViewById(R.id.tvDaysCount);
        tvUnitPrice = findViewById(R.id.tvUnitPrice);
        tvFinalTotal = findViewById(R.id.tvFinalTotal);
        tvDeposit = findViewById(R.id.tvDeposit);
        tvSubtotal = findViewById(R.id.tvSubtotal);

        // Input fields
        etFullName = findViewById(R.id.etFullName);
        etPhone = findViewById(R.id.etPhone);
        etAddress = findViewById(R.id.etAddress);

        // Radio Groups
        rgDeliveryOption = findViewById(R.id.rgDeliveryOption);
        rgPaymentMethod = findViewById(R.id.rgPaymentMethod);

        // Radio Buttons
        rbPickup = findViewById(R.id.rbPickup);
        rbDelivery = findViewById(R.id.rbDelivery);
        rbQRBanking = findViewById(R.id.rbQRBanking);
        rbCOD = findViewById(R.id.rbCOD);

        // Buttons
        btnConfirmCheckout = findViewById(R.id.btnConfirmCheckout);
        backIcon = findViewById(R.id.backIcon);

        // Set default selections - This will now work properly
        rbPickup.setChecked(true);
        rbQRBanking.setChecked(true);

        // Add listeners to ensure mutual exclusivity
        rgDeliveryOption.setOnCheckedChangeListener((group, checkedId) -> {
            // This ensures only one option can be selected
            if (checkedId == R.id.rbPickup) {
                rbDelivery.setChecked(false);
            } else if (checkedId == R.id.rbDelivery) {
                rbPickup.setChecked(false);
            }
        });

        rgPaymentMethod.setOnCheckedChangeListener((group, checkedId) -> {
            // This ensures only one option can be selected
            if (checkedId == R.id.rbQRBanking) {
                rbCOD.setChecked(false);
            } else if (checkedId == R.id.rbCOD) {
                rbQRBanking.setChecked(false);
            }
        });
    }

    private void getIntentData() {
        productId = getIntent().getStringExtra("productId");
        ownerId = getIntent().getStringExtra("ownerId");
        productName = getIntent().getStringExtra("productName");
        startDateMillis = getIntent().getLongExtra("startDateMillis", -1);
        endDateMillis = getIntent().getLongExtra("endDateMillis", -1);
        days = getIntent().getIntExtra("days", 1);
        unitPrice = getIntent().getDoubleExtra("unitPrice", 0.0);

        // Calculate amounts
        totalAmount = unitPrice * days;
        depositAmount = 50.00; // Fixed RM 50 deposit
        subtotalAmount = totalAmount + depositAmount;
    }

    private void initializeFirebase() {
        mAuth = FirebaseAuth.getInstance();
        currentUserId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;

        FirebaseDatabase database = FirebaseDatabase.getInstance();
        usersRef = database.getReference("users");
        productsRef = database.getReference("products");
        bookingsRef = database.getReference("bookings");
    }

    private void loadUserData() {
        if (currentUserId != null) {
            usersRef.child(currentUserId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String fullName = snapshot.child("fullName").getValue(String.class);
                        String phone = snapshot.child("phone").getValue(String.class);
                        String address = snapshot.child("address").getValue(String.class);

                        if (fullName != null) etFullName.setText(fullName);
                        if (phone != null) etPhone.setText(phone);
                        if (address != null) etAddress.setText(address);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(activity_checkout.this, "Failed to load user data", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Load product details
        if (productId != null) {
            productsRef.child(productId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String size = snapshot.child("size").getValue(String.class);
                        if (size != null) {
                            tvProductSize.setText("Size: " + size);
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(activity_checkout.this, "Failed to load product details", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void setupClickListeners() {
        backIcon.setOnClickListener(v -> finish());

        btnConfirmCheckout.setOnClickListener(v -> confirmBooking());
    }

    private void updateUI() {
        String startStr = startDateMillis > 0 ? DATE_FORMAT.format(startDateMillis) : "—";
        String endStr = endDateMillis > 0 ? DATE_FORMAT.format(endDateMillis) : "—";

        // Update product info
        tvProductName.setText(productName != null ? productName : "Product");
        tvRentalPeriod.setText("Rental Period: " + startStr + " to " + endStr);

        // Update pricing
        tvTotalPrice.setText(String.format(Locale.US, "RM %.2f", subtotalAmount));
        tvRentalFee.setText(String.format(Locale.US, "RM %.2f", totalAmount));
        tvDaysCount.setText(days + " days");
        tvUnitPrice.setText(String.format(Locale.US, "RM %.2f/day", unitPrice));
        tvDeposit.setText(String.format(Locale.US, "RM %.2f", depositAmount));
        tvSubtotal.setText(String.format(Locale.US, "RM %.2f", subtotalAmount));
        tvFinalTotal.setText(String.format(Locale.US, "RM %.2f", subtotalAmount));
    }

    private String generateBookingNumber() {
        // Generate a proper booking number: MR + timestamp + random digits
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyMMddHHmmss", Locale.getDefault());
        String timestamp = dateFormat.format(System.currentTimeMillis());

        Random random = new Random();
        int randomDigits = random.nextInt(900) + 100; // 100-999

        return "MR" + timestamp + randomDigits;
    }

    private void updateProductStatusToUnavailable() {
        if (productId != null) {
            productsRef.child(productId).child("status").setValue("Unavailable")
                    .addOnSuccessListener(aVoid -> {
                        // Product status updated successfully
                        Log.d("Checkout", "Product status updated to Unavailable for product: " + productId);
                    })
                    .addOnFailureListener(e -> {
                        // Handle the error but don't block the booking process
                        Log.e("Checkout", "Failed to update product status: " + e.getMessage());
                    });
        }
    }

    private void confirmBooking() {
        // Validate input fields
        String fullName = etFullName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String address = etAddress.getText().toString().trim();

        if (fullName.isEmpty()) {
            etFullName.setError("Please enter your full name");
            return;
        }

        if (phone.isEmpty()) {
            etPhone.setError("Please enter your phone number");
            return;
        }

        if (address.isEmpty()) {
            etAddress.setError("Please enter your address");
            return;
        }

        // Validate radio button selections
        if (!rbPickup.isChecked() && !rbDelivery.isChecked()) {
            Toast.makeText(this, "Please select a delivery option", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!rbQRBanking.isChecked() && !rbCOD.isChecked()) {
            Toast.makeText(this, "Please select a payment method", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get selected options
        String deliveryOption = rbPickup.isChecked() ? "Pickup" : "Delivery";
        String paymentMethod = rbQRBanking.isChecked() ? "QR Banking" : "COD";

        if (currentUserId == null) {
            Toast.makeText(this, "Please login to continue", Toast.LENGTH_SHORT).show();
            return;
        }

        // Generate booking ID and booking number
        String bookingId = bookingsRef.push().getKey();
        String bookingNumber = generateBookingNumber();

        // Create booking object
        Booking booking = new Booking();
        booking.setBookingId(bookingId);
        booking.setBookingNumber(bookingNumber);
        booking.setProductId(productId);
        booking.setOwnerId(ownerId);
        booking.setRenterId(currentUserId);
        booking.setProductName(productName);
        booking.setRenterName(fullName);
        booking.setRenterPhone(phone);
        booking.setDeliveryAddress(address);
        booking.setDeliveryOption(deliveryOption);
        booking.setPaymentMethod(paymentMethod);
        booking.setStartDate(startDateMillis);
        booking.setEndDate(endDateMillis);
        booking.setRentalDays(days);
        booking.setUnitPrice(unitPrice);
        booking.setRentalAmount(totalAmount);
        booking.setDepositAmount(depositAmount);
        booking.setTotalAmount(subtotalAmount);
        booking.setStatus("pending");
        booking.setBookingDate(System.currentTimeMillis());

        // Save to Firebase and update product status
        bookingsRef.child(bookingId).setValue(booking)
                .addOnSuccessListener(aVoid -> {
                    // Update product status to "Unavailable" in Realtime Database
                    updateProductStatusToUnavailable();

                    // Check payment method and navigate accordingly
                    if (paymentMethod.equals("QR Banking")) {
                        // Navigate to dummy payment page
                        Intent intent = new Intent(activity_checkout.this, activity_dummy_payment.class);
                        intent.putExtra("bookingId", bookingId);
                        intent.putExtra("bookingNumber", bookingNumber);
                        intent.putExtra("paymentMethod", paymentMethod);
                        intent.putExtra("totalAmount", subtotalAmount);
                        startActivity(intent);
                    } else {
                        // COD - directly go to booking confirmation
                        Intent intent = new Intent(activity_checkout.this, activity_booking.class);
                        intent.putExtra("bookingId", bookingId);
                        startActivity(intent);
                    }
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(activity_checkout.this, "Failed to confirm booking: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
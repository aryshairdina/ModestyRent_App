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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import com.example.modestyrent_app.NotificationHelper;

public class activity_checkout extends AppCompatActivity {

    private TextView tvProductName, tvProductSize, tvRentalPeriod, tvTotalPrice, tvRentalFee,
            tvDaysCount, tvUnitPrice, tvFinalTotal, tvDeposit, tvSubtotal;
    private TextInputEditText etFullName, etPhone, etAddress;
    private RadioGroup rgDeliveryOption, rgPaymentMethod;
    private RadioButton rbPickup, rbDelivery, rbOnlineBanking;
    private Button btnConfirmCheckout;
    private ImageView backIcon;

    private String productId, ownerId, productName;
    private long startDateMillis, endDateMillis;
    private int days;
    private double unitPrice, totalAmount, depositAmount, subtotalAmount;
    private String currentUserId;

    private DatabaseReference usersRef, productsRef, bookingsRef, chatsRef;
    private FirebaseAuth mAuth;

    private final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);

        // 🔒 AUTH GUARD (rule-related only)
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Please login to continue", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        // 🔒 END auth guard

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
        rbOnlineBanking = findViewById(R.id.rbOnlineBanking);

        // Buttons
        btnConfirmCheckout = findViewById(R.id.btnConfirmCheckout);
        backIcon = findViewById(R.id.backIcon);

        // Default selections
        rbPickup.setChecked(true);
        rbOnlineBanking.setChecked(true);

        // Ensure mutual exclusivity is fine (RadioGroup already ensures this, but keep your logic)
        rgDeliveryOption.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbPickup) {
                rbDelivery.setChecked(false);
            } else if (checkedId == R.id.rbDelivery) {
                rbPickup.setChecked(false);
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
        depositAmount = 150.00; // Fixed RM 150 deposit
        subtotalAmount = totalAmount + depositAmount;
    }

    private void initializeFirebase() {
        mAuth = FirebaseAuth.getInstance();
        currentUserId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;

        FirebaseDatabase database = FirebaseDatabase.getInstance();
        usersRef = database.getReference("users");
        productsRef = database.getReference("products");
        bookingsRef = database.getReference("bookings");
        chatsRef = database.getReference("chats");
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

        // Load product size
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

        tvProductName.setText(productName != null ? productName : "Product");
        tvRentalPeriod.setText("Rental Period: " + startStr + " to " + endStr);

        tvTotalPrice.setText(String.format(Locale.US, "RM %.2f", subtotalAmount));
        tvRentalFee.setText(String.format(Locale.US, "RM %.2f", totalAmount));
        tvDaysCount.setText(days + " days");
        tvUnitPrice.setText(String.format(Locale.US, "RM %.2f/day", unitPrice));
        tvDeposit.setText(String.format(Locale.US, "RM %.2f", depositAmount));
        tvSubtotal.setText(String.format(Locale.US, "RM %.2f", subtotalAmount));
        tvFinalTotal.setText(String.format(Locale.US, "RM %.2f", subtotalAmount));
    }

    private String generateBookingNumber() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyMMddHHmmss", Locale.getDefault());
        String timestamp = dateFormat.format(System.currentTimeMillis());

        Random random = new Random();
        int randomDigits = random.nextInt(900) + 100; // 100–999

        return "MR" + timestamp + randomDigits;
    }

    private String generateChatId(String userId1, String userId2, String productId) {
        // Create consistent chat ID for two users and a product
        String[] ids = {userId1, userId2};
        java.util.Arrays.sort(ids);
        return "chat_" + ids[0] + "_" + ids[1] + "_" + productId;
    }

    private void updateProductStatusToUnavailable() {
        if (productId != null) {
            productsRef.child(productId).child("status").setValue("Unavailable")
                    .addOnSuccessListener(aVoid ->
                            Log.d("Checkout", "Product status updated to Unavailable: " + productId))
                    .addOnFailureListener(e ->
                            Log.e("Checkout", "Failed to update product status: " + e.getMessage()));
        }
    }

    private void createChatRoom(String bookingId, String renterId, String ownerId) {
        String chatId = generateChatId(renterId, ownerId, productId);

        Map<String, Object> chatRoomData = new HashMap<>();
        Map<String, Object> participants = new HashMap<>();
        participants.put(renterId, true);
        participants.put(ownerId, true);

        chatRoomData.put("participants", participants);
        chatRoomData.put("createdAt", System.currentTimeMillis());
        chatRoomData.put("productId", productId);
        chatRoomData.put("bookingId", bookingId);
        chatRoomData.put("productName", productName);
        chatRoomData.put("lastMessage", "");
        chatRoomData.put("lastMessageTime", 0L);
        chatRoomData.put("lastMessageSender", "");
        chatRoomData.put("renterId", renterId);
        chatRoomData.put("ownerId", ownerId);

        chatsRef.child(chatId).setValue(chatRoomData)
                .addOnSuccessListener(aVoid -> {
                    Log.d("Checkout", "Chat room created: " + chatId);

                    // 🔔 SEND CHAT AVAILABLE NOTIFICATION
                    sendChatAvailableNotification(renterId, ownerId, chatId, bookingId);
                })
                .addOnFailureListener(e -> {
                    Log.e("Checkout", "Failed to create chat room: " + e.getMessage());
                });
    }

    private void sendChatAvailableNotification(String renterId, String ownerId, String chatId, String bookingId) {
        // Get renter name for owner notification
        usersRef.child(renterId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String renterName = "Borrower";
                if (snapshot.exists()) {
                    renterName = snapshot.child("fullName").getValue(String.class);
                    if (renterName == null || renterName.isEmpty()) {
                        renterName = "Borrower";
                    }
                }

                // Also send standard booking notification
                NotificationHelper.sendNotification(
                        ownerId,
                        "New Booking",
                        renterName + " has booked your product: " + productName,
                        "booking_confirmed",
                        bookingId
                );
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("Checkout", "Failed to get renter info for notification");
            }
        });

        // Get owner name for renter notification
        usersRef.child(ownerId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String ownerName = "Owner";
                if (snapshot.exists()) {
                    ownerName = snapshot.child("fullName").getValue(String.class);
                    if (ownerName == null || ownerName.isEmpty()) {
                        ownerName = "Owner";
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("Checkout", "Failed to get owner info for notification");
            }
        });
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

        // Delivery option must be selected
        if (!rbPickup.isChecked() && !rbDelivery.isChecked()) {
            Toast.makeText(this, "Please select a delivery option", Toast.LENGTH_SHORT).show();
            return;
        }

        // Payment method must be selected (only Online Banking available now)
        if (!rbOnlineBanking.isChecked()) {
            Toast.makeText(this, "Please select a payment method", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentUserId == null) {
            Toast.makeText(this, "Please login to continue", Toast.LENGTH_SHORT).show();
            return;
        }

        String deliveryOption = rbPickup.isChecked() ? "Pickup" : "Delivery";
        String paymentMethod = "Online Banking";  // Only payment method available

        // 🔹 ONLINE BANKING – DO NOT INSERT BOOKING HERE
        String bookingNumber = generateBookingNumber();

        Intent intent = new Intent(activity_checkout.this, activity_dummy_payment.class);
        intent.putExtra("productId", productId);
        intent.putExtra("ownerId", ownerId);
        intent.putExtra("productName", productName);
        intent.putExtra("renterName", fullName);
        intent.putExtra("renterPhone", phone);
        intent.putExtra("deliveryAddress", address);
        intent.putExtra("deliveryOption", deliveryOption);
        intent.putExtra("paymentMethod", paymentMethod);
        intent.putExtra("startDateMillis", startDateMillis);
        intent.putExtra("endDateMillis", endDateMillis);
        intent.putExtra("days", days);
        intent.putExtra("unitPrice", unitPrice);
        intent.putExtra("rentalAmount", totalAmount);
        intent.putExtra("depositAmount", depositAmount);
        intent.putExtra("totalAmount", subtotalAmount);

        // 🔔 Add these extras for notifications
        intent.putExtra("renterId", currentUserId);
        intent.putExtra("bookingNumber", bookingNumber);

        startActivity(intent);

        // ⚠️ IMPORTANT: DO NOT WRITE TO bookingsRef HERE
        // But we can send a pending notification to owner
        sendPendingBookingNotification(bookingNumber, fullName);
    }

    private void sendPendingBookingNotification(String bookingNumber, String renterName) {
        // Get owner info for personalized notification
        usersRef.child(ownerId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String ownerName = "Owner";
                if (snapshot.exists()) {
                    ownerName = snapshot.child("fullName").getValue(String.class);
                    if (ownerName == null || ownerName.isEmpty()) {
                        ownerName = "Owner";
                    }
                }

                // 🔔 NOTIFICATION FOR OWNER: Pending booking
                NotificationHelper.sendNotification(
                        ownerId,
                        "Pending Booking",
                        renterName + " is trying to book " + productName + ". Waiting for payment.",
                        "pending_booking",
                        null
                );

                // 🔔 NOTIFICATION FOR BORROWER: Proceed to payment
                NotificationHelper.sendNotification(
                        currentUserId,
                        "Proceed to Payment",
                        "Please complete payment for booking #" + bookingNumber,
                        "payment_required",
                        null
                );
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("Checkout", "Failed to send pending notification");
            }
        });
    }

    private void sendFCMBookingNotification(String bookingId, String bookingNumber,
                                            String renterId, String ownerId, String renterName) {
        // This would call your FCM service
        // For now, we'll log it
        Log.d("Checkout", "FCM Notification would be sent:");
        Log.d("Checkout", "- Booking: " + bookingNumber);
        Log.d("Checkout", "- Renter: " + renterName);
        Log.d("Checkout", "- Owner ID: " + ownerId);
    }
}
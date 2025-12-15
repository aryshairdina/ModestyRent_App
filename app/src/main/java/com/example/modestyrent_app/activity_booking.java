package com.example.modestyrent_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Random;

public class activity_booking extends AppCompatActivity {

    private TextView tvBookingNumber, tvProductName, tvRentalPeriod, tvDeliveryOption, tvPaymentMethod, tvTotalAmount, tvBookingStatus;
    private MaterialButton btnViewBookings, btnBackToHome;
    private ImageView backIcon;

    private DatabaseReference bookingsRef;
    private String bookingId;

    private final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking);

        // 🔒 AUTH GUARD (required for .write: auth != null)
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Please sign in to continue", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, activity_signin.class));
            finish();
            return;
        }
        // 🔒 END auth guard

        // Get bookingId from intent
        bookingId = getIntent().getStringExtra("bookingId");
        if (bookingId == null || bookingId.isEmpty()) {
            Toast.makeText(this, "Booking information not available", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initializeViews();
        setupClickListeners();
        loadBookingData();
    }


    private void initializeViews() {
        tvBookingNumber = findViewById(R.id.tvBookingNumber);
        tvProductName = findViewById(R.id.tvProductName);
        tvRentalPeriod = findViewById(R.id.tvRentalPeriod);
        tvDeliveryOption = findViewById(R.id.tvDeliveryOption);
        tvPaymentMethod = findViewById(R.id.tvPaymentMethod);
        tvTotalAmount = findViewById(R.id.tvTotalAmount);
        tvBookingStatus = findViewById(R.id.tvBookingStatus);
        btnViewBookings = findViewById(R.id.btnViewBookings);
        btnBackToHome = findViewById(R.id.btnBackToHome);
        backIcon = findViewById(R.id.backIcon);
    }

    private void setupClickListeners() {
        if (backIcon != null) {
            backIcon.setOnClickListener(v -> finish());
        }

        if (btnViewBookings != null) {
            btnViewBookings.setOnClickListener(v -> {
                // Navigate to My Bookings page
                Intent intent = new Intent(activity_booking.this, activity_myrentals.class);
                startActivity(intent);
                finish();
            });
        }

        if (btnBackToHome != null) {
            btnBackToHome.setOnClickListener(v -> {
                // Navigate back to home page
                Intent intent = new Intent(activity_booking.this, activity_homepage.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            });
        }
    }

    private void loadBookingData() {
        // Resolve bookings path
        DatabaseReference root = FirebaseDatabase.getInstance().getReference();
        DatabaseReference candidate = root.child("ModestyRent - App").child("bookings");

        candidate.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                bookingsRef = candidate;
            } else {
                bookingsRef = root.child("bookings");
            }
            fetchBookingDetails();
        }).addOnFailureListener(e -> {
            bookingsRef = root.child("bookings");
            fetchBookingDetails();
        });
    }

    private void fetchBookingDetails() {
        if (bookingsRef == null) return;

        bookingsRef.child(bookingId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Get booking data
                    String productName = snapshot.child("productName").getValue(String.class);
                    String deliveryOption = snapshot.child("deliveryOption").getValue(String.class);
                    String paymentMethod = snapshot.child("paymentMethod").getValue(String.class);
                    String status = snapshot.child("status").getValue(String.class);

                    // Check if booking number exists
                    String bookingNumber = snapshot.child("bookingNumber").getValue(String.class);
                    if (bookingNumber == null || bookingNumber.isEmpty()) {
                        bookingNumber = generateBookingNumber();
                        saveBookingNumber(bookingNumber);
                    }

                    Long startDate = snapshot.child("startDate").getValue(Long.class);
                    Long endDate = snapshot.child("endDate").getValue(Long.class);
                    Long rentalDays = snapshot.child("rentalDays").getValue(Long.class);

                    Double totalAmount = snapshot.child("totalAmount").getValue(Double.class);
                    Double rentalAmount = snapshot.child("rentalAmount").getValue(Double.class);
                    Double depositAmount = snapshot.child("depositAmount").getValue(Double.class);

                    // Update UI with booking data
                    updateBookingUI(
                            bookingNumber,
                            productName,
                            deliveryOption,
                            paymentMethod,
                            status,
                            startDate,
                            endDate,
                            rentalDays,
                            totalAmount,
                            rentalAmount,
                            depositAmount
                    );
                } else {
                    Toast.makeText(activity_booking.this, "Booking details not found", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(activity_booking.this, "Failed to load booking details", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private String generateBookingNumber() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyMMddHHmmss", Locale.getDefault());
        String timestamp = dateFormat.format(System.currentTimeMillis());
        Random random = new Random();
        int randomDigits = random.nextInt(900) + 100;
        return "MR" + timestamp + randomDigits;
    }

    private void saveBookingNumber(String bookingNumber) {
        if (bookingsRef != null && bookingId != null) {
            bookingsRef.child(bookingId).child("bookingNumber").setValue(bookingNumber)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            System.out.println("Booking number saved: " + bookingNumber);
                        }
                    });
        }
    }

    private void updateBookingUI(String bookingNumber, String productName, String deliveryOption, String paymentMethod,
                                 String status, Long startDate, Long endDate, Long rentalDays,
                                 Double totalAmount, Double rentalAmount, Double depositAmount) {

        // Set booking number
        if (tvBookingNumber != null) {
            tvBookingNumber.setText(bookingNumber != null ? bookingNumber : generateBookingNumber());
        }

        // Set product name
        if (tvProductName != null) {
            tvProductName.setText(productName != null ? productName : "Unknown Product");
        }

        // Set rental period
        String rentalPeriodText = "--";
        if (startDate != null && endDate != null) {
            String startStr = DATE_FORMAT.format(startDate);
            String endStr = DATE_FORMAT.format(endDate);
            rentalPeriodText = startStr + " to " + endStr;
            if (rentalDays != null) {
                rentalPeriodText += " (" + rentalDays + " days)";
            }
        }
        if (tvRentalPeriod != null) {
            tvRentalPeriod.setText(rentalPeriodText);
        }

        // Set delivery option
        if (tvDeliveryOption != null) {
            tvDeliveryOption.setText(deliveryOption != null ? deliveryOption : "--");
        }

        // Set payment method
        if (tvPaymentMethod != null) {
            tvPaymentMethod.setText(paymentMethod != null ? paymentMethod : "--");
        }

        // Set total amount
        if (tvTotalAmount != null) {
            if (totalAmount != null) {
                tvTotalAmount.setText(String.format(Locale.getDefault(), "RM %.2f", totalAmount));
            } else {
                tvTotalAmount.setText("RM 0.00");
            }
        }

        // Set status - update status to "pending" after payment
        if (tvBookingStatus != null) {
            String displayStatus = "Pending Delivery";
            if (status != null && status.equals("pending_payment")) {
                displayStatus = "Payment Completed";
                // Update status in Firebase to "confirmed"
                updateBookingStatus("Confirmed");
            } else if (status != null) {
                switch (status.toLowerCase()) {
                    case "pending":
                        displayStatus = "Pending Delivery";
                        break;
                    case "confirmed":
                        displayStatus = "Confirmed";
                        break;
                    case "delivered":
                        displayStatus = "Delivered";
                        break;
                    case "completed":
                        displayStatus = "Completed";
                        break;
                    case "cancelled":
                        displayStatus = "Cancelled";
                        break;
                    default:
                        displayStatus = status;
                        break;
                }
            }
            tvBookingStatus.setText(displayStatus);
        }
    }

    private void updateBookingStatus(String newStatus) {
        if (bookingsRef != null && bookingId != null) {
            bookingsRef.child(bookingId).child("status").setValue(newStatus)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            System.out.println("Booking status updated to: " + newStatus);
                        }
                    });
        }
    }
}
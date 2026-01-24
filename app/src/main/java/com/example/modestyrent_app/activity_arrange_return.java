package com.example.modestyrent_app;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class activity_arrange_return extends AppCompatActivity {

    private String bookingId, productId, ownerId, currentUserId;

    private ImageView backButton;
    private TextView tvProductName, tvRentalPeriod, tvBookingNumber, tvOwnerContact, tvSelectedMethod;
    private RadioGroup rgReturnMethod;
    private RadioButton rbDropOff, rbOwnerPickup;
    private EditText etReturnInstructions;
    private MaterialButton btnConfirmReturn;
    private ProgressBar loadingProgress;

    private DatabaseReference bookingsRef, usersRef;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_arrange_return);

        // Get intent data
        Intent intent = getIntent();
        bookingId = intent.getStringExtra("bookingId");
        productId = intent.getStringExtra("productId");
        ownerId = intent.getStringExtra("ownerId");

        if (bookingId == null) {
            Toast.makeText(this, "Booking ID not provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        mAuth = FirebaseAuth.getInstance();

        // 🔒 AUTH GUARD (important for .write: auth != null)
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please sign in to continue", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, activity_signin.class));
            finish();
            return;
        }
        currentUserId = user.getUid();
        // 🔒 END auth guard

        initializeViews();
        setupFirebase();
        loadBookingDetails();
    }


    private void initializeViews() {
        backButton = findViewById(R.id.backButton);
        tvProductName = findViewById(R.id.tvProductName);
        tvRentalPeriod = findViewById(R.id.tvRentalPeriod);
        tvBookingNumber = findViewById(R.id.tvBookingNumber);
        tvOwnerContact = findViewById(R.id.tvOwnerContact);
        tvSelectedMethod = findViewById(R.id.tvSelectedMethod);

        rgReturnMethod = findViewById(R.id.rgReturnMethod);
        rbDropOff = findViewById(R.id.rbDropOff);
        rbOwnerPickup = findViewById(R.id.rbOwnerPickup);
        etReturnInstructions = findViewById(R.id.etReturnInstructions);
        btnConfirmReturn = findViewById(R.id.btnConfirmReturn);
        loadingProgress = findViewById(R.id.loadingProgress);

        backButton.setOnClickListener(v -> finish());
        btnConfirmReturn.setOnClickListener(v -> confirmReturn());

        // RadioGroup listener: update hint + bottom "Return Method" text
        rgReturnMethod.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbDropOff) {
                etReturnInstructions.setHint("Add any special instructions for drop-off...");
                tvSelectedMethod.setText("Drop-off at Owner");
            } else if (checkedId == R.id.rbOwnerPickup) {
                etReturnInstructions.setHint("Add any special instructions for pickup (e.g., gate code, floor number)...");
                tvSelectedMethod.setText("Owner Pickup");
            } else {
                tvSelectedMethod.setText("Not selected");
            }
        });

        // 🔒 Ensure ONLY ONE option can be selected at a time (manual exclusivity)
        rbDropOff.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (rbOwnerPickup.isChecked()) {
                    rbOwnerPickup.setChecked(false);
                }
                // update RadioGroup checked id so confirmReturn() can still use getCheckedRadioButtonId()
                rgReturnMethod.check(R.id.rbDropOff);
            }
        });

        rbOwnerPickup.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (rbDropOff.isChecked()) {
                    rbDropOff.setChecked(false);
                }
                rgReturnMethod.check(R.id.rbOwnerPickup);
            }
        });

        // Add click listeners to the entire option containers for better UX
        setupCardClickListeners();
    }

    private void setupCardClickListeners() {
        try {
            int childCount = rgReturnMethod.getChildCount();

            // First option container (Drop-off)
            if (childCount > 0) {
                View firstOption = rgReturnMethod.getChildAt(0);
                if (firstOption != null) {
                    firstOption.setOnClickListener(v -> rbDropOff.setChecked(true));
                }
            }

            // Second option container (Owner pickup)
            if (childCount > 1) {
                View secondOption = rgReturnMethod.getChildAt(1);
                if (secondOption != null) {
                    secondOption.setOnClickListener(v -> rbOwnerPickup.setChecked(true));
                }
            }
        } catch (Exception e) {
            System.out.println("Could not setup card click listeners: " + e.getMessage());
        }
    }

    private void setupFirebase() {
        bookingsRef = FirebaseDatabase.getInstance().getReference("bookings");
        usersRef = FirebaseDatabase.getInstance().getReference("users");
    }

    private void loadBookingDetails() {
        // Safely handle loading progress - only use if it exists
        if (loadingProgress != null) {
            loadingProgress.setVisibility(View.VISIBLE);
        }

        bookingsRef.child(bookingId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    try {
                        // Update UI with booking data
                        updateBookingUI(snapshot);

                        // Load owner contact info
                        loadOwnerContactInfo(ownerId);

                    } catch (Exception e) {
                        Toast.makeText(activity_arrange_return.this, "Error loading booking details", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(activity_arrange_return.this, "Booking not found", Toast.LENGTH_SHORT).show();
                    finish();
                }

                // Safely hide loading progress if it exists
                if (loadingProgress != null) {
                    loadingProgress.setVisibility(View.GONE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(activity_arrange_return.this, "Failed to load booking", Toast.LENGTH_SHORT).show();

                // Safely hide loading progress if it exists
                if (loadingProgress != null) {
                    loadingProgress.setVisibility(View.GONE);
                }
            }
        });
    }

    private void updateBookingUI(DataSnapshot bookingSnapshot) {
        // Basic booking info
        tvProductName.setText(getStringValue(bookingSnapshot, "productName"));
        tvBookingNumber.setText("#" + getStringValue(bookingSnapshot, "bookingNumber"));

        // Rental period
        Long startDate = getLongValue(bookingSnapshot, "startDate");
        Long endDate = getLongValue(bookingSnapshot, "endDate");
        Integer rentalDays = getIntegerValue(bookingSnapshot, "rentalDays");

        if (startDate != null && endDate != null) {
            String periodText = formatDate(startDate) + " - " + formatDate(endDate);
            if (rentalDays != null) {
                periodText += " (" + rentalDays + " days)";
            }
            tvRentalPeriod.setText(periodText);
        }
    }

    private void loadOwnerContactInfo(String ownerId) {
        if (ownerId != null) {
            usersRef.child(ownerId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String ownerName = getStringValue(snapshot, "fullName");
                        String ownerPhone = getStringValue(snapshot, "phone");

                        if (ownerName != null && ownerPhone != null) {
                            tvOwnerContact.setText("Owner: " + ownerName + " (" + ownerPhone + ")");
                        } else {
                            tvOwnerContact.setText("Owner information not available");
                        }
                    } else {
                        tvOwnerContact.setText("Owner information not available");
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    tvOwnerContact.setText("Owner information not available");
                }
            });
        }
    }

    private void confirmReturn() {
        int selectedId = rgReturnMethod.getCheckedRadioButtonId();

        if (selectedId == -1) {
            Toast.makeText(this, "Please select a return method", Toast.LENGTH_SHORT).show();
            return;
        }

        // Declare variables with final so they can be used in the inner class
        final String returnMethod;
        final String returnMethodDisplay;

        if (selectedId == R.id.rbDropOff) {
            returnMethod = "DropOff";
            returnMethodDisplay = "Drop-off at Owner's Location";
        } else if (selectedId == R.id.rbOwnerPickup) {
            returnMethod = "OwnerPickup";
            returnMethodDisplay = "Owner Pickup";
        } else {
            Toast.makeText(this, "Invalid return method selected", Toast.LENGTH_SHORT).show();
            return;
        }

        final String instructions = etReturnInstructions.getText().toString().trim();

        // Show confirmation dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Confirm Return");
        builder.setMessage("Are you sure you want to proceed with return?\n\n" +
                "Return Method: " + returnMethodDisplay +
                (instructions.isEmpty() ? "" : "\nInstructions: " + instructions) +
                "\n\nThis will notify the owner about your return.");

        builder.setPositiveButton("Yes, Confirm Return", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                processReturn(returnMethod, instructions);
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void processReturn(String returnMethod, String instructions) {
        // Show loading state
        btnConfirmReturn.setEnabled(false);
        btnConfirmReturn.setText("Processing...");

        Map<String, Object> updates = new HashMap<>();

        // CORRECT STATUS FLOW: Change status to "ReturnRequested" when borrower confirms return
        updates.put("status", "ReturnRequested");
        updates.put("returnMethod", returnMethod);
        updates.put("returnInstructions", instructions);
        updates.put("returnRequestTime", System.currentTimeMillis());
        updates.put("returnConfirmedByBorrower", true);
        updates.put("deliveryStatus", "ReturnRequested");

        bookingsRef.child(bookingId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Return submitted successfully!", Toast.LENGTH_SHORT).show();

                    // Navigate back to rental details with success message
                    Intent intent = new Intent(this, activity_rentals_details_borrower.class);
                    intent.putExtra("bookingId", bookingId);
                    intent.putExtra("productId", productId);
                    intent.putExtra("ownerId", ownerId);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    intent.putExtra("showSuccessMessage", "Return submitted successfully!");
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to submit return: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    // Reset button state
                    btnConfirmReturn.setEnabled(true);
                    btnConfirmReturn.setText("Confirm Return");
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

    private Integer getIntegerValue(DataSnapshot snapshot, String key) {
        DataSnapshot child = snapshot.child(key);
        if (child.exists()) {
            Object value = child.getValue();
            if (value instanceof Integer) return (Integer) value;
            if (value instanceof Long) return ((Long) value).intValue();
            if (value instanceof String) {
                try { return Integer.parseInt((String) value); } catch (NumberFormatException e) { return null; }
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

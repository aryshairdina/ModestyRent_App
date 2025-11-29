package com.example.modestyrent_app;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class activity_return_rentals extends AppCompatActivity {

    private String bookingId, productId, ownerId, currentUserId;

    private ImageView backButton;
    private TextView tvProductName, tvBookingNumber, tvBorrowerName, tvReturnMethod, tvReturnRequestDate;
    private TextView tvBorrowerContact, tvBorrowerPhone, tvBorrowerAddress, tvReturnInstructions;
    private LinearLayout returnTimeline;
    private MaterialCardView cardActionRequired;
    private TextView tvActionDescription;
    private MaterialButton btnTakeAction, btnContactBorrower, btnMainAction;
    private TextView tvCurrentStatus;

    private DatabaseReference bookingsRef, usersRef, productsRef;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_return_rentals);

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
        currentUserId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;

        initializeViews();
        setupFirebase();
        loadBookingDetails();
    }

    private void initializeViews() {
        backButton = findViewById(R.id.backButton);
        tvProductName = findViewById(R.id.tvProductName);
        tvBookingNumber = findViewById(R.id.tvBookingNumber);
        tvBorrowerName = findViewById(R.id.tvBorrowerName);
        tvReturnMethod = findViewById(R.id.tvReturnMethod);
        tvReturnRequestDate = findViewById(R.id.tvReturnRequestDate);
        tvBorrowerContact = findViewById(R.id.tvBorrowerContact);
        tvBorrowerPhone = findViewById(R.id.tvBorrowerPhone);
        tvBorrowerAddress = findViewById(R.id.tvBorrowerAddress);
        tvReturnInstructions = findViewById(R.id.tvReturnInstructions);
        returnTimeline = findViewById(R.id.returnTimeline);
        cardActionRequired = findViewById(R.id.cardActionRequired);
        tvActionDescription = findViewById(R.id.tvActionDescription);
        btnTakeAction = findViewById(R.id.btnTakeAction);
        btnContactBorrower = findViewById(R.id.btnContactBorrower);
        btnMainAction = findViewById(R.id.btnMainAction);
        tvCurrentStatus = findViewById(R.id.tvCurrentStatus);

        backButton.setOnClickListener(v -> finish());

        btnTakeAction.setOnClickListener(v -> handleTakeAction());
        btnContactBorrower.setOnClickListener(v -> contactBorrower());
        btnMainAction.setOnClickListener(v -> handleMainAction());
    }

    private void setupFirebase() {
        bookingsRef = FirebaseDatabase.getInstance().getReference("bookings");
        usersRef = FirebaseDatabase.getInstance().getReference("users");
        productsRef = FirebaseDatabase.getInstance().getReference("products");
    }

    private void loadBookingDetails() {
        bookingsRef.child(bookingId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    try {
                        updateBookingUI(snapshot);
                        loadBorrowerInfo();
                        updateReturnTimeline(snapshot);
                        updateActionButtons(snapshot);
                    } catch (Exception e) {
                        Toast.makeText(activity_return_rentals.this, "Error loading booking details", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(activity_return_rentals.this, "Booking not found", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(activity_return_rentals.this, "Failed to load booking", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateBookingUI(DataSnapshot bookingSnapshot) {
        // Basic booking info
        tvProductName.setText(getStringValue(bookingSnapshot, "productName"));
        tvBookingNumber.setText("#" + getStringValue(bookingSnapshot, "bookingNumber"));

        // Return method
        String returnMethod = getStringValue(bookingSnapshot, "returnMethod");
        if ("DropOff".equals(returnMethod)) {
            tvReturnMethod.setText("Drop-off at Owner");
        } else if ("OwnerPickup".equals(returnMethod)) {
            tvReturnMethod.setText("Owner Pickup");
        } else {
            tvReturnMethod.setText("Not specified");
        }

        // Return request date
        Long returnRequestTime = getLongValue(bookingSnapshot, "returnRequestTime");
        if (returnRequestTime != null) {
            tvReturnRequestDate.setText(formatDateTime(returnRequestTime));
        }

        // Return instructions
        String instructions = getStringValue(bookingSnapshot, "returnInstructions");
        if (instructions != null && !instructions.isEmpty()) {
            tvReturnInstructions.setText(instructions);
        } else {
            tvReturnInstructions.setText("No special instructions provided.");
        }

        // Current status
        String status = getStringValue(bookingSnapshot, "status");
        tvCurrentStatus.setText(getStatusDisplayText(status));
    }

    private void loadBorrowerInfo() {
        bookingsRef.child(bookingId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String renterId = getStringValue(snapshot, "renterId");
                    if (renterId != null) {
                        usersRef.child(renterId).addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot userSnapshot) {
                                if (userSnapshot.exists()) {
                                    String borrowerName = getStringValue(userSnapshot, "fullName");
                                    String borrowerPhone = getStringValue(userSnapshot, "phone");
                                    String borrowerAddress = getStringValue(userSnapshot, "address");

                                    if (borrowerName != null) {
                                        tvBorrowerName.setText(borrowerName);
                                        tvBorrowerContact.setText("Name: " + borrowerName);
                                    }
                                    if (borrowerPhone != null) {
                                        tvBorrowerPhone.setText("Phone: " + borrowerPhone);
                                    }
                                    if (borrowerAddress != null) {
                                        tvBorrowerAddress.setText("Address: " + borrowerAddress);
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
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Handle error
            }
        });
    }

    private void updateReturnTimeline(DataSnapshot bookingSnapshot) {
        returnTimeline.removeAllViews();

        String status = getStringValue(bookingSnapshot, "status");
        String returnMethod = getStringValue(bookingSnapshot, "returnMethod");

        // Return timeline stages
        String[] timelineStages = {"Return Requested", "Return Arranged", "Item Received", "Inspection Started", "Inspection Completed", "Deposit Refunded"};
        String[] timelineDescriptions = {
                "Borrower has requested to return the item",
                "Return method has been confirmed and arranged",
                "Item has been received from borrower",
                "Item inspection has been started",
                "Item inspection has been completed",
                "Deposit has been refunded to borrower"
        };

        Long returnRequestTime = getLongValue(bookingSnapshot, "returnRequestTime");
        Long inspectionTime = getLongValue(bookingSnapshot, "inspectionTime");
        Long completionTime = getLongValue(bookingSnapshot, "completionTime");

        for (int i = 0; i < timelineStages.length; i++) {
            View timelineItem = createTimelineItem(
                    timelineStages[i],
                    timelineDescriptions[i],
                    getTimelineDate(i, returnRequestTime, inspectionTime, completionTime),
                    isTimelineCompleted(i, status),
                    i == timelineStages.length - 1
            );
            returnTimeline.addView(timelineItem);
        }
    }

    private View createTimelineItem(String title, String description, String date, boolean isCompleted, boolean isLast) {
        View timelineItem = LayoutInflater.from(this).inflate(R.layout.item_return_timeline, returnTimeline, false);

        View timelineIndicator = timelineItem.findViewById(R.id.timelineIndicator);
        View timelineConnector = timelineItem.findViewById(R.id.timelineConnector);
        TextView tvTimelineTitle = timelineItem.findViewById(R.id.tvTimelineTitle);
        TextView tvTimelineDescription = timelineItem.findViewById(R.id.tvTimelineDescription);
        TextView tvTimelineDate = timelineItem.findViewById(R.id.tvTimelineDate);

        tvTimelineTitle.setText(title);
        tvTimelineDescription.setText(description);
        tvTimelineDate.setText(date != null ? date : "Pending");

        if (isLast) {
            timelineConnector.setVisibility(View.GONE);
        }

        if (isCompleted) {
            timelineIndicator.setBackgroundResource(R.drawable.circle_completed);
            tvTimelineTitle.setTextColor(getColor(R.color.primary));
        } else {
            timelineIndicator.setBackgroundResource(R.drawable.circle_pending);
            tvTimelineTitle.setTextColor(getColor(R.color.textcolor));
        }

        return timelineItem;
    }

    private String getTimelineDate(int stage, Long returnRequestTime, Long inspectionTime, Long completionTime) {
        switch (stage) {
            case 0: // Return Requested
                return returnRequestTime != null ? formatDateTime(returnRequestTime) : null;
            case 3: // Inspection Started
            case 4: // Inspection Completed
                return inspectionTime != null ? formatDateTime(inspectionTime) : null;
            case 5: // Deposit Refunded
                return completionTime != null ? formatDateTime(completionTime) : null;
            default:
                return null;
        }
    }

    private boolean isTimelineCompleted(int stage, String currentStatus) {
        if (currentStatus == null) return false;

        switch (stage) {
            case 0: // Return Requested
                return true; // Always completed if we're viewing this page
            case 1: // Return Arranged
                return !"ReturnRequested".equals(currentStatus);
            case 2: // Item Received
                return "AwaitingInspection".equals(currentStatus) || "Completed".equals(currentStatus);
            case 3: // Inspection Started
            case 4: // Inspection Completed
            case 5: // Deposit Refunded
                return "Completed".equals(currentStatus);
            default:
                return false;
        }
    }

    private void updateActionButtons(DataSnapshot bookingSnapshot) {
        String status = getStringValue(bookingSnapshot, "status");
        String returnMethod = getStringValue(bookingSnapshot, "returnMethod");

        cardActionRequired.setVisibility(View.GONE);
        btnMainAction.setVisibility(View.VISIBLE);

        if ("ReturnRequested".equals(status) && "OwnerPickup".equals(returnMethod)) {
            // Action required: Arrange pickup
            cardActionRequired.setVisibility(View.VISIBLE);
            tvActionDescription.setText("Borrower has requested owner pickup. Please arrange the pickup schedule.");
            btnTakeAction.setText("Arrange Pickup");
            btnMainAction.setText("Start Inspection");
            btnMainAction.setVisibility(View.GONE);
        } else if ("AwaitingInspection".equals(status)) {
            // Action: Start inspection
            btnMainAction.setText("Start Inspection");
        } else if ("Completed".equals(status)) {
            // Rental completed
            btnMainAction.setText("View Details");
        } else {
            btnMainAction.setVisibility(View.GONE);
        }
    }

    private void handleTakeAction() {
        // Handle the specific action needed
        bookingsRef.child(bookingId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String status = getStringValue(snapshot, "status");
                String returnMethod = getStringValue(snapshot, "returnMethod");

                if ("ReturnRequested".equals(status) && "OwnerPickup".equals(returnMethod)) {
                    // Mark as ready for pickup arrangement
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("status", "AwaitingInspection");
                    updates.put("pickupArrangedTime", System.currentTimeMillis());

                    bookingsRef.child(bookingId).updateChildren(updates)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(activity_return_rentals.this, "Pickup arrangement confirmed", Toast.LENGTH_SHORT).show();
                                loadBookingDetails();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(activity_return_rentals.this, "Failed to update status", Toast.LENGTH_SHORT).show();
                            });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(activity_return_rentals.this, "Failed to verify status", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleMainAction() {
        bookingsRef.child(bookingId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String status = getStringValue(snapshot, "status");

                if ("AwaitingInspection".equals(status)) {
                    // Start inspection
                    Intent intent = new Intent(activity_return_rentals.this, activity_inspection.class);
                    intent.putExtra("bookingId", bookingId);
                    intent.putExtra("productId", productId);
                    String renterId = getStringValue(snapshot, "renterId");
                    intent.putExtra("renterId", renterId);
                    startActivity(intent);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(activity_return_rentals.this, "Failed to verify status", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void contactBorrower() {
        // Implement contact borrower functionality
        Toast.makeText(this, "Open chat with borrower", Toast.LENGTH_SHORT).show();
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

    private String getStatusDisplayText(String status) {
        if (status == null) return "Unknown";

        switch (status) {
            case "ReturnRequested": return "Return Requested";
            case "AwaitingInspection": return "Awaiting Inspection";
            case "Completed": return "Completed";
            default: return status;
        }
    }

    private String formatDateTime(Long timestamp) {
        if (timestamp == null) return null;
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
}
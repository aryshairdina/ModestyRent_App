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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class activity_rentals_details_owner extends AppCompatActivity {

    private String bookingId, productId, ownerId, currentUserId, renterId;
    private String deliveryOption = "";
    private String productNameStr = "";
    private String bookingNumberStr = "";
    private String renterName = "";

    private ImageView backButton;
    private LinearLayout statusTimeline;
    private TextView productName, bookingNumber, rentalPeriod;
    private TextView rentalAmount, depositAmount, totalAmount;
    private TextView deliveryOptionText, deliveryAddress, ownerContact;

    // Owner Action Buttons
    private MaterialButton btnMarkOutForDelivery, btnConfirmReadyPickup;
    private MaterialButton btnInspectReturn, btnCompleteRental;
    private LinearLayout ownerActionsLayout;

    private DatabaseReference bookingsRef, productsRef, usersRef, disputesRef, chatsRef;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rentals_details_owner);

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
        statusTimeline = findViewById(R.id.statusTimeline);

        productName = findViewById(R.id.productName);
        bookingNumber = findViewById(R.id.bookingNumber);
        rentalPeriod = findViewById(R.id.rentalPeriod);

        rentalAmount = findViewById(R.id.rentalAmount);
        depositAmount = findViewById(R.id.depositAmount);
        totalAmount = findViewById(R.id.totalAmount);

        deliveryOptionText = findViewById(R.id.deliveryOption);
        deliveryAddress = findViewById(R.id.deliveryAddress);
        ownerContact = findViewById(R.id.ownerContact);

        // Owner buttons
        btnMarkOutForDelivery = findViewById(R.id.btnMarkOutForDelivery);
        btnConfirmReadyPickup = findViewById(R.id.btnConfirmReadyPickup);
        btnInspectReturn = findViewById(R.id.btnInspectReturn);
        btnCompleteRental = findViewById(R.id.btnCompleteRental);
        ownerActionsLayout = findViewById(R.id.ownerActionsLayout);

        // Back button
        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(activity_rentals_details_owner.this, activity_booking_requests.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        setupButtonListeners();
    }

    private void setupFirebase() {
        bookingsRef = FirebaseDatabase.getInstance().getReference("bookings");
        productsRef = FirebaseDatabase.getInstance().getReference("products");
        usersRef = FirebaseDatabase.getInstance().getReference("users");
        disputesRef = FirebaseDatabase.getInstance().getReference("disputes");
        chatsRef = FirebaseDatabase.getInstance().getReference("chats");
    }

    private void setupButtonListeners() {
        btnMarkOutForDelivery.setOnClickListener(v -> markOutForDelivery());
        btnConfirmReadyPickup.setOnClickListener(v -> confirmReadyForPickup());
        btnInspectReturn.setOnClickListener(v -> inspectReturnedItem());
        btnCompleteRental.setOnClickListener(v -> completeRental());
    }

    private void loadBookingDetails() {
        bookingsRef.child(bookingId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    try {
                        productId = getStringValue(snapshot, "productId");
                        ownerId = getStringValue(snapshot, "ownerId");
                        deliveryOption = getStringValue(snapshot, "deliveryOption");

                        // Store renterId from booking data
                        renterId = getStringValue(snapshot, "renterId");

                        // Store product name and booking number
                        productNameStr = getStringValue(snapshot, "productName");
                        bookingNumberStr = getStringValue(snapshot, "bookingNumber");

                        // Load renter name
                        loadRenterInfo(renterId);

                        updateBookingUI(snapshot);
                        loadDeliveryInformation(snapshot, renterId);

                    } catch (Exception e) {
                        Toast.makeText(activity_rentals_details_owner.this, "Error loading booking details", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(activity_rentals_details_owner.this, "Booking not found", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(activity_rentals_details_owner.this, "Failed to load booking", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadRenterInfo(String renterId) {
        if (renterId != null) {
            usersRef.child(renterId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        renterName = getStringValue(snapshot, "fullName");
                        if (renterName == null) {
                            renterName = getStringValue(snapshot, "name");
                        }
                        if (renterName == null) {
                            renterName = "Borrower";
                        }
                    } else {
                        renterName = "Borrower";
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    renterName = "Borrower";
                }
            });
        }
    }

    private void updateBookingUI(DataSnapshot bookingSnapshot) {
        productName.setText(getStringValue(bookingSnapshot, "productName"));
        bookingNumber.setText("#" + getStringValue(bookingSnapshot, "bookingNumber"));

        Long startDate = getLongValue(bookingSnapshot, "startDate");
        Long endDate = getLongValue(bookingSnapshot, "endDate");
        Integer rentalDays = getIntegerValue(bookingSnapshot, "rentalDays");

        if (startDate != null && endDate != null) {
            String periodText = formatDate(startDate) + " - " + formatDate(endDate);
            if (rentalDays != null) {
                periodText += " (" + rentalDays + " days)";
            }
            rentalPeriod.setText(periodText);
        }

        Double rentalAmt = getDoubleValue(bookingSnapshot, "rentalAmount");
        Double depositAmt = getDoubleValue(bookingSnapshot, "depositAmount");
        Double totalAmt = getDoubleValue(bookingSnapshot, "totalAmount");

        if (rentalAmt != null) rentalAmount.setText(String.format("RM %.2f", rentalAmt));
        if (depositAmt != null) depositAmount.setText(String.format("RM %.2f", depositAmt));
        if (totalAmt != null) totalAmount.setText(String.format("RM %.2f", totalAmt));

        String deliveryOpt = getStringValue(bookingSnapshot, "deliveryOption");
        deliveryOptionText.setText(deliveryOpt != null ? deliveryOpt : "Pickup");

        String status = getStringValue(bookingSnapshot, "status");
        String deliveryStatus = getStringValue(bookingSnapshot, "deliveryStatus");

        updateStatusTimeline(bookingSnapshot);
        updateOwnerActions(status, deliveryStatus, deliveryOption);
    }

    private void loadDeliveryInformation(DataSnapshot bookingSnapshot, String renterId) {
        String deliveryOpt = getStringValue(bookingSnapshot, "deliveryOption");

        if ("Delivery".equals(deliveryOpt)) {
            // For delivery: show borrower's delivery address from booking
            String deliveryAddressText = getStringValue(bookingSnapshot, "deliveryAddress");
            deliveryAddress.setText(deliveryAddressText != null ? deliveryAddressText : "Address not available");
            loadBorrowerContactInfo(bookingSnapshot, renterId);
        } else {
            // For pickup: show owner's address (from current user's profile)
            loadOwnerAddressForPickup(currentUserId);
            loadBorrowerContactInfo(bookingSnapshot, renterId);
        }
    }

    private void loadOwnerAddressForPickup(String ownerId) {
        if (ownerId != null) {
            usersRef.child(ownerId).child("address").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String ownerAddress = snapshot.getValue(String.class);
                        deliveryAddress.setText(ownerAddress != null ? ownerAddress : "Address not available");
                    } else {
                        deliveryAddress.setText("Address not available");
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    deliveryAddress.setText("Address not available");
                }
            });
        }
    }

    private void loadBorrowerContactInfo(DataSnapshot bookingSnapshot, String renterId) {
        // First try to get borrower info from booking data (renterName & renterPhone)
        String renterNameFromBooking = getStringValue(bookingSnapshot, "renterName");
        String renterPhoneFromBooking = getStringValue(bookingSnapshot, "renterPhone");

        if (renterNameFromBooking != null && renterPhoneFromBooking != null) {
            // Use data from booking
            ownerContact.setText("Borrower: " + renterNameFromBooking + " (" + renterPhoneFromBooking + ")");
        } else if (renterId != null) {
            // Fallback to user profile if booking data doesn't have it
            usersRef.child(renterId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String borrowerName = getStringValue(snapshot, "fullName");
                        if (borrowerName == null) {
                            borrowerName = getStringValue(snapshot, "name");
                        }
                        String borrowerPhone = getStringValue(snapshot, "phone");

                        if (borrowerName != null && borrowerPhone != null) {
                            ownerContact.setText("Borrower: " + borrowerName + " (" + borrowerPhone + ")");
                        } else {
                            ownerContact.setText("Borrower information not available");
                        }
                    } else {
                        ownerContact.setText("Borrower information not available");
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    ownerContact.setText("Borrower information not available");
                }
            });
        } else {
            ownerContact.setText("Borrower information not available");
        }
    }

    private void markOutForDelivery() {
        Map<String, Object> updates = new HashMap<>();
        updates.put("deliveryStatus", "OutForDelivery");
        updates.put("deliveryLeaveTime", System.currentTimeMillis());
        updates.put("status", "OutForDelivery");

        bookingsRef.child(bookingId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Marked as out for delivery", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update delivery status", Toast.LENGTH_SHORT).show();
                });
    }

    private void confirmReadyForPickup() {
        Map<String, Object> updates = new HashMap<>();
        updates.put("deliveryStatus", "ReadyForPickup");
        updates.put("readyForPickupTime", System.currentTimeMillis());
        updates.put("status", "ReadyForPickup");

        bookingsRef.child(bookingId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Item ready for pickup", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update status", Toast.LENGTH_SHORT).show();
                });
    }

    private void inspectReturnedItem() {
        // Check current status to decide which action
        bookingsRef.child(bookingId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String status = getStringValue(snapshot, "status");
                String deliveryStatus = getStringValue(snapshot, "deliveryStatus");

                if ("ReturnRequested".equals(status) || "returnrequested".equalsIgnoreCase(status)) {
                    // Mark as received first
                    markReturnAsReceived();
                } else if ("AwaitingInspection".equals(status) || "awaitinginspection".equalsIgnoreCase(status)) {
                    // Open inspection screen
                    openInspectionScreen();
                } else {
                    // Default: open inspection
                    openInspectionScreen();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                openInspectionScreen();
            }
        });
    }

    private void markReturnAsReceived() {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "AwaitingInspection");
        updates.put("deliveryStatus", "CompletedDelivery");
        updates.put("returnTime", System.currentTimeMillis());

        bookingsRef.child(bookingId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Return marked as received", Toast.LENGTH_SHORT).show();

                    // Open inspection screen after marking as received
                    openInspectionScreen();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update return status", Toast.LENGTH_SHORT).show();
                });
    }

    private void openInspectionScreen() {
        if (renterId != null && !renterId.isEmpty()) {
            Intent intent = new Intent(activity_rentals_details_owner.this, activity_inspection.class);
            intent.putExtra("bookingId", bookingId);
            intent.putExtra("productId", productId != null ? productId : "");
            intent.putExtra("renterId", renterId);
            intent.putExtra("ownerId", currentUserId);

            // Pass product name
            intent.putExtra("productName", productNameStr);

            startActivity(intent);
        } else {
            Toast.makeText(activity_rentals_details_owner.this,
                    "Error: Could not find borrower information", Toast.LENGTH_SHORT).show();
        }
    }

    private void completeRental() {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "Completed");
        updates.put("depositReturned", true);
        updates.put("completionTime", System.currentTimeMillis());

        // Also update deliveryStatus if needed
        if ("Delivery".equals(deliveryOption)) {
            updates.put("deliveryStatus", "CompletedDelivery");
        }

        bookingsRef.child(bookingId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Rental completed successfully", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to complete rental", Toast.LENGTH_SHORT).show();
                });
    }

    // ============= HELPER METHODS =============

    private void updateOwnerActions(String status, String deliveryStatus, String deliveryOption) {
        btnMarkOutForDelivery.setVisibility(View.GONE);
        btnConfirmReadyPickup.setVisibility(View.GONE);
        btnInspectReturn.setVisibility(View.GONE);
        btnCompleteRental.setVisibility(View.GONE);
        ownerActionsLayout.setVisibility(View.GONE);

        if (status == null) return;

        String statusLower = status.toLowerCase();
        String deliveryStatusLower = deliveryStatus != null ? deliveryStatus.toLowerCase() : "";

        boolean hasAction = false;

        if ("Delivery".equals(deliveryOption)) {
            if (statusLower.contains("preparingdelivery")) {
                if (!deliveryStatusLower.contains("outfordelivery")) {
                    btnMarkOutForDelivery.setVisibility(View.VISIBLE);
                    hasAction = true;
                }
            } else if (statusLower.contains("returnrequested")) {
                btnInspectReturn.setText("Mark as Received");
                btnInspectReturn.setVisibility(View.VISIBLE);
                hasAction = true;
            } else if (statusLower.contains("awaitinginspection")) {
                btnInspectReturn.setText("Inspect Return");
                btnInspectReturn.setVisibility(View.VISIBLE);
                hasAction = true;
            } else if (statusLower.contains("complete") || statusLower.contains("finished") || statusLower.contains("done")) {
                hasAction = true;
            }
        } else {
            if (statusLower.contains("preparingpickup")) {
                if (!deliveryStatusLower.contains("readyforpickup")) {
                    btnConfirmReadyPickup.setVisibility(View.VISIBLE);
                    hasAction = true;
                }
            } else if (statusLower.contains("returnrequested")) {
                btnInspectReturn.setText("Mark as Received");
                btnInspectReturn.setVisibility(View.VISIBLE);
                hasAction = true;
            } else if (statusLower.contains("awaitinginspection")) {
                btnInspectReturn.setText("Inspect Return");
                btnInspectReturn.setVisibility(View.VISIBLE);
                hasAction = true;
            } else if (statusLower.contains("complete") || statusLower.contains("finished") || statusLower.contains("done")) {
                hasAction = true;
            }
        }

        if (hasAction) {
            ownerActionsLayout.setVisibility(View.VISIBLE);
        }
    }

    // ============= STATUS TIMELINE METHODS =============

    private void updateStatusTimeline(DataSnapshot bookingSnapshot) {
        statusTimeline.removeAllViews();

        Long bookingDate = getLongValue(bookingSnapshot, "bookingDate");
        Long paymentDate = getLongValue(bookingSnapshot, "paymentDate");
        String status = getStringValue(bookingSnapshot, "status");
        String deliveryStatus = getStringValue(bookingSnapshot, "deliveryStatus");

        // Get timestamps
        Long completionTime = getLongValue(bookingSnapshot, "completionTime");
        Long inspectionTime = getLongValue(bookingSnapshot, "inspectionTime");
        Long returnTime = getLongValue(bookingSnapshot, "returnTime");

        if ("Delivery".equals(deliveryOption)) {
            String[] statusFlow = {"Confirmed", "Preparing Delivery", "Out for Delivery", "On Rent", "Return", "Inspection", "Completed"};
            String[] statusDescriptions = {
                    "Booking confirmed and payment received",
                    "Owner is preparing the item for delivery",
                    "Item is out for delivery to borrower",
                    "Item is with borrower during rental period",
                    "Borrower has returned the item",
                    "Owner is inspecting the returned item",
                    "Rental completed successfully"
            };
            String[] statusIcons = {"ic_check", "ic_preparing", "ic_delivery", "ic_onrent", "ic_return", "ic_inspection", "ic_completed"};

            // Get all timestamps
            Long[] statusTimestamps = {
                    paymentDate != null ? paymentDate : bookingDate,
                    getLongValue(bookingSnapshot, "preparationTime"),
                    getLongValue(bookingSnapshot, "deliveryLeaveTime"),
                    getLongValue(bookingSnapshot, "deliveryTime"),
                    returnTime,
                    inspectionTime,
                    completionTime
            };

            for (int i = 0; i < statusFlow.length; i++) {
                String currentStatus = statusFlow[i];
                String dateText = getDateTextForStatus(statusTimestamps[i], statusFlow[i], status, deliveryStatus);
                View statusItem = createStatusItem(currentStatus, statusDescriptions[i], statusIcons[i], dateText,
                        status, deliveryStatus, i == statusFlow.length - 1);
                statusTimeline.addView(statusItem);
            }
        } else {
            String[] statusFlow = {"Confirmed", "Preparing Pickup", "Ready for Pickup", "On Rent", "Return", "Inspection", "Completed"};
            String[] statusDescriptions = {
                    "Booking confirmed and payment received",
                    "Owner is preparing the item for pickup",
                    "Item is ready for pickup by borrower",
                    "Item is with borrower during rental period",
                    "Borrower has returned the item",
                    "Owner is inspecting the returned item",
                    "Rental completed successfully"
            };
            String[] statusIcons = {"ic_check", "ic_preparing", "ic_ready_pickup", "ic_onrent", "ic_return", "ic_inspection", "ic_completed"};

            // Get all timestamps
            Long[] statusTimestamps = {
                    paymentDate != null ? paymentDate : bookingDate,
                    getLongValue(bookingSnapshot, "preparationTime"),
                    getLongValue(bookingSnapshot, "readyForPickupTime"),
                    getLongValue(bookingSnapshot, "pickupTime"),
                    returnTime,
                    inspectionTime,
                    completionTime
            };

            for (int i = 0; i < statusFlow.length; i++) {
                String currentStatus = statusFlow[i];
                String dateText = getDateTextForStatus(statusTimestamps[i], statusFlow[i], status, deliveryStatus);
                View statusItem = createStatusItem(currentStatus, statusDescriptions[i], statusIcons[i], dateText,
                        status, deliveryStatus, i == statusFlow.length - 1);
                statusTimeline.addView(statusItem);
            }
        }
    }

    private String getDateTextForStatus(Long timestamp, String timelineStatus, String currentStatus, String deliveryStatus) {
        if (timestamp == null || timestamp == 0) {
            // Check if this status should be completed based on current booking status
            boolean isCompleted = isStatusCompletedSimple(timelineStatus, currentStatus, deliveryStatus);
            if (isCompleted) {
                // If it should be completed but has no timestamp, show current time
                return formatDateTime(System.currentTimeMillis());
            } else {
                return "Pending";
            }
        } else {
            return formatDateTime(timestamp);
        }
    }

    private boolean isStatusCompletedSimple(String timelineStatus, String currentStatus, String deliveryStatus) {
        if (currentStatus == null) return false;

        String currentStatusLower = currentStatus.toLowerCase();
        String deliveryStatusLower = deliveryStatus != null ? deliveryStatus.toLowerCase() : "";

        // Basic logic: if current status is completed, all previous statuses should be completed
        if (currentStatusLower.contains("complete") ||
                currentStatusLower.contains("finished") ||
                currentStatusLower.contains("done")) {
            return true;
        }

        // Add more specific logic if needed
        return false;
    }

    private View createStatusItem(String statusText, String description, String iconName, String date,
                                  String currentStatus, String deliveryStatus, boolean isLast) {
        View statusItem = LayoutInflater.from(this).inflate(R.layout.item_status_timeline, statusTimeline, false);

        ImageView statusIcon = statusItem.findViewById(R.id.statusIcon);
        TextView statusTextView = statusItem.findViewById(R.id.statusText);
        TextView statusDescriptionView = statusItem.findViewById(R.id.statusDescription);
        TextView statusDateView = statusItem.findViewById(R.id.statusDate);
        View statusIndicator = statusItem.findViewById(R.id.statusIndicator);
        View connectorLine = statusItem.findViewById(R.id.connectorLine);

        int iconResource = getResources().getIdentifier(iconName, "drawable", getPackageName());
        if (iconResource == 0) {
            iconResource = R.drawable.ic_check;
        }
        statusIcon.setImageResource(iconResource);

        statusTextView.setText(statusText);
        statusDescriptionView.setText(description);
        statusDateView.setText(date);

        if (isLast) {
            connectorLine.setVisibility(View.GONE);
        }

        boolean isCompleted = isStatusCompleted(statusText, currentStatus, deliveryStatus);
        boolean isCurrent = isCurrentStatus(statusText, currentStatus, deliveryStatus);

        if (isCompleted) {
            statusIndicator.setBackgroundResource(R.drawable.circle_completed);
            statusIcon.setColorFilter(getColor(R.color.primary));
            statusTextView.setTextColor(getColor(R.color.primary));
            statusDescriptionView.setTextColor(getColor(R.color.textcolor));
            statusDateView.setTextColor(getColor(R.color.textcolor));
        } else if (isCurrent) {
            statusIndicator.setBackgroundResource(R.drawable.circle_current);
            statusIcon.setColorFilter(getColor(R.color.primary));
            statusTextView.setTextColor(getColor(R.color.primary));
            statusDescriptionView.setTextColor(getColor(R.color.primary));
            statusDateView.setTextColor(getColor(R.color.primary));
        } else {
            statusIndicator.setBackgroundResource(R.drawable.circle_pending);
            statusIcon.setColorFilter(getColor(R.color.textcolor));
            statusTextView.setTextColor(getColor(R.color.textcolor));
            statusDescriptionView.setTextColor(getColor(R.color.textcolor));
            statusDateView.setTextColor(getColor(R.color.textcolor));
        }

        return statusItem;
    }

    private boolean isStatusCompleted(String timelineStatus, String currentStatus, String deliveryStatus) {
        if (currentStatus == null) return false;

        String currentStatusLower = currentStatus.toLowerCase();
        String deliveryStatusLower = deliveryStatus != null ? deliveryStatus.toLowerCase() : "";

        if ("Delivery".equals(deliveryOption)) {
            switch (timelineStatus) {
                case "Confirmed":
                    return true;
                case "Preparing Delivery":
                    return "preparingdelivery".equals(currentStatusLower) ||
                            "outfordelivery".equals(deliveryStatusLower) ||
                            "onrent".equals(currentStatusLower) ||
                            "returnrequested".equals(currentStatusLower) ||
                            "awaitinginspection".equals(currentStatusLower) ||
                            currentStatusLower.contains("complete") ||
                            currentStatusLower.contains("finished") ||
                            currentStatusLower.contains("done");
                case "Out for Delivery":
                    return "outfordelivery".equals(deliveryStatusLower) ||
                            "onrent".equals(currentStatusLower) ||
                            "returnrequested".equals(currentStatusLower) ||
                            "awaitinginspection".equals(currentStatusLower) ||
                            currentStatusLower.contains("complete") ||
                            currentStatusLower.contains("finished") ||
                            currentStatusLower.contains("done");
                case "On Rent":
                    return "onrent".equals(currentStatusLower) ||
                            "returnrequested".equals(currentStatusLower) ||
                            "awaitinginspection".equals(currentStatusLower) ||
                            currentStatusLower.contains("complete") ||
                            currentStatusLower.contains("finished") ||
                            currentStatusLower.contains("done");
                case "Return":
                    return "returnrequested".equals(currentStatusLower) ||
                            "awaitinginspection".equals(currentStatusLower) ||
                            currentStatusLower.contains("complete") ||
                            currentStatusLower.contains("finished") ||
                            currentStatusLower.contains("done");
                case "Inspection":
                    return "awaitinginspection".equals(currentStatusLower) ||
                            currentStatusLower.contains("complete") ||
                            currentStatusLower.contains("finished") ||
                            currentStatusLower.contains("done");
                case "Completed":
                    return currentStatusLower.contains("complete") ||
                            currentStatusLower.contains("finished") ||
                            currentStatusLower.contains("done");
                default:
                    return false;
            }
        } else {
            switch (timelineStatus) {
                case "Confirmed":
                    return true;
                case "Preparing Pickup":
                    return "preparingpickup".equals(currentStatusLower) ||
                            "readyforpickup".equals(deliveryStatusLower) ||
                            "onrent".equals(currentStatusLower) ||
                            "returnrequested".equals(currentStatusLower) ||
                            "awaitinginspection".equals(currentStatusLower) ||
                            currentStatusLower.contains("complete") ||
                            currentStatusLower.contains("finished") ||
                            currentStatusLower.contains("done");
                case "Ready for Pickup":
                    return "readyforpickup".equals(deliveryStatusLower) ||
                            "onrent".equals(currentStatusLower) ||
                            "returnrequested".equals(currentStatusLower) ||
                            "awaitinginspection".equals(currentStatusLower) ||
                            currentStatusLower.contains("complete") ||
                            currentStatusLower.contains("finished") ||
                            currentStatusLower.contains("done");
                case "On Rent":
                    return "onrent".equals(currentStatusLower) ||
                            "returnrequested".equals(currentStatusLower) ||
                            "awaitinginspection".equals(currentStatusLower) ||
                            currentStatusLower.contains("complete") ||
                            currentStatusLower.contains("finished") ||
                            currentStatusLower.contains("done");
                case "Return":
                    return "returnrequested".equals(currentStatusLower) ||
                            "awaitinginspection".equals(currentStatusLower) ||
                            currentStatusLower.contains("complete") ||
                            currentStatusLower.contains("finished") ||
                            currentStatusLower.contains("done");
                case "Inspection":
                    return "awaitinginspection".equals(currentStatusLower) ||
                            currentStatusLower.contains("complete") ||
                            currentStatusLower.contains("finished") ||
                            currentStatusLower.contains("done");
                case "Completed":
                    return currentStatusLower.contains("complete") ||
                            currentStatusLower.contains("finished") ||
                            currentStatusLower.contains("done");
                default:
                    return false;
            }
        }
    }

    private boolean isCurrentStatus(String timelineStatus, String currentStatus, String deliveryStatus) {
        if (currentStatus == null) return false;

        String currentStatusLower = currentStatus.toLowerCase();
        String deliveryStatusLower = deliveryStatus != null ? deliveryStatus.toLowerCase() : "";

        if ("Delivery".equals(deliveryOption)) {
            return (timelineStatus.equals("Preparing Delivery") && "preparingdelivery".equals(currentStatusLower)) ||
                    (timelineStatus.equals("Out for Delivery") && "outfordelivery".equals(deliveryStatusLower)) ||
                    (timelineStatus.equals("On Rent") && "onrent".equals(currentStatusLower)) ||
                    (timelineStatus.equals("Return") && "returnrequested".equals(currentStatusLower)) ||
                    (timelineStatus.equals("Inspection") && "awaitinginspection".equals(currentStatusLower)) ||
                    (timelineStatus.equals("Completed") &&
                            (currentStatusLower.contains("complete") ||
                                    currentStatusLower.contains("finished") ||
                                    currentStatusLower.contains("done")));
        } else {
            return (timelineStatus.equals("Preparing Pickup") && "preparingpickup".equals(currentStatusLower)) ||
                    (timelineStatus.equals("Ready for Pickup") && "readyforpickup".equals(deliveryStatusLower)) ||
                    (timelineStatus.equals("On Rent") && "onrent".equals(currentStatusLower)) ||
                    (timelineStatus.equals("Return") && "returnrequested".equals(currentStatusLower)) ||
                    (timelineStatus.equals("Inspection") && "awaitinginspection".equals(currentStatusLower)) ||
                    (timelineStatus.equals("Completed") &&
                            (currentStatusLower.contains("complete") ||
                                    currentStatusLower.contains("finished") ||
                                    currentStatusLower.contains("done")));
        }
    }

    // ============= UTILITY METHODS =============

    private String getStringValue(DataSnapshot snapshot, String key) {
        DataSnapshot child = snapshot.child(key);
        return child.exists() && child.getValue() != null ? child.getValue().toString() : null;
    }

    private Long getLongValue(DataSnapshot snapshot, String key) {
        DataSnapshot child = snapshot.child(key);
        if (child.exists()) {
            Object value = child.getValue();
            if (value instanceof Long) return (Long) value;
            if (value instanceof Integer) return ((Integer) value).longValue();
            if (value instanceof String) {
                try {
                    return Long.parseLong((String) value);
                } catch (NumberFormatException e) {
                    return null;
                }
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
                try {
                    return Double.parseDouble((String) value);
                } catch (NumberFormatException e) {
                    return null;
                }
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
                try {
                    return Integer.parseInt((String) value);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private String formatDate(Long timestamp) {
        if (timestamp == null) return "N/A";
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    private String formatDateTime(Long timestamp) {
        if (timestamp == null || timestamp == 0) return null;
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
}
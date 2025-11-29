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

    private String bookingId, productId, ownerId, currentUserId;
    private String deliveryOption = "";

    private ImageView backButton;
    private LinearLayout statusTimeline;
    private TextView productName, bookingNumber, rentalPeriod;
    private TextView rentalAmount, depositAmount, totalAmount;
    private TextView deliveryOptionText, deliveryAddress, ownerContact;

    // Owner Action Buttons
    private MaterialButton btnMarkOutForDelivery, btnConfirmReadyPickup;
    private MaterialButton btnInspectReturn, btnCompleteRental, btnRaiseDispute, btnRefundDeposit;
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
        btnRaiseDispute = findViewById(R.id.btnRaiseDispute);
        btnRefundDeposit = findViewById(R.id.btnRefundDeposit);
        ownerActionsLayout = findViewById(R.id.ownerActionsLayout);

        // 🔹 UPDATED: back button now goes to activity_booking_request
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

        // Default: open inspection screen (we override this for "Mark as Received" inside updateOwnerActions)
        btnInspectReturn.setOnClickListener(v -> inspectReturnedItem());

        btnCompleteRental.setOnClickListener(v -> completeRental());
        btnRaiseDispute.setOnClickListener(v -> raiseDispute());
        btnRefundDeposit.setOnClickListener(v -> refundDeposit());
    }

    private void loadBookingDetails() {
        bookingsRef.child(bookingId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    try {
                        String productId = getStringValue(snapshot, "productId");
                        String ownerId = getStringValue(snapshot, "ownerId");
                        deliveryOption = getStringValue(snapshot, "deliveryOption");

                        updateBookingUI(snapshot);
                        loadDeliveryInformation(snapshot, ownerId);

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

    private void loadDeliveryInformation(DataSnapshot bookingSnapshot, String ownerId) {
        String deliveryOpt = getStringValue(bookingSnapshot, "deliveryOption");

        if ("Delivery".equals(deliveryOpt)) {
            String deliveryAddressText = getStringValue(bookingSnapshot, "deliveryAddress");
            deliveryAddress.setText(deliveryAddressText != null ? deliveryAddressText : "Address not available");
            loadOwnerContactInfo(ownerId);
        } else {
            loadOwnerAddressForPickup(ownerId);
            loadOwnerContactInfo(ownerId);
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

    private void loadOwnerContactInfo(String ownerId) {
        if (ownerId != null) {
            usersRef.child(ownerId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String ownerName = getStringValue(snapshot, "fullName");
                        String ownerPhone = getStringValue(snapshot, "phone");

                        if (ownerName != null && ownerPhone != null) {
                            ownerContact.setText("Owner: " + ownerName + " (" + ownerPhone + ")");
                        } else {
                            ownerContact.setText("Owner information not available");
                        }
                    } else {
                        ownerContact.setText("Owner information not available");
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    ownerContact.setText("Owner information not available");
                }
            });
        }
    }

    private void updateStatusTimeline(DataSnapshot bookingSnapshot) {
        statusTimeline.removeAllViews();

        Long bookingDate = getLongValue(bookingSnapshot, "bookingDate");
        Long paymentDate = getLongValue(bookingSnapshot, "paymentDate");
        String status = getStringValue(bookingSnapshot, "status");
        String deliveryStatus = getStringValue(bookingSnapshot, "deliveryStatus");

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
            String[] statusDates = {
                    formatDateTime(paymentDate != null ? paymentDate : bookingDate),
                    formatDateTime(getLongValue(bookingSnapshot, "preparationTime")),
                    formatDateTime(getLongValue(bookingSnapshot, "deliveryLeaveTime")),
                    formatDateTime(getLongValue(bookingSnapshot, "deliveryTime")),
                    formatDateTime(getLongValue(bookingSnapshot, "returnTime")),
                    formatDateTime(getLongValue(bookingSnapshot, "inspectionTime")),
                    formatDateTime(getLongValue(bookingSnapshot, "completionTime"))
            };

            for (int i = 0; i < statusFlow.length; i++) {
                String currentStatus = statusFlow[i];
                View statusItem = createStatusItem(currentStatus, statusDescriptions[i], statusIcons[i], statusDates[i],
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
            String[] statusDates = {
                    formatDateTime(paymentDate != null ? paymentDate : bookingDate),
                    formatDateTime(getLongValue(bookingSnapshot, "preparationTime")),
                    formatDateTime(getLongValue(bookingSnapshot, "readyForPickupTime")),
                    formatDateTime(getLongValue(bookingSnapshot, "pickupTime")),
                    formatDateTime(getLongValue(bookingSnapshot, "returnTime")),
                    formatDateTime(getLongValue(bookingSnapshot, "inspectionTime")),
                    formatDateTime(getLongValue(bookingSnapshot, "completionTime"))
            };

            for (int i = 0; i < statusFlow.length; i++) {
                String currentStatus = statusFlow[i];
                View statusItem = createStatusItem(currentStatus, statusDescriptions[i], statusIcons[i], statusDates[i],
                        status, deliveryStatus, i == statusFlow.length - 1);
                statusTimeline.addView(statusItem);
            }
        }
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
        statusDateView.setText(date != null ? date : "Pending");

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
            statusDateView.setText("Pending");
        }

        return statusItem;
    }

    private boolean isStatusCompleted(String timelineStatus, String currentStatus, String deliveryStatus) {
        if ("Delivery".equals(deliveryOption)) {
            switch (timelineStatus) {
                case "Confirmed":
                    return true;
                case "Preparing Delivery":
                    return "PreparingDelivery".equals(currentStatus) ||
                            "OutForDelivery".equals(deliveryStatus) ||
                            "OnRent".equals(currentStatus) ||
                            "ReturnRequested".equals(currentStatus) ||
                            "AwaitingInspection".equals(currentStatus) ||
                            "Completed".equals(currentStatus);
                case "Out for Delivery":
                    return "OutForDelivery".equals(deliveryStatus) ||
                            "OnRent".equals(currentStatus) ||
                            "ReturnRequested".equals(currentStatus) ||
                            "AwaitingInspection".equals(currentStatus) ||
                            "Completed".equals(currentStatus);
                case "On Rent":
                    return "OnRent".equals(currentStatus) ||
                            "ReturnRequested".equals(currentStatus) ||
                            "AwaitingInspection".equals(currentStatus) ||
                            "Completed".equals(currentStatus);
                case "Return":
                    return "AwaitingInspection".equals(currentStatus) ||
                            "Completed".equals(currentStatus);
                case "Inspection":
                    return "Completed".equals(currentStatus);
                case "Completed":
                    return "Completed".equals(currentStatus);
                default:
                    return false;
            }
        } else {
            switch (timelineStatus) {
                case "Confirmed":
                    return true;
                case "Preparing Pickup":
                    return "PreparingPickup".equals(currentStatus) ||
                            "ReadyForPickup".equals(deliveryStatus) ||
                            "OnRent".equals(currentStatus) ||
                            "ReturnRequested".equals(currentStatus) ||
                            "AwaitingInspection".equals(currentStatus) ||
                            "Completed".equals(currentStatus);
                case "Ready for Pickup":
                    return "ReadyForPickup".equals(deliveryStatus) ||
                            "OnRent".equals(currentStatus) ||
                            "ReturnRequested".equals(currentStatus) ||
                            "AwaitingInspection".equals(currentStatus) ||
                            "Completed".equals(currentStatus);
                case "On Rent":
                    return "OnRent".equals(currentStatus) ||
                            "ReturnRequested".equals(currentStatus) ||
                            "AwaitingInspection".equals(currentStatus) ||
                            "Completed".equals(currentStatus);
                case "Return":
                    return "AwaitingInspection".equals(currentStatus) ||
                            "Completed".equals(currentStatus);
                case "Inspection":
                    return "Completed".equals(currentStatus);
                case "Completed":
                    return "Completed".equals(currentStatus);
                default:
                    return false;
            }
        }
    }

    private boolean isCurrentStatus(String timelineStatus, String currentStatus, String deliveryStatus) {
        if ("Delivery".equals(deliveryOption)) {
            return (timelineStatus.equals("Preparing Delivery") && "PreparingDelivery".equals(currentStatus)) ||
                    (timelineStatus.equals("Out for Delivery") && "OutForDelivery".equals(deliveryStatus)) ||
                    (timelineStatus.equals("On Rent") && "OnRent".equals(currentStatus)) ||
                    (timelineStatus.equals("Return") && "ReturnRequested".equals(currentStatus)) ||
                    (timelineStatus.equals("Inspection") && "AwaitingInspection".equals(currentStatus)) ||
                    (timelineStatus.equals("Completed") && "Completed".equals(currentStatus));
        } else {
            return (timelineStatus.equals("Preparing Pickup") && "PreparingPickup".equals(currentStatus)) ||
                    (timelineStatus.equals("Ready for Pickup") && "ReadyForPickup".equals(deliveryStatus)) ||
                    (timelineStatus.equals("On Rent") && "OnRent".equals(currentStatus)) ||
                    (timelineStatus.equals("Return") && "ReturnRequested".equals(currentStatus)) ||
                    (timelineStatus.equals("Inspection") && "AwaitingInspection".equals(currentStatus)) ||
                    (timelineStatus.equals("Completed") && "Completed".equals(currentStatus));
        }
    }

    // OWNER ACTIONS
    private void markOutForDelivery() {
        Map<String, Object> updates = new HashMap<>();
        updates.put("deliveryStatus", "OutForDelivery");
        updates.put("deliveryLeaveTime", System.currentTimeMillis());

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

        bookingsRef.child(bookingId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Item ready for pickup", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update status", Toast.LENGTH_SHORT).show();
                });
    }

    private void inspectReturnedItem() {
        Intent intent = new Intent(this, activity_inspection.class);
        intent.putExtra("bookingId", bookingId);
        startActivity(intent);
    }

    private void markReturnAsReceived() {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "AwaitingInspection");
        updates.put("deliveryStatus", "CompletedDelivery");
        updates.put("returnTime", System.currentTimeMillis());

        bookingsRef.child(bookingId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Return marked as received", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update return status", Toast.LENGTH_SHORT).show();
                });
    }

    private void completeRental() {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "Completed");
        updates.put("depositReturned", true);
        updates.put("completionTime", System.currentTimeMillis());

        bookingsRef.child(bookingId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Rental completed successfully", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to complete rental", Toast.LENGTH_SHORT).show();
                });
    }

    private void raiseDispute() {
        String disputeId = disputesRef.push().getKey();
        Map<String, Object> disputeData = new HashMap<>();
        disputeData.put("disputeId", disputeId);
        disputeData.put("bookingId", bookingId);
        disputeData.put("raisedBy", currentUserId);
        disputeData.put("raisedAt", System.currentTimeMillis());
        disputeData.put("status", "open");

        Map<String, Object> bookingUpdates = new HashMap<>();
        bookingUpdates.put("status", "Dispute");

        disputesRef.child(disputeId).setValue(disputeData)
                .addOnSuccessListener(aVoid -> {
                    bookingsRef.child(bookingId).updateChildren(bookingUpdates)
                            .addOnSuccessListener(aVoid1 -> {
                                Toast.makeText(this, "Dispute raised successfully", Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to raise dispute", Toast.LENGTH_SHORT).show();
                });
    }

    private void refundDeposit() {
        Map<String, Object> updates = new HashMap<>();
        updates.put("depositReturnDate", System.currentTimeMillis());
        updates.put("depositReturned", true);

        bookingsRef.child(bookingId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Deposit refund processed", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to process refund", Toast.LENGTH_SHORT).show();
                });
    }

    private void updateOwnerActions(String status, String deliveryStatus, String deliveryOption) {
        btnMarkOutForDelivery.setVisibility(View.GONE);
        btnConfirmReadyPickup.setVisibility(View.GONE);
        btnInspectReturn.setVisibility(View.GONE);
        btnCompleteRental.setVisibility(View.GONE);
        btnRaiseDispute.setVisibility(View.GONE);
        btnRefundDeposit.setVisibility(View.GONE);
        ownerActionsLayout.setVisibility(View.GONE);

        if (status == null) return;

        boolean hasAction = false;

        if ("Delivery".equals(deliveryOption)) {
            switch (status) {
                case "PreparingDelivery":
                    if (!"OutForDelivery".equals(deliveryStatus)) {
                        btnMarkOutForDelivery.setVisibility(View.VISIBLE);
                        hasAction = true;
                    }
                    break;

                case "OutForDelivery":
                    break;

                case "OnRent":
                    break;

                case "ReturnRequested":
                    btnInspectReturn.setText("Mark as Received");
                    btnInspectReturn.setVisibility(View.VISIBLE);
                    btnInspectReturn.setOnClickListener(v -> markReturnAsReceived());
                    hasAction = true;
                    break;

                case "AwaitingInspection":
                    btnInspectReturn.setText("Inspect Return");
                    btnInspectReturn.setVisibility(View.VISIBLE);
                    btnInspectReturn.setOnClickListener(v -> inspectReturnedItem());
                    hasAction = true;
                    break;

                case "Completed":
                    checkAndShowRefundButton();
                    hasAction = true;
                    break;

                case "Dispute":
                    break;
            }
        } else {
            switch (status) {
                case "PreparingPickup":
                    if (!"ReadyForPickup".equals(deliveryStatus)) {
                        btnConfirmReadyPickup.setVisibility(View.VISIBLE);
                        hasAction = true;
                    }
                    break;

                case "ReadyForPickup":
                    break;

                case "OnRent":
                    break;

                case "ReturnRequested":
                    btnInspectReturn.setText("Mark as Received");
                    btnInspectReturn.setVisibility(View.VISIBLE);
                    btnInspectReturn.setOnClickListener(v -> markReturnAsReceived());
                    hasAction = true;
                    break;

                case "AwaitingInspection":
                    btnInspectReturn.setText("Inspect Return");
                    btnInspectReturn.setVisibility(View.VISIBLE);
                    btnInspectReturn.setOnClickListener(v -> inspectReturnedItem());
                    hasAction = true;
                    break;

                case "Completed":
                    checkAndShowRefundButton();
                    hasAction = true;
                    break;

                case "Dispute":
                    break;
            }
        }

        if (hasAction) {
            ownerActionsLayout.setVisibility(View.VISIBLE);
        }
    }

    private void checkAndShowRefundButton() {
        bookingsRef.child(bookingId).child("depositReturned").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Boolean depositReturned = snapshot.getValue(Boolean.class);
                    if (depositReturned == null || !depositReturned) {
                        btnRefundDeposit.setVisibility(View.VISIBLE);
                    }
                } else {
                    btnRefundDeposit.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                btnRefundDeposit.setVisibility(View.GONE);
            }
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
        if (timestamp == null) return null;
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
}

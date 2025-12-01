package com.example.modestyrent_app;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RentalAdapter extends RecyclerView.Adapter<RentalAdapter.RentalViewHolder> {

    private List<Rental> rentalList;
    private Map<String, String> productImageMap;
    private Map<String, String> ownerNameMap;
    private DatabaseReference chatsRef;

    public RentalAdapter(List<Rental> rentalList,
                         Map<String, String> productImageMap,
                         Map<String, String> ownerNameMap) {
        this.rentalList = rentalList;
        this.productImageMap = productImageMap;
        this.ownerNameMap = ownerNameMap;
        this.chatsRef = FirebaseDatabase.getInstance().getReference("chats");
    }

    @NonNull
    @Override
    public RentalViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_rental, parent, false);
        return new RentalViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RentalViewHolder holder, int position) {
        Rental rental = rentalList.get(position);
        holder.bind(rental);
    }

    @Override
    public int getItemCount() {
        return rentalList != null ? rentalList.size() : 0;
    }

    class RentalViewHolder extends RecyclerView.ViewHolder {

        private ImageView productImage;
        private TextView productName, bookingNumber, rentalDates, rentalAmount, depositAmount;
        private TextView statusIndicator, deliveryIndicator;
        private MaterialButton primaryAction, secondaryAction;

        public RentalViewHolder(@NonNull View itemView) {
            super(itemView);

            productImage = itemView.findViewById(R.id.productImage);
            productName = itemView.findViewById(R.id.productName);
            bookingNumber = itemView.findViewById(R.id.bookingNumber);
            rentalDates = itemView.findViewById(R.id.rentalDates);
            rentalAmount = itemView.findViewById(R.id.rentalAmount);
            depositAmount = itemView.findViewById(R.id.depositAmount);
            statusIndicator = itemView.findViewById(R.id.statusIndicator);
            deliveryIndicator = itemView.findViewById(R.id.deliveryIndicator);
            primaryAction = itemView.findViewById(R.id.primaryAction);
            secondaryAction = itemView.findViewById(R.id.secondaryAction);
        }

        public void bind(Rental rental) {
            // --- Product image from map ---
            String productId = rental.getProductId();
            if (productId != null && productImageMap != null && productImageMap.containsKey(productId)) {
                String imageUrl = productImageMap.get(productId);
                Glide.with(itemView.getContext())
                        .load(imageUrl)
                        .placeholder(R.drawable.ic_image_placeholder)
                        .into(productImage);
            } else {
                productImage.setImageResource(R.drawable.ic_image_placeholder);
            }

            // --- Basic info ---
            productName.setText(
                    rental.getProductName() != null ? rental.getProductName() : "Unknown Product"
            );

            bookingNumber.setText(
                    "#" + (rental.getBookingNumber() != null ? rental.getBookingNumber() : "N/A")
            );

            rentalDates.setText(formatDates(rental.getStartDate(), rental.getEndDate()));
            rentalAmount.setText(String.format(Locale.getDefault(), "RM %.2f", rental.getRentalAmount()));
            depositAmount.setText(String.format(Locale.getDefault(), "RM %.2f", rental.getDepositAmount()));

            // --- Status + delivery (you can still keep the nice status text) ---
            String statusText = getStatusDisplayText(
                    rental.getStatus(),
                    rental.getDeliveryStatus(),
                    rental.getDeliveryOption()
            );
            statusIndicator.setText(statusText);

            deliveryIndicator.setText(
                    rental.getDeliveryOption() != null ? rental.getDeliveryOption() : "Pickup"
            );

            // --- Buttons: fixed text, simple logic ---
            primaryAction.setVisibility(View.VISIBLE);
            secondaryAction.setVisibility(View.VISIBLE);

            primaryAction.setText("View Details");
            secondaryAction.setText("Contact Owner");

            // View Details = go to borrower rental details page
            primaryAction.setOnClickListener(v -> {
                Intent intent = new Intent(itemView.getContext(), activity_rentals_details_borrower.class);
                intent.putExtra("bookingId", rental.getBookingId());
                intent.putExtra("productId", rental.getProductId());
                intent.putExtra("ownerId", rental.getOwnerId());
                itemView.getContext().startActivity(intent);
            });

            // Contact Owner = open chat with owner
            secondaryAction.setOnClickListener(v -> openChatWithOwner(rental));
        }

        // ---------------- Helper methods ----------------

        private String formatDates(String startDate, String endDate) {
            if (startDate == null || endDate == null || startDate.isEmpty() || endDate.isEmpty()) {
                return "Dates not available";
            }

            try {
                long startMillis = Long.parseLong(startDate);
                long endMillis = Long.parseLong(endDate);

                SimpleDateFormat outputFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
                String startFormatted = outputFormat.format(new Date(startMillis));
                String endFormatted = outputFormat.format(new Date(endMillis));

                return startFormatted + " - " + endFormatted;
            } catch (NumberFormatException e) {
                // If they are already stored as readable strings
                return startDate + " - " + endDate;
            } catch (Exception e) {
                return "Invalid date format";
            }
        }

        private String getStatusDisplayText(String status, String deliveryStatus, String deliveryOption) {
            if (status == null) return "Unknown";

            if ("OnRent".equals(status)) return "On Rent";

            if ("Pickup".equals(deliveryOption) && "ReadyForPickup".equals(deliveryStatus)) {
                return "Ready for Pickup";
            }

            if ("Delivery".equals(deliveryOption) && "OutForDelivery".equals(deliveryStatus)) {
                return "Out for Delivery";
            }

            switch (status.toLowerCase(Locale.getDefault())) {
                case "confirmed": return "Confirmed";
                case "pending": return "Pending";
                case "preparingdelivery": return "Preparing Delivery";
                case "preparingpickup": return "Preparing Pickup";
                case "outfordelivery": return "Out for Delivery";
                case "readyforpickup": return "Ready for Pickup";
                case "onrent": return "On Rent";
                case "returnrequested": return "Return Requested";
                case "awaitinginspection": return "Awaiting Inspection";
                case "completed": return "Completed";
                case "dispute": return "Dispute";
                default: return status;
            }
        }

        // ---------------- Chat with owner ----------------

        private void openChatWithOwner(Rental rental) {
            String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null
                    ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                    : null;

            if (currentUserId == null) {
                android.widget.Toast.makeText(
                        itemView.getContext(),
                        "Please login to chat",
                        android.widget.Toast.LENGTH_SHORT
                ).show();
                return;
            }

            String ownerId = rental.getOwnerId();
            String productId = rental.getProductId();

            if (ownerId == null) {
                android.widget.Toast.makeText(
                        itemView.getContext(),
                        "Owner information not available",
                        android.widget.Toast.LENGTH_SHORT
                ).show();
                return;
            }

            String chatId = generateChatId(currentUserId, ownerId);

            String ownerName = ownerNameMap != null ? ownerNameMap.get(ownerId) : null;
            if (ownerName == null) ownerName = "Product Owner";

            checkAndCreateChat(chatId, currentUserId, ownerId, productId, ownerName, rental);
        }

        private void checkAndCreateChat(String chatId,
                                        String currentUserId,
                                        String ownerId,
                                        String productId,
                                        String ownerName,
                                        Rental rental) {

            chatsRef.child(chatId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (!snapshot.exists()) {
                        Map<String, Object> chatData = new HashMap<>();
                        chatData.put("chatId", chatId);
                        chatData.put("user1Id", currentUserId);
                        chatData.put("user2Id", ownerId);
                        chatData.put("user1Name", "You");
                        chatData.put("user2Name", ownerName);
                        chatData.put("productId", productId);
                        chatData.put("productName", rental.getProductName());
                        chatData.put("lastMessage", "");
                        chatData.put("lastMessageTime", System.currentTimeMillis());
                        chatData.put("createdAt", System.currentTimeMillis());

                        chatsRef.child(chatId).setValue(chatData)
                                .addOnSuccessListener(aVoid ->
                                        openChatActivity(chatId, ownerId, productId, ownerName, rental)
                                )
                                .addOnFailureListener(e ->
                                        android.widget.Toast.makeText(
                                                itemView.getContext(),
                                                "Failed to create chat",
                                                android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                );
                    } else {
                        openChatActivity(chatId, ownerId, productId, ownerName, rental);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    android.widget.Toast.makeText(
                            itemView.getContext(),
                            "Failed to check chat",
                            android.widget.Toast.LENGTH_SHORT
                    ).show();
                }
            });
        }

        private void openChatActivity(String chatId,
                                      String ownerId,
                                      String productId,
                                      String ownerName,
                                      Rental rental) {
            Intent chatIntent = new Intent(itemView.getContext(), activity_chat_owner.class);
            chatIntent.putExtra("chatId", chatId);
            chatIntent.putExtra("ownerId", ownerId);
            chatIntent.putExtra("productId", productId);
            chatIntent.putExtra("ownerName", ownerName);
            chatIntent.putExtra("bookingId", rental.getBookingId());
            itemView.getContext().startActivity(chatIntent);
        }

        private String generateChatId(String userId1, String userId2) {
            if (userId1 == null || userId2 == null) return "invalid_chat";
            return (userId1.compareTo(userId2) < 0)
                    ? userId1 + "_" + userId2
                    : userId2 + "_" + userId1;
        }
    }
}

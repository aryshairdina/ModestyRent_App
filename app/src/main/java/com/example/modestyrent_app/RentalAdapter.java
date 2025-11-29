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
    private DatabaseReference bookingsRef;

    public RentalAdapter(List<Rental> rentalList, Map<String, String> productImageMap, Map<String, String> ownerNameMap) {
        this.rentalList = rentalList;
        this.productImageMap = productImageMap;
        this.ownerNameMap = ownerNameMap;
        this.chatsRef = FirebaseDatabase.getInstance().getReference("chats");
        this.bookingsRef = FirebaseDatabase.getInstance().getReference("bookings");
    }

    @NonNull
    @Override
    public RentalViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rental, parent, false);
        return new RentalViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RentalViewHolder holder, int position) {
        Rental rental = rentalList.get(position);
        holder.bind(rental);
    }

    @Override
    public int getItemCount() {
        return rentalList.size();
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
            // Load product image from productImageMap
            String productId = rental.getProductId();
            if (productId != null && productImageMap.containsKey(productId)) {
                String imageUrl = productImageMap.get(productId);
                Glide.with(itemView.getContext())
                        .load(imageUrl)
                        .placeholder(R.drawable.ic_image_placeholder)
                        .into(productImage);
            } else {
                productImage.setImageResource(R.drawable.ic_image_placeholder);
            }

            // Set text values with null checks
            productName.setText(rental.getProductName() != null ? rental.getProductName() : "Unknown Product");
            bookingNumber.setText("#" + (rental.getBookingNumber() != null ? rental.getBookingNumber() : "N/A"));
            rentalDates.setText(formatDates(rental.getStartDate(), rental.getEndDate()));
            rentalAmount.setText(String.format("RM %.2f", rental.getRentalAmount()));
            depositAmount.setText(String.format("RM %.2f", rental.getDepositAmount()));

            // Set status and delivery indicators - FIXED: Show proper status
            String statusText = getStatusDisplayText(rental.getStatus(), rental.getDeliveryStatus(), rental.getDeliveryOption());
            statusIndicator.setText(statusText);
            deliveryIndicator.setText(rental.getDeliveryOption() != null ? rental.getDeliveryOption() : "Pickup");

            // Set action buttons based on status
            setupActionButtons(rental);
        }

        private String formatDates(String startDate, String endDate) {
            if (startDate == null || endDate == null || startDate.isEmpty() || endDate.isEmpty()) {
                return "Dates not available";
            }

            try {
                // Parse the dates as milliseconds (long values stored as strings)
                long startMillis = Long.parseLong(startDate);
                long endMillis = Long.parseLong(endDate);

                SimpleDateFormat outputFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

                String startFormatted = outputFormat.format(new Date(startMillis));
                String endFormatted = outputFormat.format(new Date(endMillis));

                return startFormatted + " - " + endFormatted;
            } catch (NumberFormatException e) {
                // If parsing as milliseconds fails, try to parse as string directly
                return startDate + " - " + endDate;
            } catch (Exception e) {
                return "Invalid date format";
            }
        }

        private String getStatusDisplayText(String status, String deliveryStatus, String deliveryOption) {
            if (status == null) return "Unknown";

            // If status is OnRent, always show "On Rent"
            if ("OnRent".equals(status)) {
                return "On Rent";
            }

            // For pickup flow, show "Ready for Pickup" when deliveryStatus is ReadyForPickup
            if ("Pickup".equals(deliveryOption) && "ReadyForPickup".equals(deliveryStatus)) {
                return "Ready for Pickup";
            }

            // For delivery flow, show "Out for Delivery" when deliveryStatus is OutForDelivery
            if ("Delivery".equals(deliveryOption) && "OutForDelivery".equals(deliveryStatus)) {
                return "Out for Delivery";
            }

            switch (status.toLowerCase()) {
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

        private void setupActionButtons(Rental rental) {
            String status = rental.getStatus() != null ? rental.getStatus().toLowerCase() : "unknown";
            String deliveryStatus = rental.getDeliveryStatus();
            String deliveryOption = rental.getDeliveryOption();

            // Check borrower-specific actions
            boolean showMarkAsPickedUp = "readyforpickup".equals(deliveryStatus) && "Pickup".equals(deliveryOption);
            boolean showMarkAsReceived = "outfordelivery".equals(deliveryStatus) && "Delivery".equals(deliveryOption);

            if (showMarkAsPickedUp) {
                primaryAction.setText("Mark as Picked Up");
                secondaryAction.setText("Contact Owner");
                primaryAction.setVisibility(View.VISIBLE);
                secondaryAction.setVisibility(View.VISIBLE);
            } else if (showMarkAsReceived) {
                primaryAction.setText("Mark as Received");
                secondaryAction.setText("Contact Owner");
                primaryAction.setVisibility(View.VISIBLE);
                secondaryAction.setVisibility(View.VISIBLE);
            } else {
                switch (status) {
                    case "pending":
                    case "confirmed":
                        primaryAction.setText("View Booking");
                        secondaryAction.setText("Contact Owner");
                        primaryAction.setVisibility(View.VISIBLE);
                        secondaryAction.setVisibility(View.VISIBLE);
                        break;
                    case "onrent":
                        primaryAction.setText("Start Return");
                        secondaryAction.setText("Contact Owner");
                        primaryAction.setVisibility(View.VISIBLE);
                        secondaryAction.setVisibility(View.VISIBLE);
                        break;
                    case "returnrequested":
                        primaryAction.setText("View Return");
                        secondaryAction.setText("Contact Owner");
                        primaryAction.setVisibility(View.VISIBLE);
                        secondaryAction.setVisibility(View.VISIBLE);
                        break;
                    case "awaitinginspection":
                        primaryAction.setText("Upload Proof");
                        secondaryAction.setText("Contact Owner");
                        primaryAction.setVisibility(View.VISIBLE);
                        secondaryAction.setVisibility(View.VISIBLE);
                        break;
                    case "completed":
                        primaryAction.setText("Leave Review");
                        secondaryAction.setText("Contact Owner");
                        primaryAction.setVisibility(View.VISIBLE);
                        secondaryAction.setVisibility(View.VISIBLE);
                        break;
                    case "dispute":
                        primaryAction.setText("View Dispute");
                        secondaryAction.setText("Contact Owner");
                        primaryAction.setVisibility(View.VISIBLE);
                        secondaryAction.setVisibility(View.VISIBLE);
                        break;
                    default:
                        primaryAction.setText("View Details");
                        secondaryAction.setText("Contact Owner");
                        primaryAction.setVisibility(View.VISIBLE);
                        secondaryAction.setVisibility(View.VISIBLE);
                        break;
                }
            }

            // Set click listeners for action buttons
            primaryAction.setOnClickListener(v -> handlePrimaryAction(rental));
            secondaryAction.setOnClickListener(v -> handleSecondaryAction(rental));
        }

        private void handlePrimaryAction(Rental rental) {
            String status = rental.getStatus() != null ? rental.getStatus().toLowerCase() : "unknown";
            String deliveryStatus = rental.getDeliveryStatus();
            String deliveryOption = rental.getDeliveryOption();

            // Check for borrower pickup/receive actions
            if ("readyforpickup".equals(deliveryStatus) && "Pickup".equals(deliveryOption)) {
                markAsPickedUp(rental);
                return;
            } else if ("outfordelivery".equals(deliveryStatus) && "Delivery".equals(deliveryOption)) {
                markAsReceived(rental);
                return;
            }

            // Default actions - Open borrower rental details
            Intent intent = new Intent(itemView.getContext(), activity_rentals_details_borrower.class);
            intent.putExtra("bookingId", rental.getBookingId());
            intent.putExtra("productId", rental.getProductId());
            intent.putExtra("ownerId", rental.getOwnerId());
            itemView.getContext().startActivity(intent);
        }

        private void handleSecondaryAction(Rental rental) {
            // Open chat with the product owner using the same logic as product details
            openChatWithOwner(rental);
        }

        private void openChatWithOwner(Rental rental) {
            String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                    FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

            if (currentUserId == null) {
                android.widget.Toast.makeText(itemView.getContext(), "Please login to chat", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }

            // Use the ownerId from the rental (this is the product owner)
            String ownerId = rental.getOwnerId();
            String productId = rental.getProductId();

            if (ownerId == null) {
                android.widget.Toast.makeText(itemView.getContext(), "Owner information not available", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }

            // Generate chat ID using the same format as your product details
            String chatId = generateChatId(currentUserId, ownerId);

            // Get owner name for the chat
            String ownerName = ownerNameMap.get(ownerId);
            if (ownerName == null) {
                ownerName = "Product Owner";
            }

            // Check if chat exists, if not create it
            checkAndCreateChat(chatId, currentUserId, ownerId, productId, ownerName, rental);
        }

        private void checkAndCreateChat(String chatId, String currentUserId, String ownerId, String productId, String ownerName, Rental rental) {
            chatsRef.child(chatId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (!snapshot.exists()) {
                        // Create new chat
                        Map<String, Object> chatData = new HashMap<>();
                        chatData.put("chatId", chatId);
                        chatData.put("user1Id", currentUserId);
                        chatData.put("user2Id", ownerId);
                        chatData.put("user1Name", "You"); // Borrower name
                        chatData.put("user2Name", ownerName);
                        chatData.put("productId", productId);
                        chatData.put("productName", rental.getProductName());
                        chatData.put("lastMessage", "");
                        chatData.put("lastMessageTime", System.currentTimeMillis());
                        chatData.put("createdAt", System.currentTimeMillis());

                        chatsRef.child(chatId).setValue(chatData)
                                .addOnSuccessListener(aVoid -> {
                                    openChatActivity(chatId, ownerId, productId, ownerName, rental);
                                })
                                .addOnFailureListener(e -> {
                                    android.widget.Toast.makeText(itemView.getContext(), "Failed to create chat", android.widget.Toast.LENGTH_SHORT).show();
                                });
                    } else {
                        openChatActivity(chatId, ownerId, productId, ownerName, rental);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    android.widget.Toast.makeText(itemView.getContext(), "Failed to check chat", android.widget.Toast.LENGTH_SHORT).show();
                }
            });
        }

        private void openChatActivity(String chatId, String ownerId, String productId, String ownerName, Rental rental) {
            Intent chatIntent = new Intent(itemView.getContext(), activity_chat_owner.class);
            chatIntent.putExtra("chatId", chatId);
            chatIntent.putExtra("ownerId", ownerId);
            chatIntent.putExtra("productId", productId);
            chatIntent.putExtra("ownerName", ownerName);
            chatIntent.putExtra("bookingId", rental.getBookingId());
            itemView.getContext().startActivity(chatIntent);
        }

        private void markAsPickedUp(Rental rental) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", "OnRent");
            updates.put("deliveryStatus", ""); // Clear delivery status when item is picked up
            updates.put("pickupTime", System.currentTimeMillis());

            bookingsRef.child(rental.getBookingId()).updateChildren(updates)
                    .addOnSuccessListener(aVoid -> {
                        android.widget.Toast.makeText(itemView.getContext(), "Item marked as picked up", android.widget.Toast.LENGTH_SHORT).show();
                        // Refresh the data by reloading
                        notifyDataSetChanged();
                    })
                    .addOnFailureListener(e -> {
                        android.widget.Toast.makeText(itemView.getContext(), "Failed to update status", android.widget.Toast.LENGTH_SHORT).show();
                    });
        }

        private void markAsReceived(Rental rental) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", "OnRent");
            updates.put("deliveryStatus", ""); // Clear delivery status when item is received
            updates.put("deliveryTime", System.currentTimeMillis());

            bookingsRef.child(rental.getBookingId()).updateChildren(updates)
                    .addOnSuccessListener(aVoid -> {
                        android.widget.Toast.makeText(itemView.getContext(), "Item marked as received", android.widget.Toast.LENGTH_SHORT).show();
                        // Refresh the data by reloading
                        notifyDataSetChanged();
                    })
                    .addOnFailureListener(e -> {
                        android.widget.Toast.makeText(itemView.getContext(), "Failed to update status", android.widget.Toast.LENGTH_SHORT).show();
                    });
        }

        private String generateChatId(String userId1, String userId2) {
            // Generate consistent chat ID regardless of parameter order
            if (userId1 == null || userId2 == null) {
                return "invalid_chat";
            }

            // Sort the user IDs to ensure consistent chat ID generation
            if (userId1.compareTo(userId2) < 0) {
                return userId1 + "_" + userId2;
            } else {
                return userId2 + "_" + userId1;
            }
        }
    }
}
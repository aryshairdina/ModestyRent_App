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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RentalAdapter extends RecyclerView.Adapter<RentalAdapter.RentalViewHolder> {

    private List<Rental> rentalList;
    private Map<String, String> productImageMap;
    private Map<String, String> ownerNameMap;

    public RentalAdapter(List<Rental> rentalList, Map<String, String> productImageMap, Map<String, String> ownerNameMap) {
        this.rentalList = rentalList;
        this.productImageMap = productImageMap;
        this.ownerNameMap = ownerNameMap;
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

            // Set status and delivery indicators
            statusIndicator.setText(getStatusDisplayText(rental.getStatus()));
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

        private String getStatusDisplayText(String status) {
            if (status == null) return "Unknown";

            switch (status.toLowerCase()) {
                case "confirmed": return "Confirmed";
                case "pending": return "Pending";
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

            switch (status) {
                case "pending":
                case "confirmed":
                    primaryAction.setText("View Booking");
                    secondaryAction.setText("Message Owner");
                    break;
                case "onrent":
                    primaryAction.setText("Start Return");
                    secondaryAction.setText("Message Owner");
                    break;
                case "returnrequested":
                    primaryAction.setText("View Return");
                    secondaryAction.setText("Message Owner");
                    break;
                case "awaitinginspection":
                    primaryAction.setText("View Inspection");
                    secondaryAction.setText("Add Evidence");
                    break;
                case "completed":
                    primaryAction.setText("Leave Review");
                    secondaryAction.setText("Message Owner");
                    break;
                case "dispute":
                    primaryAction.setText("View Dispute");
                    secondaryAction.setText("Message Owner");
                    break;
                default:
                    primaryAction.setText("View Details");
                    secondaryAction.setText("Message Owner");
                    break;
            }

            // Set click listeners for action buttons
            primaryAction.setOnClickListener(v -> handlePrimaryAction(rental));
            secondaryAction.setOnClickListener(v -> handleSecondaryAction(rental));
        }

        private void handlePrimaryAction(Rental rental) {
            String status = rental.getStatus() != null ? rental.getStatus().toLowerCase() : "unknown";

            switch (status) {
                case "pending":
                case "confirmed":
                    // Open booking details
                    Intent intent = new Intent(itemView.getContext(), activity_rentals_details.class);
                    intent.putExtra("bookingId", rental.getBookingId());
                    intent.putExtra("productId", rental.getProductId());
                    intent.putExtra("ownerId", rental.getOwnerId());
                    itemView.getContext().startActivity(intent);
                    break;
                case "onrent":
                    // Start return process
                    startReturnProcess(rental);
                    break;
                case "completed":
                    // Leave review
                    leaveReview(rental);
                    break;
                default:
                    // Default action - view booking details
                    Intent defaultIntent = new Intent(itemView.getContext(), activity_rentals_details.class);
                    defaultIntent.putExtra("bookingId", rental.getBookingId());
                    defaultIntent.putExtra("productId", rental.getProductId());
                    defaultIntent.putExtra("ownerId", rental.getOwnerId());
                    itemView.getContext().startActivity(defaultIntent);
                    break;
            }
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

            Intent chatIntent = new Intent(itemView.getContext(), activity_chat_owner.class);
            chatIntent.putExtra("chatId", chatId);
            chatIntent.putExtra("ownerId", ownerId);
            chatIntent.putExtra("productId", productId);
            chatIntent.putExtra("ownerName", ownerName);
            itemView.getContext().startActivity(chatIntent);
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

        private void startReturnProcess(Rental rental) {
            // Implement return process
            android.widget.Toast.makeText(itemView.getContext(), "Return process started", android.widget.Toast.LENGTH_SHORT).show();
            // This would typically open a return activity
        }

        private void leaveReview(Rental rental) {
            // Implement review system
            android.widget.Toast.makeText(itemView.getContext(), "Leave review feature", android.widget.Toast.LENGTH_SHORT).show();
            // This would typically open a review activity
        }
    }
}
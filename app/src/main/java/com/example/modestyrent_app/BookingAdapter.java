package com.example.modestyrent_app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import java.text.SimpleDateFormat;
import java.util.*;

public class BookingAdapter extends RecyclerView.Adapter<BookingAdapter.BookingViewHolder> {

    private List<Booking> bookingList;
    private Map<String, String> productImageMap;
    private Map<String, String> renterNameMap;
    private Map<String, String> renterPhoneMap;
    private BookingActionListener actionListener;

    public interface BookingActionListener {
        void onPrepareDelivery(Booking booking);
        void onPreparePickup(Booking booking);
        void onContactRenter(Booking booking);
        void onViewBookingDetails(Booking booking);
    }

    public BookingAdapter(List<Booking> bookingList, Map<String, String> productImageMap,
                          Map<String, String> renterNameMap, Map<String, String> renterPhoneMap,
                          BookingActionListener actionListener) {
        this.bookingList = bookingList;
        this.productImageMap = productImageMap;
        this.renterNameMap = renterNameMap;
        this.renterPhoneMap = renterPhoneMap;
        this.actionListener = actionListener;
    }

    @NonNull
    @Override
    public BookingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_booking_request, parent, false);
        return new BookingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BookingViewHolder holder, int position) {
        Booking booking = bookingList.get(position);
        holder.bind(booking);
    }

    @Override
    public int getItemCount() {
        return bookingList.size();
    }

    class BookingViewHolder extends RecyclerView.ViewHolder {
        private ImageView productImage;
        private TextView productName, renterName, bookingNumber, bookingDates;
        private TextView deliveryOption, rentalAmount;
        private com.google.android.material.chip.Chip statusChip;
        private MaterialButton primaryAction, secondaryAction;

        public BookingViewHolder(@NonNull View itemView) {
            super(itemView);

            productImage = itemView.findViewById(R.id.productImage);
            productName = itemView.findViewById(R.id.productName);
            renterName = itemView.findViewById(R.id.renterName);
            bookingNumber = itemView.findViewById(R.id.bookingNumber);
            bookingDates = itemView.findViewById(R.id.bookingDates);
            deliveryOption = itemView.findViewById(R.id.deliveryOption);
            rentalAmount = itemView.findViewById(R.id.rentalAmount);
            statusChip = itemView.findViewById(R.id.statusChip);
            primaryAction = itemView.findViewById(R.id.primaryAction);
            secondaryAction = itemView.findViewById(R.id.secondaryAction);
        }

        public void bind(Booking booking) {
            // Load product image
            String productId = booking.getProductId();
            if (productId != null && productImageMap.containsKey(productId)) {
                String imageUrl = productImageMap.get(productId);
                Glide.with(itemView.getContext())
                        .load(imageUrl)
                        .placeholder(R.drawable.ic_image_placeholder)
                        .into(productImage);
            } else {
                productImage.setImageResource(R.drawable.ic_image_placeholder);
            }

            // Set text values
            productName.setText(booking.getProductName() != null ? booking.getProductName() : "Unknown Product");

            // Get renter name from map or use fallback
            String renterNameText = booking.getRenterName();
            if (renterNameText == null && booking.getRenterId() != null) {
                renterNameText = renterNameMap.get(booking.getRenterId());
            }
            renterName.setText(renterNameText != null ? renterNameText : "Unknown Renter");

            bookingNumber.setText("#" + (booking.getBookingNumber() != null ? booking.getBookingNumber() : "N/A"));
            bookingDates.setText(formatDates(booking.getStartDate(), booking.getEndDate()));
            rentalAmount.setText(String.format("RM %.2f", booking.getRentalAmount()));

            // Delivery info
            deliveryOption.setText(booking.getDeliveryOption() != null ? booking.getDeliveryOption() : "Pickup");

            // Status chip - Show proper status based on both status and deliveryStatus
            String status = booking.getStatus();
            String deliveryStatus = booking.getDeliveryStatus();
            String deliveryOption = booking.getDeliveryOption();

            String displayStatus = getStatusDisplayText(status, deliveryStatus, deliveryOption);
            statusChip.setText(displayStatus);
            statusChip.setChipBackgroundColorResource(getStatusColor(status, deliveryStatus));

            // Setup action buttons - ONLY show Prepare Delivery/Pickup for confirmed status
            setupActionButtons(booking);

            // Card click - open booking details
            itemView.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onViewBookingDetails(booking);
                }
            });
        }

        private String formatDates(long startDate, long endDate) {
            try {
                SimpleDateFormat outputFormat = new SimpleDateFormat("dd MMM - dd MMM yyyy", Locale.getDefault());
                String startFormatted = outputFormat.format(new Date(startDate));

                // Extract just the end date part
                SimpleDateFormat endFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
                String endFormatted = endFormat.format(new Date(endDate));

                return startFormatted.split(" - ")[0] + " - " + endFormatted;
            } catch (Exception e) {
                return "Dates not available";
            }
        }

        private String getStatusDisplayText(String status, String deliveryStatus, String deliveryOption) {
            if (status == null) return "Unknown";

            // If status is OnRent, always show "On Rent"
            if ("OnRent".equals(status)) {
                return "On Rent";
            }

            // For delivery flow
            if ("Delivery".equals(deliveryOption)) {
                if ("OutForDelivery".equals(deliveryStatus)) {
                    return "Out for Delivery";
                } else if ("PreparingDelivery".equals(status)) {
                    return "Preparing Delivery";
                }
            }

            // For pickup flow
            if ("Pickup".equals(deliveryOption)) {
                if ("ReadyForPickup".equals(deliveryStatus)) {
                    return "Ready for Pickup";
                } else if ("PreparingPickup".equals(status)) {
                    return "Preparing Pickup";
                }
            }

            // Fallback to status-based display
            switch (status.toLowerCase()) {
                case "confirmed": return "Confirmed";
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

        private int getStatusColor(String status, String deliveryStatus) {
            // Use primary color for active states, secondary for completed
            if ("Completed".equals(status)) {
                return R.color.secondary;
            }

            // For OnRent status
            if ("OnRent".equals(status)) {
                return R.color.primary;
            }

            // For delivery flow active states
            if ("OutForDelivery".equals(deliveryStatus) || "PreparingDelivery".equals(status)) {
                return R.color.primary;
            }

            // For pickup flow active states
            if ("ReadyForPickup".equals(deliveryStatus) || "PreparingPickup".equals(status)) {
                return R.color.primary;
            }

            // Default to primary for active statuses
            if (status != null && !"completed".equals(status.toLowerCase())) {
                return R.color.primary;
            }

            return R.color.secondary;
        }

        private void setupActionButtons(Booking booking) {
            String status = booking.getStatus() != null ? booking.getStatus().toLowerCase() : "unknown";
            String deliveryOption = booking.getDeliveryOption();

            // Reset buttons
            primaryAction.setVisibility(View.GONE);
            secondaryAction.setVisibility(View.VISIBLE); // Contact button always visible

            // ONLY show Prepare Delivery/Pickup buttons for confirmed status
            boolean showPrepareDelivery = "confirmed".equals(status) && "Delivery".equals(deliveryOption);
            boolean showPreparePickup = "confirmed".equals(status) && "Pickup".equals(deliveryOption);

            if (showPrepareDelivery) {
                primaryAction.setText("Prepare Delivery");
                primaryAction.setVisibility(View.VISIBLE);
                primaryAction.setOnClickListener(v -> {
                    if (actionListener != null) {
                        actionListener.onPrepareDelivery(booking);
                    }
                });
            } else if (showPreparePickup) {
                primaryAction.setText("Prepare Pickup");
                primaryAction.setVisibility(View.VISIBLE);
                primaryAction.setOnClickListener(v -> {
                    if (actionListener != null) {
                        actionListener.onPreparePickup(booking);
                    }
                });
            } else {
                // For all other statuses, NO primary action button
                primaryAction.setVisibility(View.GONE);
            }

            // Secondary action (Contact) is always available
            secondaryAction.setText("Contact");
            secondaryAction.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onContactRenter(booking);
                }
            });
        }
    }
}
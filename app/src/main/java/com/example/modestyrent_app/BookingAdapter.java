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
        void onConfirmReadyForPickup(Booking booking);
        void onContactRenter(Booking booking);
        void onInspectReturn(Booking booking);
        void onViewBookingDetails(Booking booking);
        void onAwaitReturn(Booking booking);
        void onArrangeReturn(Booking booking);
        void onLeaveReview(Booking booking);
        void onViewTransaction(Booking booking);
        void onViewDispute(Booking booking);
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

            // Status chip - show delivery status for pickup flow
            String status = booking.getStatus();
            String deliveryStatus = booking.getDeliveryStatus();

            if ("Pickup".equals(booking.getDeliveryOption()) && "ReadyForPickup".equals(deliveryStatus)) {
                statusChip.setText("Ready for Pickup");
            } else {
                statusChip.setText(getStatusDisplayText(status));
            }
            statusChip.setChipBackgroundColorResource(getStatusColor(status));

            // Setup action buttons based on status
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

        private String getStatusDisplayText(String status) {
            if (status == null) return "Unknown";

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

        private int getStatusColor(String status) {
            if (status == null) return R.color.secondary;

            switch (status.toLowerCase()) {
                case "confirmed": return R.color.primary;
                case "preparingdelivery": return R.color.primary;
                case "preparingpickup": return R.color.primary;
                case "outfordelivery": return R.color.primary;
                case "readyforpickup": return R.color.primary;
                case "onrent": return R.color.primary;
                case "returnrequested": return R.color.primary;
                case "awaitinginspection": return R.color.primary;
                case "completed": return R.color.secondary;
                case "dispute": return R.color.primary;
                default: return R.color.secondary;
            }
        }

        private void setupActionButtons(Booking booking) {
            String status = booking.getStatus() != null ? booking.getStatus().toLowerCase() : "unknown";
            String deliveryOption = booking.getDeliveryOption();
            String deliveryStatus = booking.getDeliveryStatus();

            // Reset buttons
            primaryAction.setVisibility(View.VISIBLE);
            secondaryAction.setVisibility(View.VISIBLE);

            // Check if button should be shown based on current status
            boolean showPrepareDelivery = "confirmed".equals(status) && "Delivery".equals(deliveryOption);
            boolean showPreparePickup = "confirmed".equals(status) && "Pickup".equals(deliveryOption);
            boolean showReadyForPickup = "preparingpickup".equals(status) && "Pickup".equals(deliveryOption);
            boolean showMarkOutForDelivery = "preparingdelivery".equals(status) && "Delivery".equals(deliveryOption);

            if (showPrepareDelivery) {
                primaryAction.setText("Prepare Delivery");
                primaryAction.setOnClickListener(v -> {
                    if (actionListener != null) {
                        actionListener.onPrepareDelivery(booking);
                    }
                });
            } else if (showPreparePickup) {
                primaryAction.setText("Prepare Pickup");
                primaryAction.setOnClickListener(v -> {
                    if (actionListener != null) {
                        actionListener.onPreparePickup(booking);
                    }
                });
            } else if (showReadyForPickup) {
                primaryAction.setText("Ready for Pickup");
                primaryAction.setOnClickListener(v -> {
                    if (actionListener != null) {
                        actionListener.onConfirmReadyForPickup(booking);
                    }
                });
            } else if (showMarkOutForDelivery) {
                primaryAction.setText("Mark Out for Delivery");
                primaryAction.setOnClickListener(v -> {
                    // This will be handled in the details activity
                    if (actionListener != null) {
                        actionListener.onViewBookingDetails(booking);
                    }
                });
            } else {
                switch (status) {
                    case "onrent":
                        primaryAction.setText("Await Return");
                        primaryAction.setOnClickListener(v -> {
                            if (actionListener != null) {
                                actionListener.onAwaitReturn(booking);
                            }
                        });
                        break;

                    case "returnrequested":
                        primaryAction.setText("Arrange Return");
                        primaryAction.setOnClickListener(v -> {
                            if (actionListener != null) {
                                actionListener.onArrangeReturn(booking);
                            }
                        });
                        break;

                    case "awaitinginspection":
                        primaryAction.setText("Inspect Return");
                        primaryAction.setOnClickListener(v -> {
                            if (actionListener != null) {
                                actionListener.onInspectReturn(booking);
                            }
                        });
                        break;

                    case "completed":
                        primaryAction.setText("Leave Review");
                        primaryAction.setOnClickListener(v -> {
                            if (actionListener != null) {
                                actionListener.onLeaveReview(booking);
                            }
                        });
                        break;

                    case "dispute":
                        primaryAction.setText("View Dispute");
                        primaryAction.setOnClickListener(v -> {
                            if (actionListener != null) {
                                actionListener.onViewDispute(booking);
                            }
                        });
                        break;

                    default:
                        primaryAction.setVisibility(View.GONE);
                        break;
                }
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
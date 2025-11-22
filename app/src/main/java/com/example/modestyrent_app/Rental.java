package com.example.modestyrent_app;

public class Rental {
    private String bookingId;
    private String bookingNumber;
    private String productId;
    private String productName;
    private String productImage;
    private String ownerId;
    private String renterName;
    private String startDate;
    private String endDate;
    private double rentalAmount;
    private double depositAmount;
    private double totalAmount;
    private String deliveryOption;
    private String deliveryStatus;
    private String status;
    private String paymentStatus;

    // Default constructor
    public Rental() {}

    // Getters and setters
    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public String getBookingNumber() { return bookingNumber; }
    public void setBookingNumber(String bookingNumber) { this.bookingNumber = bookingNumber; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getProductImage() { return productImage; }
    public void setProductImage(String productImage) { this.productImage = productImage; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getRenterName() { return renterName; }
    public void setRenterName(String renterName) { this.renterName = renterName; }

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }

    public double getRentalAmount() { return rentalAmount; }
    public void setRentalAmount(double rentalAmount) { this.rentalAmount = rentalAmount; }

    public double getDepositAmount() { return depositAmount; }
    public void setDepositAmount(double depositAmount) { this.depositAmount = depositAmount; }

    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }

    public String getDeliveryOption() { return deliveryOption; }
    public void setDeliveryOption(String deliveryOption) { this.deliveryOption = deliveryOption; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }


    // Add deliveryStatus field and getter
    public String getDeliveryStatus() {return deliveryStatus;}

    public void setDeliveryStatus(String deliveryStatus) {this.deliveryStatus = deliveryStatus;}
}
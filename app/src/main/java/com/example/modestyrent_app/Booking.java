package com.example.modestyrent_app;

public class Booking {
    private String bookingId, bookingNumber, productId, ownerId, renterId, productName, renterName, renterPhone;
    private String deliveryAddress, deliveryOption, paymentMethod, status, paymentStatus, deliveryStatus;
    private long startDate, endDate, bookingDate, paymentDate;
    private int rentalDays;
    private double unitPrice, rentalAmount, depositAmount, totalAmount;

    // Timestamps for proper flow tracking
    private Long preparationTime;
    private Long deliveryLeaveTime;
    private Long readyForPickupTime;
    private Long pickupTime;
    private Long deliveryTime;
    private Long returnRequestTime;
    private Long returnTime;
    private Long inspectionTime;
    private Long completionTime;
    private Boolean depositReturned;
    private Long depositReturnDate;

    // Default constructor
    public Booking() {}

    // Getters and setters for all fields
    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public String getBookingNumber() { return bookingNumber; }
    public void setBookingNumber(String bookingNumber) { this.bookingNumber = bookingNumber; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getRenterId() { return renterId; }
    public void setRenterId(String renterId) { this.renterId = renterId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getRenterName() { return renterName; }
    public void setRenterName(String renterName) { this.renterName = renterName; }

    public String getRenterPhone() { return renterPhone; }
    public void setRenterPhone(String renterPhone) { this.renterPhone = renterPhone; }

    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }

    public String getDeliveryOption() { return deliveryOption; }
    public void setDeliveryOption(String deliveryOption) { this.deliveryOption = deliveryOption; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getDeliveryStatus() { return deliveryStatus; }
    public void setDeliveryStatus(String deliveryStatus) { this.deliveryStatus = deliveryStatus; }

    public long getStartDate() { return startDate; }
    public void setStartDate(long startDate) { this.startDate = startDate; }

    public long getEndDate() { return endDate; }
    public void setEndDate(long endDate) { this.endDate = endDate; }

    public long getBookingDate() { return bookingDate; }
    public void setBookingDate(long bookingDate) { this.bookingDate = bookingDate; }

    public long getPaymentDate() { return paymentDate; }
    public void setPaymentDate(long paymentDate) { this.paymentDate = paymentDate; }

    public int getRentalDays() { return rentalDays; }
    public void setRentalDays(int rentalDays) { this.rentalDays = rentalDays; }

    public double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }

    public double getRentalAmount() { return rentalAmount; }
    public void setRentalAmount(double rentalAmount) { this.rentalAmount = rentalAmount; }

    public double getDepositAmount() { return depositAmount; }
    public void setDepositAmount(double depositAmount) { this.depositAmount = depositAmount; }

    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }

    public Long getPreparationTime() { return preparationTime; }
    public void setPreparationTime(Long preparationTime) { this.preparationTime = preparationTime; }

    public Long getDeliveryLeaveTime() { return deliveryLeaveTime; }
    public void setDeliveryLeaveTime(Long deliveryLeaveTime) { this.deliveryLeaveTime = deliveryLeaveTime; }

    public Long getReadyForPickupTime() { return readyForPickupTime; }
    public void setReadyForPickupTime(Long readyForPickupTime) { this.readyForPickupTime = readyForPickupTime; }

    public Long getPickupTime() { return pickupTime; }
    public void setPickupTime(Long pickupTime) { this.pickupTime = pickupTime; }

    public Long getDeliveryTime() { return deliveryTime; }
    public void setDeliveryTime(Long deliveryTime) { this.deliveryTime = deliveryTime; }

    public Long getReturnRequestTime() { return returnRequestTime; }
    public void setReturnRequestTime(Long returnRequestTime) { this.returnRequestTime = returnRequestTime; }

    public Long getReturnTime() { return returnTime; }
    public void setReturnTime(Long returnTime) { this.returnTime = returnTime; }

    public Long getInspectionTime() { return inspectionTime; }
    public void setInspectionTime(Long inspectionTime) { this.inspectionTime = inspectionTime; }

    public Long getCompletionTime() { return completionTime; }
    public void setCompletionTime(Long completionTime) { this.completionTime = completionTime; }

    public Boolean getDepositReturned() { return depositReturned; }
    public void setDepositReturned(Boolean depositReturned) { this.depositReturned = depositReturned; }

    public Long getDepositReturnDate() { return depositReturnDate; }
    public void setDepositReturnDate(Long depositReturnDate) { this.depositReturnDate = depositReturnDate; }
}
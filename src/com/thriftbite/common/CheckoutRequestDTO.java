package com.thriftbite.common;

public class CheckoutRequestDTO implements java.io.Serializable {
    private String userId;
    private String pickupLocation;

    public CheckoutRequestDTO() {}

    public CheckoutRequestDTO(String userId, String pickupLocation) {
        this.userId = userId;
        this.pickupLocation = pickupLocation;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getPickupLocation() { return pickupLocation; }
    public void setPickupLocation(String pickupLocation) { this.pickupLocation = pickupLocation; }
}

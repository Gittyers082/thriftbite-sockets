package com.thriftbite.common;

import java.io.Serializable;

public class ItemDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private int quantity;
    private double price;
    private ItemStatus status;
    private String ownerId;
    private boolean isExpiringSoon;
    private String expiryDate;
    private String pickupLocation;
    private double originalPrice;
    private String buyerName;

    public ItemDTO() {}

    public ItemDTO(String id, String name, int quantity, double price, ItemStatus status, String ownerId, boolean isExpiringSoon, String expiryDate, String pickupLocation, double originalPrice, String buyerName) {
        this.id = id;
        this.name = name;
        this.quantity = quantity;
        this.price = price;
        this.status = status;
        this.ownerId = ownerId;
        this.isExpiringSoon = isExpiringSoon;
        this.expiryDate = expiryDate;
        this.pickupLocation = pickupLocation;
        this.originalPrice = originalPrice;
        this.buyerName = buyerName;
    }


    public String getId() { return id; }

    public void setId(String id) { this.id = id; }

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public int getQuantity() { return quantity; }

    public void setQuantity(int quantity) { this.quantity = quantity; }

    public double getPrice() { return price; }

    public void setPrice(double price) { this.price = price; }

    public ItemStatus getStatus() { return status; }

    public void setStatus(ItemStatus status) { this.status = status; }

    public String getOwnerId() { return ownerId; }

    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public boolean isExpiringSoon() { return isExpiringSoon; }

    public void setExpiringSoon(boolean expiringSoon) { isExpiringSoon = expiringSoon; }

    public String getExpiryDate() { return expiryDate; }

    public void setExpiryDate(String expiryDate) { this.expiryDate = expiryDate; }

    public String getPickupLocation() { return pickupLocation; }

    public void setPickupLocation(String pickupLocation) { this.pickupLocation = pickupLocation; }

    public double getOriginalPrice() { return originalPrice; }

    public void setOriginalPrice(double originalPrice) { this.originalPrice = originalPrice; }

    public String getBuyerName() { return buyerName; }

    public void setBuyerName(String buyerName) { this.buyerName = buyerName; }
}


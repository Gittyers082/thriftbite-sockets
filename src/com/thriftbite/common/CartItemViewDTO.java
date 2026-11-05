package com.thriftbite.common;

import java.io.Serializable;

/**
 * Cart entry with item details for display (used in GET_CART response).
 */
public class CartItemViewDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String itemId;
    private String itemName;
    private int quantity;
    private double price;
    private String expiryDate;

    public CartItemViewDTO() {}

    public CartItemViewDTO(String itemId, String itemName, int quantity, double price, String expiryDate) {
        this.itemId = itemId;
        this.itemName = itemName;
        this.quantity = quantity;
        this.price = price;
        this.expiryDate = expiryDate;
    }

    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public String getExpiryDate() { return expiryDate; }
    public void setExpiryDate(String expiryDate) { this.expiryDate = expiryDate; }
}

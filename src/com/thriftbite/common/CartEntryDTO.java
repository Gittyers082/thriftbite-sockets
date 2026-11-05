package com.thriftbite.common;

import java.io.Serializable;

/**
 * Represents a single entry in a user's cart (itemId + quantity).
 */
public class CartEntryDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String userId;
    private String itemId;
    private int quantity;

    public CartEntryDTO() {}

    public CartEntryDTO(String userId, String itemId, int quantity) {
        this.userId = userId;
        this.itemId = itemId;
        this.quantity = quantity;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}

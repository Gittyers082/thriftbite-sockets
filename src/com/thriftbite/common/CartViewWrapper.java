package com.thriftbite.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for serializing a list of CartItemViewDTO in responses.
 */
public class CartViewWrapper implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<CartItemViewDTO> items = new ArrayList<>();

    public List<CartItemViewDTO> getItems() { return items; }
    public void setItems(List<CartItemViewDTO> items) { this.items = items != null ? items : new ArrayList<>(); }
}

package com.thriftbite.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for serializing a list of ItemDTO in responses (e.g. expiry notifications).
 */
public class ItemListWrapper implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<ItemDTO> items = new ArrayList<>();

    public List<ItemDTO> getItems() { return items; }
    public void setItems(List<ItemDTO> items) { this.items = items != null ? items : new ArrayList<>(); }
}

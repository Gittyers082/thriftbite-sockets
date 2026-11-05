package com.thriftbite.MVC.controller;

import com.thriftbite.MVC.model.ConsumerModel;
import com.thriftbite.common.CartItemViewDTO;
import com.thriftbite.common.ItemDTO;
import com.thriftbite.common.Response;

import java.util.List;

public class ConsumerController {
    private final ConsumerModel model;
    private final ItemController itemController;

    public ConsumerController(ConsumerModel model) {
        this.model = model;
        this.itemController = new ItemController(model.getClientModel());
    }

    public List<ItemDTO> loadItems() {
        return itemController.loadItems();
    }

    public boolean addToCart(String itemId, int qty) {
        return itemController.addToCart(itemId, qty);
    }

    public List<CartItemViewDTO> getCartItems() {
        return itemController.getCartItems();
    }

    public boolean removeFromCart(String itemId) {
        return itemController.removeFromCart(itemId);
    }

    public List<ItemDTO> getMyOrders() {
        return itemController.getMyOrders();
    }

    public Response checkoutCart(String pickupLocation) {
        return itemController.checkoutCart(pickupLocation);
    }
}
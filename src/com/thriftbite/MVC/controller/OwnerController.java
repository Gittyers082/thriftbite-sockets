package com.thriftbite.MVC.controller;

import com.thriftbite.MVC.model.OwnerModel;
import com.thriftbite.common.ItemDTO;
import com.thriftbite.common.Response;

import java.util.List;

public class OwnerController {
    private final OwnerModel model;
    private final ItemController itemController;

    public OwnerController(OwnerModel model) {
        this.model = model;
        this.itemController = new ItemController(model.getClientModel());
    }

    public List<ItemDTO> loadItems() {
        return itemController.loadItems();
    }

    public List<ItemDTO> searchItems(String keyword) {
        return itemController.searchItems(keyword);
    }

    public List<ItemDTO> getExpiryNotificationsList() {
        Response res = model.getExpiryNotifications();
        return model.getExpiryNotificationItems(res);
    }
}
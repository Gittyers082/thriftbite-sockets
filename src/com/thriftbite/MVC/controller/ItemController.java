package com.thriftbite.MVC.controller;

import com.thriftbite.common.CartItemViewDTO;
import com.thriftbite.common.ItemDTO;
import com.thriftbite.common.Response;
import com.thriftbite.MVC.model.ClientModel;
import com.thriftbite.MVC.model.ConsumerModel;
import com.thriftbite.MVC.model.OwnerModel;
import com.thriftbite.common.CheckoutRequestDTO;
import java.util.List;

public class ItemController {

    private final ClientModel baseModel;
    private final ConsumerModel consumerModel;
    private final OwnerModel ownerModel;

    public ItemController(ClientModel model) {
        this.baseModel = model;
        // --- WE INJECT THE NEW MODELS HERE ---
        this.consumerModel = new ConsumerModel(model);
        this.ownerModel = new OwnerModel(model);
    }

    public List<ItemDTO> loadItems() {
        Response res = baseModel.getAllItems();
        if (res.isSuccess() && res.getPayload() != null) {
            return com.thriftbite.common.XmlUtils.fromXmlList(res.getPayload(), ItemDTO.class);
        }
        return null;
    }

    /** Load items for a specific food business (owner) for admin filtering */
    public List<ItemDTO> loadItemsByOwner(String ownerId) {
        if (ownerId == null || ownerId.trim().isEmpty()) return loadItems();
        Response res = consumerModel.searchItemsByOwner(ownerId);
        if (res != null && res.isSuccess() && res.getPayload() != null) {
            return com.thriftbite.common.XmlUtils.fromXmlList(res.getPayload(), ItemDTO.class);
        }
        return null;
    }

    public boolean addItem(ItemDTO item) {
        Response res = ownerModel.addItem(item);
        return res.isSuccess();
    }

    public boolean claimItem(String itemId) {
        Response res = consumerModel.claimItem(itemId);
        return res != null && res.isSuccess();
    }

    public Response claimItemWithResponse(String itemId) {
        return consumerModel.claimItem(itemId);
    }

    public boolean updateItem(ItemDTO item) {
        Response res = ownerModel.updateItem(item);
        return res.isSuccess();
    }

    public boolean deleteItem(String itemId) {
        Response res = ownerModel.deleteItem(itemId);
        return res.isSuccess();
    }

    public List<ItemDTO> searchItems(String keyword) {
        Response res = consumerModel.searchItemsByName(keyword);
        if (res.isSuccess() && res.getPayload() != null) {
            return com.thriftbite.common.XmlUtils.fromXmlList(res.getPayload(), ItemDTO.class);
        }
        return null;
    }

    public boolean addToCart(String itemId, int quantity) {
        Response res = consumerModel.addToCart(itemId, quantity);
        return res != null && res.isSuccess();
    }

    public boolean removeFromCart(String itemId) {
        Response res = consumerModel.removeFromCart(itemId);
        return res != null && res.isSuccess();
    }

    public List<CartItemViewDTO> getCartItems() {
        Response res = consumerModel.getCart();
        return res != null ? consumerModel.getCartItems(res) : java.util.Collections.emptyList();
    }

    public List<ItemDTO> getMyOrders() {
        Response res = consumerModel.getMyOrders();
        return res != null ? consumerModel.getMyOrderItems(res) : java.util.Collections.emptyList();
    }

    public Response checkoutCart(String pickupLocation) {
        String safePickup = (pickupLocation == null) ? "" : pickupLocation.trim();

        CheckoutRequestDTO dto = new CheckoutRequestDTO(
                baseModel.getCurrentUser().getUsername(),
                safePickup
        );

        return consumerModel.checkoutCart(dto);
    }
}
package com.thriftbite.MVC.model;

import com.thriftbite.common.*;

import java.util.List;

public class ConsumerModel {
    private final ClientModel client;

    public ConsumerModel(ClientModel client) {
        this.client = client;
    }

    public ClientModel getClientModel() { return client; }
    public UserDTO getCurrentUser() { return client.getCurrentUser(); }
    public boolean isConnected() { return client.isConnected(); }
    public void logout() { client.logout(); }

    public Response getAllItems() {
        return client.getAllItems();
    }

    public Response searchItemsByName(String searchTerm) {
        return client.sendRequest(new Request("SEARCH_ITEMS_BY_NAME", searchTerm));
    }

    public Response advancedSearch(String name, double minPrice, double maxPrice, ItemStatus status, boolean expiringSoon) {
        StringBuilder criteria = new StringBuilder();
        criteria.append("<searchCriteria>");
        criteria.append("<name>").append(name != null ? name : "").append("</name>");
        criteria.append("<minPrice>").append(minPrice).append("</minPrice>");
        criteria.append("<maxPrice>").append(maxPrice).append("</maxPrice>");
        criteria.append("<status>").append(status != null ? status.name() : "").append("</status>");
        criteria.append("<expiringSoon>").append(expiringSoon).append("</expiringSoon>");
        criteria.append("</searchCriteria>");

        return client.sendRequest(new Request("ADVANCED_SEARCH", criteria.toString()));
    }

    public Response addToCart(String itemId, int quantity) {
        if (getCurrentUser() == null) return new Response(false, "Not logged in", null);
        CartEntryDTO entry = new CartEntryDTO(getCurrentUser().getUsername(), itemId, quantity);
        return client.sendRequest(new Request("ADD_TO_CART", XmlUtils.toXml(entry)));
    }

    public Response removeFromCart(String itemId) {
        if (getCurrentUser() == null) return new Response(false, "Not logged in", null);
        CartEntryDTO entry = new CartEntryDTO(getCurrentUser().getUsername(), itemId, 0);
        return client.sendRequest(new Request("REMOVE_FROM_CART", XmlUtils.toXml(entry)));
    }

    public Response getCart() {
        if (getCurrentUser() == null) return new Response(false, "Not logged in", null);
        return client.sendRequest(new Request("GET_CART", getCurrentUser().getUsername()));
    }

    public List<CartItemViewDTO> getCartItems(Response cartResponse) {
        if (cartResponse == null || !cartResponse.isSuccess() || cartResponse.getPayload() == null) {
            return java.util.Collections.emptyList();
        }
        CartViewWrapper w = XmlUtils.fromXml(cartResponse.getPayload(), CartViewWrapper.class);
        return w != null ? w.getItems() : java.util.Collections.emptyList();
    }

    public Response checkoutCart(CheckoutRequestDTO dto) {
        return client.sendRequest(new Request("CHECKOUT_CART", XmlUtils.toXml(dto)));
    }

    public Response getMyOrders() {
        if (getCurrentUser() == null) return new Response(false, "Not logged in", null);
        return client.sendRequest(new Request("GET_MY_ORDERS", getCurrentUser().getUsername()));
    }

    public List<ItemDTO> getMyOrderItems(Response orderResponse) {
        if (orderResponse == null || !orderResponse.isSuccess() || orderResponse.getPayload() == null) {
            return java.util.Collections.emptyList();
        }
        return XmlUtils.fromXmlList(orderResponse.getPayload(), ItemDTO.class);
    }
    public Response claimItem(String itemId) {
        if (getCurrentUser() == null) {
            return new Response(false, "Not logged in", null);
        }

        CartEntryDTO claimEntry = new CartEntryDTO(getCurrentUser().getUsername(), itemId, 0);
        return client.sendRequest(new Request("CLAIM_ITEM", XmlUtils.toXml(claimEntry)));
    }
    public Response searchItemsByOwner(String ownerId) {
        return client.sendRequest(new Request("SEARCH_ITEMS_BY_OWNER", ownerId));
    }
}
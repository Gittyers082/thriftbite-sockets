package com.thriftbite.MVC.model;

import com.thriftbite.common.ItemDTO;
import com.thriftbite.common.ItemStatus;
import com.thriftbite.common.Request;
import com.thriftbite.common.Response;
import com.thriftbite.common.UserDTO;
import com.thriftbite.common.XmlUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class OwnerModel {
    private final ClientModel client;

    public OwnerModel(ClientModel client) {
        this.client = client;
    }

    public ClientModel getClientModel() { return client; }
    public UserDTO getCurrentUser() { return client.getCurrentUser(); }
    public boolean isConnected() { return client.isConnected(); }
    public void logout() { client.logout(); }

    public Response addItem(ItemDTO item) {
        if (getCurrentUser() == null) return new Response(false, "Not logged in", null);
        item.setOwnerId(getCurrentUser().getUsername());
        return client.sendRequest(new Request("ADD_ITEM", XmlUtils.toXml(item)));
    }

    public Response updateItem(ItemDTO item) {
        return client.sendRequest(new Request("UPDATE_ITEM", XmlUtils.toXml(item)));
    }

    public Response deleteItem(String itemId) {
        return client.sendRequest(new Request("DELETE_ITEM", itemId));
    }

    public Response getMyItems() {
        if (getCurrentUser() == null) return new Response(false, "Not logged in", null);
        return client.sendRequest(new Request("SEARCH_ITEMS_BY_OWNER", getCurrentUser().getUsername()));
    }

    public Response getMyReservedItems() {
        Response res = getMyItems();
        if (res == null || !res.isSuccess() || res.getPayload() == null) return res;

        List<ItemDTO> all = XmlUtils.fromXmlList(res.getPayload(), ItemDTO.class);
        if (all == null) return res;

        List<ItemDTO> reserved = all.stream()
                .filter(item -> item.getStatus() == ItemStatus.RESERVED || item.getStatus() == ItemStatus.PAID)
                .collect(Collectors.toList());

        return new Response(true, res.getMessage(), XmlUtils.toXml(reserved));
    }

    public Response confirmHandover(String itemId) {
        return client.sendRequest(new Request("CONFIRM_HANDOVER", itemId));
    }

    public Response searchItemsByName(String searchTerm) {
        return client.sendRequest(new Request("SEARCH_ITEMS_BY_NAME", searchTerm));
    }

    public Response searchItemsByStatus(ItemStatus status) {
        return client.sendRequest(new Request("SEARCH_ITEMS_BY_STATUS", status.name()));
    }

    public Response getExpiryNotifications() {
        if (getCurrentUser() == null) return new Response(false, "Not logged in", null);
        return client.sendRequest(new Request("GET_EXPIRY_NOTIFICATIONS", getCurrentUser().getUsername()));
    }

    public List<ItemDTO> getExpiryNotificationItems(Response notificationResponse) {
        if (notificationResponse == null || !notificationResponse.isSuccess() || notificationResponse.getPayload() == null) {
            return Collections.emptyList();
        }
        com.thriftbite.common.ItemListWrapper w = XmlUtils.fromXml(notificationResponse.getPayload(), com.thriftbite.common.ItemListWrapper.class);
        return w != null ? w.getItems() : Collections.emptyList();
    }
}
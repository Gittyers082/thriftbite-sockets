package com.thriftbite.MVC.model;

import com.thriftbite.common.ItemStatus;
import com.thriftbite.common.ItemDTO;
import com.thriftbite.common.Request;
import com.thriftbite.common.Response;
import com.thriftbite.common.TransactionDTO;
import com.thriftbite.common.UserDTO;
import com.thriftbite.common.XmlUtils;

import java.util.ArrayList;
import java.util.List;

public class AdminModel {
    private final ClientModel client;

    public AdminModel(ClientModel client) {
        this.client = client;
    }

    public Response deleteUser(String username) {
        return client.sendRequest(new Request("DELETE_USER", username));
    }

    public boolean approveUser(String username) {
        Response res = client.sendRequest(new Request("APPROVE_USER", username));
        return res.isSuccess();
    }

    @SuppressWarnings("unchecked")
    public List<UserDTO> getAllUsers() {
        Response res = client.getAllUsers();
        if (res.isSuccess() && res.getPayload() != null) {
            try { return (List<UserDTO>) XmlUtils.fromXml(res.getPayload(), ArrayList.class); }
            catch (Exception e) { e.printStackTrace(); }
        }
        return new ArrayList<>();
    }

    public Response deleteItem(String itemId) {
        return client.sendRequest(new Request("DELETE_ITEM", itemId));
    }

    @SuppressWarnings("unchecked")
    public List<TransactionDTO> getAllTransactions() {
        Response res = client.sendRequest(new Request("GET_ALL_TRANSACTIONS", ""));
        if (res.isSuccess() && res.getPayload() != null) {
            try { return (List<TransactionDTO>) XmlUtils.fromXml(res.getPayload(), ArrayList.class); }
            catch (Exception e) { e.printStackTrace(); }
        }
        return new ArrayList<>();
    }

    public List<String> getActiveUsers() {
        Response res = client.sendRequest(new Request("GET_ACTIVE_USERS", ""));
        List<String> activeList = new ArrayList<>();
        if (res.isSuccess() && res.getPayload() != null && !res.getPayload().isEmpty()) {
            String[] users = res.getPayload().split(",");
            for(String u : users) {
                if(!u.trim().isEmpty()) activeList.add(u.trim());
            }
        }
        return activeList;
    }
    public ClientModel getClientModel() { return client; }
    public UserDTO getCurrentUser() { return client.getCurrentUser(); }
    public void logout() { client.logout(); }
    public Response getAllItems() { return client.getAllItems(); }
}
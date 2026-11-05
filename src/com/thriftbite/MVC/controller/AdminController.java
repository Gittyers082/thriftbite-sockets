package com.thriftbite.MVC.controller;

import com.thriftbite.MVC.model.AdminModel;
import com.thriftbite.common.Response;
import com.thriftbite.common.TransactionDTO;
import com.thriftbite.common.UserDTO;

import java.util.List;

public class AdminController {

    private final AdminModel model;

    public AdminController(AdminModel model) {
        this.model = model;
    }

    public void logout() { model.logout(); }
    public UserDTO getCurrentUser() { return model.getCurrentUser(); }

    public List<UserDTO> getAllUsers() { return model.getAllUsers(); }
    public boolean approveUser(String username) { return model.approveUser(username); }
    public Response deleteUser(String username) { return model.deleteUser(username); }

    public Response getAllItems() { return model.getAllItems(); }
    public Response deleteItem(String itemId) { return model.deleteItem(itemId); }

    public List<TransactionDTO> getAllTransactions() { return model.getAllTransactions(); }
    public List<String> getActiveUsers() { return model.getActiveUsers(); }
}
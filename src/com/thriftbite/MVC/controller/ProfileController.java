package com.thriftbite.MVC.controller;

import com.thriftbite.MVC.model.ClientModel;
import com.thriftbite.common.Response;

public class ProfileController {
    private final ClientModel model;

    public ProfileController(ClientModel model) {
        this.model = model;
    }

    public Response updateUsername(String newUsername) {
        return model.updateUsername(newUsername);
    }

    public Response updatePassword(String newPassword) {
        return model.updatePassword(newPassword);
    }

    public void logout() {
        model.logout();
    }
}
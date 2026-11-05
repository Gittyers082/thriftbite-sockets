package com.thriftbite.MVC.controller;

import com.thriftbite.MVC.view.client.LoginPanel;
import com.thriftbite.MVC.view.client.OwnerDashboard;
import com.thriftbite.MVC.view.client.ConsumerDashboard;
import com.thriftbite.MVC.view.admin.AdminDashboardPanel;
import com.thriftbite.common.Role;
import com.thriftbite.common.Response;
import com.thriftbite.common.UserDTO;
import com.thriftbite.MVC.model.ClientModel;
import com.thriftbite.MVC.model.AdminModel;
import com.thriftbite.MVC.model.OwnerModel;
import com.thriftbite.MVC.model.ConsumerModel;

import javax.swing.*;

public class LoginController {

    private LoginPanel loginView;
    private ClientModel model;
    private JFrame mainFrame;

    public LoginController(LoginPanel loginView, ClientModel model, JFrame frame) {
        this.loginView = loginView;
        this.model = model;
        this.mainFrame = frame;

        loginView.addLoginListener(e -> handleLogin());
    }

    private void handleLogin() {

        String username = loginView.getUsername();
        String password = loginView.getPassword();

        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(mainFrame, "Enter username and password");
            return;
        }

        Response res = model.login(username, password);

        if (!res.isSuccess()) {
            JOptionPane.showMessageDialog(mainFrame, res.getMessage());
            return;
        }

        UserDTO user = model.getCurrentUser();

        if (user.getRole() == Role.ADMIN) {
            AdminModel adminModel = new AdminModel(model);
            AdminDashboardPanel adminPanel = new AdminDashboardPanel(adminModel);
            mainFrame.setContentPane(adminPanel);

        } else if (user.getRole() == Role.OWNER) {
            OwnerModel ownerModel = new OwnerModel(model);
            OwnerDashboard owner = new OwnerDashboard(ownerModel);
            mainFrame.setContentPane(owner);

        } else {
            ConsumerModel consumerModel = new ConsumerModel(model);
            ConsumerDashboard consumer = new ConsumerDashboard(consumerModel);
            mainFrame.setContentPane(consumer);
        }

        mainFrame.setResizable(true);
        mainFrame.setSize(1000, 700);
        mainFrame.setLocationRelativeTo(null);

        mainFrame.revalidate();
        mainFrame.repaint();
    }
}
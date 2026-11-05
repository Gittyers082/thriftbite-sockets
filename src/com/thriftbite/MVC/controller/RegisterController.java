package com.thriftbite.MVC.controller;

import com.thriftbite.MVC.view.client.RegisterPanel;
import com.thriftbite.common.Response;
import com.thriftbite.MVC.model.ClientModel;
import com.thriftbite.MVC.view.client.LoginPanel;
import com.thriftbite.MVC.view.client.MainFrameLauncher;
import javax.swing.*;
import java.awt.*;

public class RegisterController {

    private RegisterPanel view;
    private ClientModel model;
    private JFrame frame;

    public RegisterController(RegisterPanel view, ClientModel model, JFrame frame) {
        this.view = view;
        this.model = model;
        this.frame = frame;

        view.addRegisterListener(e -> handleRegister());
        view.addBackToLoginListener(e -> MainFrameLauncher.showLogin(frame, model));
    }

    private void handleRegister() {
        String username = view.getUsername();
        String password = view.getPassword();
        com.thriftbite.common.Role role = view.getSelectedRole();

        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Enter username and password");
            return;
        }

        if (password.length() < 8 || password.length() > 16) {
            JOptionPane.showMessageDialog(frame,
                    "Password must be between 8 and 16 characters long.",
                    "Invalid Password",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        Response res = model.register(username, password, role);

        if (res.isSuccess()) {
            JOptionPane.showMessageDialog(frame, "Account created successfully! Please wait for account approval.");
            switchToLogin();
        } else {
            JOptionPane.showMessageDialog(frame, res.getMessage());
        }
    }

    private void switchToLogin() {

        LoginPanel loginPanel = new LoginPanel();

        loginPanel.setCreateAccountVisible(false);

        new LoginController(loginPanel, model, frame);

        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.add(loginPanel);

        frame.setContentPane(wrapper);
        frame.revalidate();
        frame.repaint();

        frame.pack();
        frame.setLocationRelativeTo(null);
    }
}
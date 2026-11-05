package com.thriftbite.MVC.view.client;

import com.thriftbite.MVC.model.ClientModel;
import com.thriftbite.MVC.view.Theme;
import com.thriftbite.common.Response;
import com.thriftbite.MVC.controller.ProfileController;

import javax.swing.*;
import java.awt.*;

public class ProfilePanel extends JPanel {
    private ProfileController controller;
    private ClientModel model;

    public ProfilePanel(ClientModel model) {
        this.model = model;
        this.controller = new ProfileController(model);
        setLayout(new BorderLayout());
        setBackground(Theme.BACKGROUND);

        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        headerPanel.setBackground(Theme.PRIMARY);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));

        JLabel title = new JLabel("My Profile");
        title.setFont(new Font("Arial", Font.BOLD, 28));
        title.setForeground(Theme.TEXT_LIGHT);
        headerPanel.add(title);

        add(headerPanel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setBackground(Theme.BACKGROUND);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(15, 15, 15, 15);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // type
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.EAST;
        JLabel lblRoleTitle = new JLabel("Account Type:");
        lblRoleTitle.setFont(Theme.STANDARD_FONT);
        lblRoleTitle.setForeground(Theme.TEXT_MAIN);
        centerPanel.add(lblRoleTitle, gbc);

        gbc.gridx = 1; gbc.anchor = GridBagConstraints.WEST;
        String roleName = "CONSUMER";
        if (model.getCurrentUser().getRole() != null) {
            roleName = model.getCurrentUser().getRole().name();
        }
        JLabel lblRole = new JLabel(roleName);
        lblRole.setFont(Theme.BUTTON_FONT);
        lblRole.setForeground(Theme.PRIMARY);
        centerPanel.add(lblRole, gbc);

        // user-
        gbc.gridx = 0; gbc.gridy++; gbc.anchor = GridBagConstraints.EAST;
        JLabel lblUser = new JLabel("Username:");
        lblUser.setFont(Theme.STANDARD_FONT);
        lblUser.setForeground(Theme.TEXT_MAIN);
        centerPanel.add(lblUser, gbc);

        gbc.gridx = 1; gbc.anchor = GridBagConstraints.WEST;
        JTextField txtUser = new JTextField(model.getCurrentUser().getUsername());
        txtUser.setPreferredSize(new Dimension(200, 35));
        centerPanel.add(txtUser, gbc);

        gbc.gridx = 2;
        JButton btnUpdateUser = new JButton("Update Name");
        Theme.stylePrimaryButton(btnUpdateUser);
        centerPanel.add(btnUpdateUser, gbc);

        //passwoord
        gbc.gridx = 0; gbc.gridy++; gbc.anchor = GridBagConstraints.EAST;
        JLabel lblPass = new JLabel("New Password:");
        lblPass.setFont(Theme.STANDARD_FONT);
        lblPass.setForeground(Theme.TEXT_MAIN);
        centerPanel.add(lblPass, gbc);

        gbc.gridx = 1; gbc.anchor = GridBagConstraints.WEST;
        JPasswordField txtPass = new JPasswordField();
        txtPass.setPreferredSize(new Dimension(200, 35));
        centerPanel.add(txtPass, gbc);

        gbc.gridx = 2;
        JButton btnUpdatePass = new JButton("Update Password");
        Theme.stylePrimaryButton(btnUpdatePass);
        centerPanel.add(btnUpdatePass, gbc);

        add(centerPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottomPanel.setBackground(Theme.BACKGROUND);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 40, 0));

        JButton btnLogout = new JButton("Log Out");
        btnLogout.setBackground(new Color(220, 53, 69));
        btnLogout.setForeground(Color.WHITE);
        btnLogout.setFont(Theme.BUTTON_FONT);
        btnLogout.setFocusPainted(false);
        btnLogout.setBorderPainted(false);
        btnLogout.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnLogout.setPreferredSize(new Dimension(200, 45));

        bottomPanel.add(btnLogout);

        add(bottomPanel, BorderLayout.SOUTH);

        btnUpdateUser.addActionListener(e -> {
            String newName = txtUser.getText().trim();
            if (newName.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Username cannot be empty!", "Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            Response res = controller.updateUsername(newName);
            if (res.isSuccess()) {
                JOptionPane.showMessageDialog(this, "Username changed successfully to: " + newName);
            } else {
                JOptionPane.showMessageDialog(this, res.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                txtUser.setText(model.getCurrentUser().getUsername());
            }
        });

        btnUpdatePass.addActionListener(e -> {
            String newPass = new String(txtPass.getPassword());
            if (newPass.trim().isEmpty() || newPass.length() < 8 || newPass.length() > 16) {
                JOptionPane.showMessageDialog(this, "Password must be 8-16 characters long!", "Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            Response res = controller.updatePassword(newPass);
            if (res.isSuccess()) {
                JOptionPane.showMessageDialog(this, "Password updated successfully!");
                txtPass.setText("");
            } else {
                JOptionPane.showMessageDialog(this, "Failed to update: " + res.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnLogout.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to log out?", "Confirm", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {

                controller.logout();
                JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(this);
                if (frame != null) {
                    MainFrameLauncher.showLogin(frame, model);
                }
            }
        });
    }
}
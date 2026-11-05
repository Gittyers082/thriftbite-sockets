package com.thriftbite.MVC.view.client;

import com.thriftbite.MVC.view.Theme;
import com.thriftbite.common.Role;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

public class RegisterPanel extends JPanel {

    private JTextField txtUsername;
    private JPasswordField txtPassword;
    private JButton btnRegister;
    private JButton btnBackToLogin;
    private JComboBox<String> cbRole;

    public RegisterPanel() {
        setLayout(new GridLayout(1, 2));

        // left part
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new GridBagLayout());
        leftPanel.setBackground(Theme.PRIMARY);
        leftPanel.setBorder(BorderFactory.createEmptyBorder(100, 50, 100, 50));

        GridBagConstraints leftGbc = new GridBagConstraints();
        leftGbc.gridx = 0;

        JLabel bigTitle = new JLabel("Join ThriftBite");
        bigTitle.setFont(new Font("Arial", Font.BOLD, 42));
        bigTitle.setForeground(Theme.TEXT_LIGHT);
        leftGbc.gridy = 0;
        leftPanel.add(bigTitle, leftGbc);

        JLabel slogan = new JLabel("Be part of the zero-waste movement.");
        slogan.setFont(Theme.STANDARD_FONT);
        slogan.setForeground(Theme.TEXT_LIGHT);
        leftGbc.gridy = 1;
        leftGbc.insets = new Insets(10, 0, 0, 0);
        leftPanel.add(slogan, leftGbc);

        //right part
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new GridBagLayout());
        rightPanel.setBackground(Theme.BACKGROUND);
        rightPanel.setBorder(BorderFactory.createEmptyBorder(50, 50, 50, 50));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.gridx = 0;
        gbc.gridwidth = 1;

        JLabel lblRegisterTitle = new JLabel("Create an Account");
        lblRegisterTitle.setFont(Theme.HEADER_FONT);
        lblRegisterTitle.setForeground(Theme.TEXT_MAIN);
        gbc.gridy = 0;
        gbc.gridwidth = 2; // Span both columns
        gbc.anchor = GridBagConstraints.CENTER;
        rightPanel.add(lblRegisterTitle, gbc);

        gbc.gridwidth = 1;

        //user
        JLabel lblUser = new JLabel("Username:");
        lblUser.setFont(Theme.STANDARD_FONT);
        lblUser.setForeground(Theme.TEXT_MAIN);
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.EAST;
        rightPanel.add(lblUser, gbc);

        txtUsername = new JTextField();
        txtUsername.setPreferredSize(new Dimension(220, 35));
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        rightPanel.add(txtUsername, gbc);

        //pass
        gbc.gridx = 0;
        gbc.gridy = 2;
        JLabel lblPass = new JLabel("Password:");
        lblPass.setFont(Theme.STANDARD_FONT);
        lblPass.setForeground(Theme.TEXT_MAIN);
        gbc.anchor = GridBagConstraints.EAST;
        rightPanel.add(lblPass, gbc);

        txtPassword = new JPasswordField();
        txtPassword.setPreferredSize(new Dimension(220, 35));
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        rightPanel.add(txtPassword, gbc);

        //type
        gbc.gridx = 0;
        gbc.gridy = 3;
        JLabel lblRole = new JLabel("Account Type:");
        lblRole.setFont(Theme.STANDARD_FONT);
        lblRole.setForeground(Theme.TEXT_MAIN);
        gbc.anchor = GridBagConstraints.EAST;
        rightPanel.add(lblRole, gbc);

        cbRole = new JComboBox<>(new String[]{"Consumer", "Food Business Owner"});
        cbRole.setPreferredSize(new Dimension(220, 35));
        cbRole.setBackground(Color.WHITE);
        cbRole.setFont(Theme.STANDARD_FONT);
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        rightPanel.add(cbRole, gbc);

        //buttons
        btnRegister = new JButton("Create Account");
        btnRegister.setPreferredSize(new Dimension(220, 40));
        Theme.styleAccentButton(btnRegister);
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        rightPanel.add(btnRegister, gbc);

        btnBackToLogin = new JButton("Back to Login");
        btnBackToLogin.setPreferredSize(new Dimension(220, 40));
        Theme.stylePrimaryButton(btnBackToLogin);
        gbc.gridy = 5;
        rightPanel.add(btnBackToLogin, gbc);

        add(leftPanel);
        add(rightPanel);
    }

    public String getUsername() { return txtUsername.getText(); }
    public String getPassword() { return new String(txtPassword.getPassword()); }

    public Role getSelectedRole() {
        if (cbRole.getSelectedIndex() == 1) {
            return Role.OWNER;
        }
        return Role.CONSUMER;
    }

    public void addRegisterListener(ActionListener l) { btnRegister.addActionListener(l); }
    public void addBackToLoginListener(ActionListener l) { btnBackToLogin.addActionListener(l); }
}
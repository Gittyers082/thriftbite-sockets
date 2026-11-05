package com.thriftbite.MVC.view.client;

import com.thriftbite.MVC.view.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

public class LoginPanel extends JPanel {

    private JTextField txtUsername;
    private JPasswordField txtPassword;
    private JButton btnLogin;
    private JButton btnCreateAccount;

    public LoginPanel() {

        setLayout(new GridLayout(1, 2));

        // leff
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new GridBagLayout());
        leftPanel.setBackground(Theme.PRIMARY);
        leftPanel.setBorder(BorderFactory.createEmptyBorder(100, 50, 100, 50));

        GridBagConstraints leftGbc = new GridBagConstraints();
        leftGbc.gridx = 0;

        JLabel bigTitle = new JLabel("ThriftBite");
        bigTitle.setFont(new Font("Arial", Font.BOLD, 48));
        bigTitle.setForeground(Theme.TEXT_LIGHT);
        leftPanel.add(bigTitle, leftGbc);

        JLabel slogan = new JLabel("Good Food, Less Waste.");
        slogan.setFont(Theme.STANDARD_FONT);
        slogan.setForeground(Theme.TEXT_LIGHT);
        leftGbc.gridy = 1;
        leftGbc.insets = new Insets(10, 0, 0, 0);
        leftPanel.add(slogan, leftGbc);

        //rihgt

        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new GridBagLayout());
        rightPanel.setBackground(Theme.BACKGROUND);
        rightPanel.setBorder(BorderFactory.createEmptyBorder(50, 50, 50, 50));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.gridx = 0;
        gbc.gridwidth = 1;

        JLabel lblLoginTitle = new JLabel("Welcome Back");
        lblLoginTitle.setFont(Theme.HEADER_FONT);
        lblLoginTitle.setForeground(Theme.TEXT_MAIN);
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        rightPanel.add(lblLoginTitle, gbc);

        gbc.gridwidth = 1;

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

        btnLogin = new JButton("Login");
        btnLogin.setPreferredSize(new Dimension(220, 40));
        Theme.stylePrimaryButton(btnLogin);
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        rightPanel.add(btnLogin, gbc);

        btnCreateAccount = new JButton("Create Account");
        btnCreateAccount.setPreferredSize(new Dimension(220, 40));
        Theme.styleAccentButton(btnCreateAccount);
        gbc.gridy = 4;
        rightPanel.add(btnCreateAccount, gbc);

        add(leftPanel);
        add(rightPanel);
    }

    public String getUsername() { return txtUsername.getText(); }
    public String getPassword() { return new String(txtPassword.getPassword()); }
    public void addLoginListener(ActionListener l) { btnLogin.addActionListener(l); }
    public void addCreateAccountListener(ActionListener l) { btnCreateAccount.addActionListener(l); }
    public void setCreateAccountVisible(boolean visible) {
        btnCreateAccount.setVisible(visible);
    }
}
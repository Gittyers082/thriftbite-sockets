package com.thriftbite.MVC.view.client;

import com.thriftbite.MVC.controller.LoginController;
import com.thriftbite.MVC.model.ClientModel;
import com.thriftbite.MVC.controller.RegisterController;

import javax.swing.*;
import java.awt.*;

public class client2 {

    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {

            JFrame frame = new JFrame("ThriftBite");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            ClientModel model = new ClientModel();
            model.connect("localhost", 16767);

            // Lock the frame so it can't be resized
            frame.setResizable(false);

            // Delegate the UI building to showLogin so we don't repeat code!
            showLogin(frame, model);

            frame.setLocationRelativeTo(null); // Center it on the screen
            frame.setVisible(true);
        });
    }

    public static void showLogin(JFrame frame, ClientModel model) {
        LoginPanel loginView = new LoginPanel();

        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.add(loginView);

        frame.setContentPane(wrapper);

        // Attach AuthController again (important!)
        new LoginController(loginView, model, frame);

        // Create account -> Register
        loginView.addCreateAccountListener(e -> showRegister(frame, model));

        frame.revalidate();
        frame.repaint();
        frame.pack();
        frame.setLocationRelativeTo(null); // Re-center it after packing
    }

    public static void showRegister(JFrame frame, ClientModel model) {
        RegisterPanel registerPanel = new RegisterPanel();
        new RegisterController(registerPanel, model, frame);

        registerPanel.addBackToLoginListener(e -> showLogin(frame, model));

        // THE MAGIC FIX: Put the Register panel in a centering wrapper!
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.add(registerPanel);

        frame.setContentPane(wrapper); // Set the wrapper, not the panel directly
        frame.revalidate();
        frame.repaint();
        frame.pack(); // Wake up the hitboxes!
        frame.setLocationRelativeTo(null); // Re-center it
    }
}
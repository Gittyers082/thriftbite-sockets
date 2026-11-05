package com.thriftbite.CRUDS.ClientHandlers;

import com.thriftbite.common.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class UpdateClient {
    private final ClientSocketManager socketManager;
    private JPanel mainPanel;
    private CardLayout cardLayout;
    private JPanel browsePanel;
    private JPanel paymentPanel;
    private ReadClient readClient;
    private JButton claimButton;

    public UpdateClient() {
        socketManager = ClientSocketManager.getInstance();
        readClient = new ReadClient();
        initializeUI();
    }

    private void initializeUI() {
        // Create main panel with CardLayout
        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        // Create browse panel (reusing ReadClient)
        browsePanel = new JPanel(new BorderLayout());
        browsePanel.add(readClient.getMainPanel(), BorderLayout.CENTER);

        // Create claim button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        claimButton = new JButton("Claim Selected Item");
        claimButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                claimSelectedItem();
            }
        });
        buttonPanel.add(claimButton);
        browsePanel.add(buttonPanel, BorderLayout.SOUTH);

        // Create payment panel
        paymentPanel = createPaymentPanel();

        // Add panels to card layout
        mainPanel.add(browsePanel, "BROWSE");
        mainPanel.add(paymentPanel, "PAYMENT");

        // Show browse panel by default
        cardLayout.show(mainPanel, "BROWSE");
    }

    private JPanel createPaymentPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel formPanel = new JPanel(new GridLayout(0, 2, 10, 10));

        formPanel.add(new JLabel("Payment Method:"));
        JComboBox<String> paymentMethodCombo = new JComboBox<>(new String[]{"Credit Card", "PayPal", "Cash on Pickup"});
        formPanel.add(paymentMethodCombo);

        formPanel.add(new JLabel("Card Number:"));
        JTextField cardNumberField = new JTextField(20);
        formPanel.add(cardNumberField);

        formPanel.add(new JLabel("Expiry Date:"));
        JTextField expiryField = new JTextField(20);
        formPanel.add(expiryField);

        formPanel.add(new JLabel("CVV:"));
        JPasswordField cvvField = new JPasswordField(20);
        formPanel.add(cvvField);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cardLayout.show(mainPanel, "BROWSE");
                readClient.refresh(); // Refresh the item list
            }
        });

        JButton confirmButton = new JButton("Confirm Payment");
        confirmButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JOptionPane.showMessageDialog(mainPanel,
                        "Payment processed successfully!",
                        "Success",
                        JOptionPane.INFORMATION_MESSAGE);
                cardLayout.show(mainPanel, "BROWSE");
                readClient.refresh(); // Refresh the item list
            }
        });

        buttonPanel.add(cancelButton);
        buttonPanel.add(confirmButton);

        panel.add(new JLabel("Complete Your Payment", JLabel.CENTER), BorderLayout.NORTH);
        panel.add(formPanel, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void claimSelectedItem() {
        int selectedRow = readClient.getItemTable().getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(mainPanel,
                    "Please select an item to claim",
                    "No Selection",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Disable the claim button to prevent double submission
        claimButton.setEnabled(false);

        try {
            // Get the item ID from the selected row
            int modelRow = readClient.getItemTable().convertRowIndexToModel(selectedRow);
            String itemId = readClient.getItemTable().getModel().getValueAt(modelRow, 0).toString();

            // Send claim request to server
            Request request = new Request("CLAIM_ITEM", itemId);
            Response response = socketManager.sendRequest(request);

            if (response != null && response.isSuccess()) {
                // Switch to payment panel
                cardLayout.show(mainPanel, "PAYMENT");
            } else {
                JOptionPane.showMessageDialog(mainPanel,
                        "Failed to claim item: " + (response != null ? response.getMessage() : "No response from server"),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                // Re-enable the claim button
                claimButton.setEnabled(true);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainPanel,
                    "Error: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            // Re-enable the claim button
            claimButton.setEnabled(true);
        }
    }

    public JPanel getMainPanel() {
        return mainPanel;
    }
}

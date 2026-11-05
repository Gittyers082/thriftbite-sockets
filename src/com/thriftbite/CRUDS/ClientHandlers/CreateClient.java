package com.thriftbite.CRUDS.ClientHandlers;

import com.thriftbite.MVC.controller.ItemController;
import com.thriftbite.common.ItemDTO;
import com.thriftbite.common.ItemStatus;

import javax.swing.*;
import java.awt.*;
/**
 * CreateClient is responsible for handling the UI logic
 * for creating a new Item in the system.
 */
public class CreateClient {

    private ItemController controller;
    private JPanel mainPanel;
    private JTextField nameField;
    private JTextField quantityField;
    private JTextField priceField;
    private JTextField expiryDateField;

    public CreateClient(ItemController controller) {
        this.controller = controller;
        initializeUI();
    }
    /**
     * Initializes and builds the user interface components.
     */
    private void initializeUI() {
        mainPanel = new JPanel(new BorderLayout());

        JPanel formPanel = new JPanel(new GridLayout(0, 2, 5, 5));
        formPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        formPanel.add(new JLabel("Name:"));
        nameField = new JTextField(20);
        formPanel.add(nameField);

        formPanel.add(new JLabel("Quantity:"));
        quantityField = new JTextField(20);
        formPanel.add(quantityField);

        formPanel.add(new JLabel("Price:"));
        priceField = new JTextField(20);
        formPanel.add(priceField);

        formPanel.add(new JLabel("Expiry Date (YYYY-MM-DD):"));
        expiryDateField = new JTextField(20);
        formPanel.add(expiryDateField);

        JButton createButton = new JButton("Create Item");
        createButton.addActionListener(e -> createItem()); // cleaner lambda

        mainPanel.add(formPanel, BorderLayout.CENTER);
        mainPanel.add(createButton, BorderLayout.SOUTH);
    }
    /**
     * Handles item creation when the button is clicked.
     * Performs validation, parsing, DTO creation,
     * and calls the controller to persist the item.
     */
    private void createItem() {
        try {
            String name = nameField.getText().trim();
            String expiryDate = expiryDateField.getText().trim();

            if (name.isEmpty() || expiryDate.isEmpty()
                    || quantityField.getText().trim().isEmpty()
                    || priceField.getText().trim().isEmpty()) {

                JOptionPane.showMessageDialog(
                        mainPanel,
                        "All fields are required",
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            int quantity = Integer.parseInt(quantityField.getText().trim());
            double price = Double.parseDouble(priceField.getText().trim());

            // Create ItemDTO
            ItemDTO item = new ItemDTO();
            item.setName(name);
            item.setQuantity(quantity);
            item.setPrice(price);
            item.setExpiryDate(expiryDate);
            item.setStatus(ItemStatus.AVAILABLE);

            item.setStatus(ItemStatus.AVAILABLE);

            boolean success = controller.addItem(item);

            if (success) {
                JOptionPane.showMessageDialog(mainPanel, "Item created successfully", "Success", JOptionPane.INFORMATION_MESSAGE);
                clearFields();
            } else {
                JOptionPane.showMessageDialog(mainPanel, "Failed to create item", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(
                    mainPanel,
                    "Quantity must be an integer and Price must be a number.",
                    "Invalid Input",
                    JOptionPane.ERROR_MESSAGE
            );
        } catch (Exception e) {
            JOptionPane.showMessageDialog(
                    mainPanel,
                    "Unexpected error: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
            e.printStackTrace(); // helpful for debugging
        }
    }

    private void clearFields() {
        nameField.setText("");
        quantityField.setText("");
        priceField.setText("");
        expiryDateField.setText("");
    }

    public JPanel getMainPanel() {
        return mainPanel;
    }
}

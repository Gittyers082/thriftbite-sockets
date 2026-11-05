package com.thriftbite.CRUDS.FoodHandlers;

import com.thriftbite.MVC.view.Theme;
import com.thriftbite.common.*;
import com.thriftbite.MVC.model.OwnerModel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class CreateHandler {
    private final OwnerModel model;
    private JPanel mainPanel;
    private JTextField itemNameField;
    private JTextField quantityField;
    private JTextField pickupLocationField;
    private JTextField expiryTimeField;
    private JTextField originalPriceField;
    private JTextField discountedPriceField;

    public CreateHandler(OwnerModel model) {
        this.model = model;
        initializeUI();
    }

    // This part of the code creates the GUI of the Create Part of the Food Handler
    private void initializeUI() {
        mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(Theme.BACKGROUND);

        // 1. Top Title Bar
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        titlePanel.setBackground(Theme.BACKGROUND);
        titlePanel.setBorder(new EmptyBorder(20, 20, 0, 20));

        JLabel titleLabel = new JLabel("Create a New Food Listing");
        titleLabel.setFont(Theme.HEADER_FONT);
        titleLabel.setForeground(Theme.PRIMARY);
        titlePanel.add(titleLabel);
        mainPanel.add(titlePanel, BorderLayout.NORTH);

        // 2. Center Form Container (Keeps the form from stretching too wide)
        JPanel centerContainer = new JPanel(new GridBagLayout());
        centerContainer.setBackground(Theme.BACKGROUND);

        JPanel formPanel = new JPanel(new GridLayout(6, 2, 20, 20));
        formPanel.setBackground(Theme.BACKGROUND);
        formPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Initialize styled fields
        itemNameField = createStyledTextField();
        quantityField = createStyledTextField("e.g. 5");
        pickupLocationField = createStyledTextField();
        expiryTimeField = createStyledTextField("YYYY-MM-DD HH:MM");
        originalPriceField = createStyledTextField("₱ 0.00");
        discountedPriceField = createStyledTextField("₱ 0.00");

        // Add labels and fields to the grid
        formPanel.add(createStyledLabel("Item Name:"));
        formPanel.add(itemNameField);

        formPanel.add(createStyledLabel("Quantity Available:"));
        formPanel.add(quantityField);

        formPanel.add(createStyledLabel("Pickup Location:"));
        formPanel.add(pickupLocationField);

        formPanel.add(createStyledLabel("Expiry Time:"));
        formPanel.add(expiryTimeField);

        formPanel.add(createStyledLabel("Original Price:"));
        formPanel.add(originalPriceField);

        formPanel.add(createStyledLabel("Current Discounted Price:"));
        formPanel.add(discountedPriceField);

        centerContainer.add(formPanel);
        mainPanel.add(centerContainer, BorderLayout.CENTER);

        // 3. Bottom Button Area
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottomPanel.setBackground(Theme.BACKGROUND);
        bottomPanel.setBorder(new EmptyBorder(10, 20, 40, 20));

        JButton sellButton = new JButton("Post Listing");
        Theme.styleAccentButton(sellButton);
        sellButton.setPreferredSize(new Dimension(250, 45)); // Big, clickable button
        sellButton.setFont(new Font("Arial", Font.BOLD, 16));

        sellButton.addActionListener(e -> createItemListing());

        bottomPanel.add(sellButton);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
    }

    private JLabel createStyledLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.STANDARD_FONT);
        label.setForeground(Theme.TEXT_MAIN);
        return label;
    }

    private JTextField createStyledTextField() {
        return createStyledTextField("");
    }

    //helper gui
    private JTextField createStyledTextField(String placeholderText) {
        JTextField field = new JTextField(20);
        field.setFont(Theme.STANDARD_FONT);
        field.setToolTipText(placeholderText);

        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        return field;
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(mainPanel, message, "Validation Error", JOptionPane.ERROR_MESSAGE);
    }

    // This part of the CreateHandler Code creates the functions and logic of the Create Food Handler
    private void createItemListing() {
        try {
            String itemName = itemNameField.getText().trim();
            String quantityText = quantityField.getText().trim();
            String pickupLocation = pickupLocationField.getText().trim();
            String expiryTime = expiryTimeField.getText().trim();
            String originalPriceText = originalPriceField.getText().trim();
            String discountedPriceText = discountedPriceField.getText().trim();

            // This part validates all txt files within  the try catch and utilizes the if else whether the variable is
            // empty and would show a message error and ask the food bussiness that all fields are required

            if (itemName.isEmpty() || quantityText.isEmpty() || pickupLocation.isEmpty() ||
                    expiryTime.isEmpty() || originalPriceText.isEmpty() || discountedPriceText.isEmpty()) {
                JOptionPane.showMessageDialog(mainPanel, "All fields are required", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            DateTimeFormatter expiryFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            try {
                LocalDateTime expiryDateParsed = LocalDateTime.parse(expiryTime, expiryFormatter);
                if (expiryDateParsed.isBefore(LocalDateTime.now())) {
                    showError("The expiry time cannot be in the past!");
                    return;
                }
            } catch (DateTimeParseException ex) {
                showError("Invalid Expiry Time.\nPlease use exactly: YYYY-MM-DD HH:MM\n(Example: 2026-12-31 23:59)");
                return;
            }


            int quantity;
            try {
                quantity = Integer.parseInt(quantityText);
                if (quantity <= 0) {
                    showError("Quantity must be at least 1.");
                    return;
                }
            } catch (NumberFormatException ex) {
                showError("Quantity must be a valid whole number.");
                return;
            }


            double originalPrice;
            double discountedPrice;
            try {
                originalPrice = Double.parseDouble(originalPriceText);
                discountedPrice = Double.parseDouble(discountedPriceText);

                if (originalPrice < 0 || discountedPrice < 0) {
                    showError("Prices cannot be negative numbers.");
                    return;
                }
                if (discountedPrice > originalPrice) {
                    showError("The discounted price (" + discountedPrice + ") cannot be higher than the original price (" + originalPrice + ").");
                    return;
                }
            } catch (NumberFormatException ex) {
                showError("Prices must be valid numbers (e.g., 50.00).");
                return;
            }

            ItemDTO item = new ItemDTO();
            item.setName(itemName);
            item.setQuantity(quantity);
            item.setPickupLocation(pickupLocation);
            item.setExpiryDate(expiryTime);
            item.setOriginalPrice(originalPrice);
            item.setPrice(discountedPrice);
            item.setStatus(ItemStatus.AVAILABLE);

            Response response = model.addItem(item);

            if (response != null && response.isSuccess()) {
                JOptionPane.showMessageDialog(mainPanel, "🎉 Listing successfully posted!", "Success", JOptionPane.INFORMATION_MESSAGE);
                clearFields();
            } else {
                showError("Failed to list item: " + (response != null ? response.getMessage() : "Unknown Server Error"));
            }

        } catch (Exception e) {
            showError("An unexpected system error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void clearFields() {
        itemNameField.setText("");
        quantityField.setText("");
        pickupLocationField.setText("");
        expiryTimeField.setText("");
        originalPriceField.setText("");
        discountedPriceField.setText("");
    }

    public JPanel getMainPanel() {
        return mainPanel;
    }
}

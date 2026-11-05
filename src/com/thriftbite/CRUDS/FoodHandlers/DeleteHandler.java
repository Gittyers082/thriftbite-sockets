package com.thriftbite.CRUDS.FoodHandlers;

import com.thriftbite.common.*;
import com.thriftbite.MVC.model.OwnerModel;
import com.thriftbite.MVC.controller.ItemController;
import com.thriftbite.MVC.view.Theme; // Import Theme

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class DeleteHandler {
    private final OwnerModel model;
    private JPanel mainPanel;
    private JTable itemsTable;
    private DefaultTableModel tableModel;
    private JButton deleteButton;
    private JTextArea notificationArea;
    private ItemController controller;
    private List<ItemDTO> currentItems;

    public DeleteHandler(OwnerModel model) {
        this.model = model;
        this.controller = new ItemController(model.getClientModel());
        this.currentItems = new ArrayList<>();
        initializeUI();
    }

    private void styleTable(JTable table) {
        table.setFont(Theme.STANDARD_FONT);
        table.setRowHeight(28);
        table.setSelectionBackground(Theme.ACCENT);
        table.setSelectionForeground(Theme.TEXT_LIGHT);
        table.setGridColor(Color.LIGHT_GRAY);

        table.getTableHeader().setFont(Theme.BUTTON_FONT);
        table.getTableHeader().setBackground(Theme.PRIMARY);
        table.getTableHeader().setForeground(Theme.TEXT_LIGHT);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setResizingAllowed(false);
    }

    private void initializeUI() {
        mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBackground(Theme.BACKGROUND);
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JPanel topContainer = new JPanel(new BorderLayout(10, 5));
        topContainer.setBackground(Theme.BACKGROUND);

        JLabel titleLabel = new JLabel("Manage My Listings");
        titleLabel.setFont(Theme.HEADER_FONT);
        titleLabel.setForeground(Theme.PRIMARY);
        topContainer.add(titleLabel, BorderLayout.NORTH);

        JPanel notificationPanel = new JPanel(new BorderLayout());
        notificationPanel.setBackground(Theme.BACKGROUND);
        TitledBorder border = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY), "Expiration Notices");
        border.setTitleFont(Theme.STANDARD_FONT);
        border.setTitleColor(Theme.TEXT_MAIN);
        notificationPanel.setBorder(border);

        notificationArea = new JTextArea(3, 50);
        notificationArea.setEditable(false);
        notificationArea.setLineWrap(true);
        notificationArea.setWrapStyleWord(true);
        notificationArea.setFont(new Font("Arial", Font.BOLD, 12));
        notificationArea.setBackground(new Color(255, 245, 245));
        notificationArea.setBorder(new EmptyBorder(5, 5, 5, 5));

        JScrollPane notificationScroll = new JScrollPane(notificationArea);
        notificationScroll.setBorder(null);
        notificationPanel.add(notificationScroll, BorderLayout.CENTER);

        topContainer.add(notificationPanel, BorderLayout.SOUTH);
        mainPanel.add(topContainer, BorderLayout.NORTH);

        String[] columnNames = {"ID", "Item Name", "Orig. Price", "Curr. Price", "Qty", "Expiry Time", "Status", "Days Left"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        itemsTable = new JTable(tableModel);
        itemsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        styleTable(itemsTable);
        applyTableRenderer();

        JScrollPane scrollPane = new JScrollPane(itemsTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 10));
        buttonPanel.setBackground(Theme.BACKGROUND);

        JButton refreshButton = new JButton("Refresh");
        Theme.stylePrimaryButton(refreshButton);
        refreshButton.addActionListener(e -> refreshItems());

        JButton updateButton = new JButton("Update Selected");
        Theme.stylePrimaryButton(updateButton);
        updateButton.addActionListener(e -> updateSelectedItem());

        deleteButton = new JButton("Cancel/Delete Listing");
        Theme.styleAccentButton(deleteButton);
        deleteButton.addActionListener(e -> deleteSelectedItem());

        buttonPanel.add(refreshButton);
        buttonPanel.add(updateButton);
        buttonPanel.add(deleteButton);

        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        refreshItems();
    }

    private void applyTableRenderer() {
        itemsTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                if (isSelected) {
                    c.setBackground(table.getSelectionBackground());
                    c.setForeground(table.getSelectionForeground());
                    return c;
                }

                String status = table.getModel().getValueAt(row, 6).toString();
                String daysLeft = table.getModel().getValueAt(row, 7).toString();

                c.setBackground(Color.WHITE);
                c.setForeground(Theme.TEXT_MAIN);

                if (!"AVAILABLE".equals(status)) {
                    c.setBackground(new Color(230, 230, 230));
                    c.setForeground(Color.GRAY);
                } else if ("EXPIRED".equals(daysLeft)) {
                    c.setBackground(new Color(255, 180, 180));
                    c.setForeground(Color.BLACK);
                } else if ("< 1 day".equals(daysLeft) || "1 day".equals(daysLeft)) {
                    c.setBackground(new Color(255, 220, 150));
                    c.setForeground(Color.BLACK);
                }

                return c;
            }
        });
    }

    public void refreshItems() {
        try {
            Response response = model.getMyItems();

            if (response != null && response.isSuccess()) {
                tableModel.setRowCount(0);
                notificationArea.setText("");

                List<ItemDTO> items = parseItems(response.getPayload());
                currentItems = items != null ? items : new ArrayList<>();

                StringBuilder notifications = new StringBuilder();
                LocalDateTime now = LocalDateTime.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

                for (ItemDTO item : items) {
                    String daysUntilExpiry = "N/A";
                    try {
                        LocalDateTime expiryDateTime = LocalDateTime.parse(item.getExpiryDate(), formatter);
                        long daysLeft = ChronoUnit.DAYS.between(now, expiryDateTime);
                        long hoursLeft = ChronoUnit.HOURS.between(now, expiryDateTime);

                        if (hoursLeft < 0) {
                            daysUntilExpiry = "EXPIRED";
                            notifications.append("⚠️ Item '").append(item.getName()).append("' has EXPIRED!\n");
                        } else if (daysLeft == 0) {
                            daysUntilExpiry = "< 1 day";
                            notifications.append("⚠️ Item '").append(item.getName()).append("' expires TODAY!\n");
                        } else if (daysLeft == 1) {
                            daysUntilExpiry = "1 day";
                            notifications.append("⚠️ Item '").append(item.getName()).append("' expires TOMORROW!\n");
                        } else {
                            daysUntilExpiry = daysLeft + " days";
                        }
                    } catch (Exception ignored) {}

                    Object[] rowData = {
                            item.getId(),
                            item.getName(),
                            item.getOriginalPrice(),
                            item.getPrice(),
                            item.getQuantity(),
                            item.getExpiryDate(),
                            item.getStatus(),
                            daysUntilExpiry
                    };
                    tableModel.addRow(rowData);
                }

                if (notifications.length() > 0) {
                    notificationArea.setText(notifications.toString());
                    notificationArea.setForeground(Color.RED);
                } else {
                    notificationArea.setText("✅ No expiration warnings at this time. All items are fresh!");
                    notificationArea.setForeground(new Color(0, 150, 0));
                }
            } else {
                JOptionPane.showMessageDialog(mainPanel,
                        "Failed to retrieve items: " + (response != null ? response.getMessage() : "Not connected"),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainPanel, "Error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateSelectedItem() {
        int selectedRow = itemsTable.getSelectedRow();
        if (selectedRow >= 0) {
            if (currentItems == null || selectedRow >= currentItems.size()) {
                JOptionPane.showMessageDialog(mainPanel, "Unable to find selected item details.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            ItemDTO itemToUpdate = currentItems.get(selectedRow);

            JTextField priceField = new JTextField(String.valueOf(itemToUpdate.getPrice()));
            JTextField qtyField = new JTextField(String.valueOf(itemToUpdate.getQuantity()));

            Object[] message = {
                    "Item Name: " + itemToUpdate.getName(),
                    "New Discounted Price:", priceField,
                    "New Quantity:", qtyField
            };

            int option = JOptionPane.showConfirmDialog(mainPanel, message, "Update Item", JOptionPane.OK_CANCEL_OPTION);

            if (option == JOptionPane.OK_OPTION) {
                try {
                    itemToUpdate.setPrice(Double.parseDouble(priceField.getText()));
                    itemToUpdate.setQuantity(Integer.parseInt(qtyField.getText()));

                    if (controller.updateItem(itemToUpdate)) {
                        JOptionPane.showMessageDialog(mainPanel, "Item Updated Successfully!");
                        refreshItems();
                    } else {
                        JOptionPane.showMessageDialog(mainPanel, "Failed to update item.", "Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(mainPanel, "Price and Quantity must be valid numbers.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        } else {
            JOptionPane.showMessageDialog(mainPanel, "Please select an item to update first.");
        }
    }

    private void deleteSelectedItem() {
        int selectedRow = itemsTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(mainPanel, "Please select an item to delete", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            String status = itemsTable.getModel().getValueAt(selectedRow, 6).toString();

            if (!status.equals("AVAILABLE")) {
                JOptionPane.showMessageDialog(mainPanel,
                        "Only items with 'AVAILABLE' status can be deleted.\nThis item has status: " + status,
                        "Cannot Delete",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            String itemName = itemsTable.getModel().getValueAt(selectedRow, 1).toString();
            int confirm = JOptionPane.showConfirmDialog(mainPanel,
                    "Are you sure you want to delete the listing for '" + itemName + "'?\nThis action cannot be undone.",
                    "Confirm Deletion",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }

            String itemId = itemsTable.getModel().getValueAt(selectedRow, 0).toString();
            Response response = model.deleteItem(itemId);

            if (response != null && response.isSuccess()) {
                JOptionPane.showMessageDialog(mainPanel, "Item listing deleted successfully", "Success", JOptionPane.INFORMATION_MESSAGE);
                refreshItems();
            } else {
                JOptionPane.showMessageDialog(mainPanel, "Failed to delete item: " + (response != null ? response.getMessage() : "Not connected"), "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainPanel, "Error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private List<ItemDTO> parseItems(String payload) {
        List<ItemDTO> items = new ArrayList<>();
        try {
            if (payload != null && !payload.isEmpty()) {
                List<ItemDTO> parsedItems = XmlUtils.fromXmlList(payload, ItemDTO.class);
                if (parsedItems != null && !parsedItems.isEmpty()) {
                    return parsedItems;
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing XML: " + e.getMessage());
        }
        return items;
    }

    public JPanel getMainPanel() {
        return mainPanel;
    }
}
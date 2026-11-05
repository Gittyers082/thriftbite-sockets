package com.thriftbite.CRUDS.FoodHandlers;

import com.thriftbite.MVC.view.Theme;
import com.thriftbite.common.*;
import com.thriftbite.MVC.model.OwnerModel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class UpdateHandler {
    private final OwnerModel model;
    private JPanel mainPanel;
    private JTable reservedItemsTable;
    private DefaultTableModel tableModel;
    private JButton confirmHandoverButton;

    public UpdateHandler(OwnerModel model) {
        this.model = model;
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

        JLabel titleLabel = new JLabel("Reserved Items - Confirm Pickup");
        titleLabel.setFont(Theme.HEADER_FONT);
        titleLabel.setForeground(Theme.PRIMARY);
        topContainer.add(titleLabel, BorderLayout.NORTH);

        JLabel subtitleLabel = new JLabel("Manage items claimed by buyers. Select a row to mark it ready for pickup.");
        subtitleLabel.setFont(Theme.STANDARD_FONT);
        subtitleLabel.setForeground(Theme.TEXT_MAIN);
        topContainer.add(subtitleLabel, BorderLayout.CENTER);

        mainPanel.add(topContainer, BorderLayout.NORTH);

        String[] columnNames = {"ID", "Item Name", "Quantity", "Pickup Location", "Price", "Buyer", "Status"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        reservedItemsTable = new JTable(tableModel);
        reservedItemsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        styleTable(reservedItemsTable);
        applyTableRenderer();

        JScrollPane scrollPane = new JScrollPane(reservedItemsTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        mainPanel.add(scrollPane, BorderLayout.CENTER);


        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 10));
        buttonPanel.setBackground(Theme.BACKGROUND);

        JButton refreshButton = new JButton("Refresh");
        Theme.stylePrimaryButton(refreshButton);
        refreshButton.addActionListener(e -> refreshReservedItems());

        confirmHandoverButton = new JButton("Mark as Ready for Pickup");
        Theme.styleAccentButton(confirmHandoverButton);
        confirmHandoverButton.setPreferredSize(new Dimension(230, 40));
        confirmHandoverButton.setFont(new Font("Arial", Font.BOLD, 14));
        confirmHandoverButton.addActionListener(e -> confirmHandover());

        buttonPanel.add(refreshButton);
        buttonPanel.add(confirmHandoverButton);

        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        refreshReservedItems();
    }

    private void applyTableRenderer() {
        reservedItemsTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                if (isSelected) {
                    c.setBackground(table.getSelectionBackground());
                    c.setForeground(table.getSelectionForeground());
                    return c;
                }

                String status = table.getModel().getValueAt(row, 6).toString();

                c.setBackground(Color.WHITE);
                c.setForeground(Theme.TEXT_MAIN);

                if ("PAID".equals(status)) {

                    c.setBackground(new Color(220, 255, 220));
                    c.setForeground(new Color(0, 100, 0));
                } else if ("RESERVED".equals(status)) {

                    c.setBackground(new Color(255, 240, 200));
                    c.setForeground(Color.BLACK);
                }

                return c;
            }
        });
    }

    public void refreshReservedItems() {
        try {
            Response response = model.getMyReservedItems();

            if (response != null && response.isSuccess()) {
                // Clear existing data
                tableModel.setRowCount(0);

                // Parse the response payload (XML) to get the list of items
                List<ItemDTO> items = parseItems(response.getPayload());

                // Add items to the table
                for (ItemDTO item : items) {
                    // Only show items with RESERVED or PAID status
                    if (item.getStatus() == ItemStatus.RESERVED || item.getStatus() == ItemStatus.PAID) {
                        Object[] rowData = {
                                item.getId(),
                                item.getName(),
                                item.getQuantity(),
                                item.getPickupLocation(),
                                item.getPrice(),
                                item.getBuyerName() != null ? item.getBuyerName() : "N/A",
                                item.getStatus()
                        };
                        tableModel.addRow(rowData);
                    }
                }
            } else {
                JOptionPane.showMessageDialog(mainPanel,
                        "Failed to retrieve reserved items: " + (response != null ? response.getMessage() : "Not connected"),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainPanel,
                    "Error: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void confirmHandover() {
        int selectedRow = reservedItemsTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(mainPanel,
                    "Please select an item to confirm handover",
                    "No Selection",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Show confirmation dialog
        int confirm = JOptionPane.showConfirmDialog(mainPanel,
                "Mark this item as READY FOR PICK-UP for the buyer?",
                "Item Ready for Pickup",
                JOptionPane.YES_NO_OPTION);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            // Get the item ID from the selected row
            String itemId = reservedItemsTable.getModel().getValueAt(selectedRow, 0).toString();

            Response response = model.confirmHandover(itemId);

            if (response != null && response.isSuccess()) {
                JOptionPane.showMessageDialog(mainPanel,
                        "Item marked as READY FOR PICK-UP.\nThe client will see the updated status in their Orders section.",
                        "Success",
                        JOptionPane.INFORMATION_MESSAGE);
                refreshReservedItems();
            } else {
                JOptionPane.showMessageDialog(mainPanel,
                        "Failed to confirm handover: " + (response != null ? response.getMessage() : "Not connected"),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainPanel,
                    "Error: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private List<ItemDTO> parseItems(String payload) {
        List<ItemDTO> items = new ArrayList<>();

        try {
            // Try to parse the XML payload using XmlUtils
            if (payload != null && !payload.isEmpty()) {
                // Attempt to use the fromXmlList method if it's implemented
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
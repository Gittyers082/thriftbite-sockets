package com.thriftbite.CRUDS.ClientHandlers;

import com.thriftbite.common.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public class DeleteClient {
    private final ClientSocketManager socketManager;
    private JPanel mainPanel;
    private JTable claimedItemsTable;
    private DefaultTableModel tableModel;
    private JButton unclaimButton;

    public DeleteClient() {
        socketManager = ClientSocketManager.getInstance();
        initializeUI();
    }

    private void initializeUI() {
        mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Create table model with columns
        String[] columnNames = {"ID", "Name", "Quantity", "Price", "Expiry Date", "Status"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Make table non-editable
            }
        };

        // Create table and set properties
        claimedItemsTable = new JTable(tableModel);
        claimedItemsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Create button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refreshClaimedItems();
            }
        });

        unclaimButton = new JButton("Unclaim Selected Item");
        unclaimButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                unclaimSelectedItem();
            }
        });

        buttonPanel.add(refreshButton);
        buttonPanel.add(unclaimButton);

        // Add components to main panel
        mainPanel.add(new JLabel("My Claimed Items", JLabel.CENTER), BorderLayout.NORTH);
        mainPanel.add(new JScrollPane(claimedItemsTable), BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Initial data load
        refreshClaimedItems();
    }

    public void refreshClaimedItems() {
        try {
            // Send request to get claimed items
            Request request = new Request("GET_MY_CLAIMS", "");
            Response response = socketManager.sendRequest(request);

            if (response != null && response.isSuccess()) {
                // Clear existing data
                tableModel.setRowCount(0);

                // Parse the response payload (XML) to get the list of items
                List<ItemDTO> items = parseItems(response.getPayload());

                // Add items to the table
                for (ItemDTO item : items) {
                    Object[] rowData = {
                            item.getId(),
                            item.getName(),
                            item.getQuantity(),
                            item.getPrice(),
                            item.getExpiryDate(),
                            item.getStatus()
                    };
                    tableModel.addRow(rowData);
                }
            } else {
                JOptionPane.showMessageDialog(mainPanel,
                        "Failed to retrieve claimed items: " + (response != null ? response.getMessage() : "No response from server"),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainPanel,
                    "Error: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void unclaimSelectedItem() {
        int selectedRow = claimedItemsTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(mainPanel,
                    "Please select an item to unclaim",
                    "No Selection",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Show confirmation dialog
        int confirm = JOptionPane.showConfirmDialog(mainPanel,
                "Are you sure you want to unclaim this item?",
                "Confirm Unclaim",
                JOptionPane.YES_NO_OPTION);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            // Get the item ID from the selected row (use model index in case row sorter is used)
            int modelRow = claimedItemsTable.convertRowIndexToModel(selectedRow);
            String itemId = claimedItemsTable.getModel().getValueAt(modelRow, 0).toString();

            // Send unclaim request to server
            Request request = new Request("UNCLAIM_ITEM", itemId);
            Response response = socketManager.sendRequest(request);

            if (response.isSuccess()) {
                JOptionPane.showMessageDialog(mainPanel,
                        "Item unclaimed successfully",
                        "Success",
                        JOptionPane.INFORMATION_MESSAGE);
                refreshClaimedItems();
            } else {
                JOptionPane.showMessageDialog(mainPanel,
                        "Failed to unclaim item: " + response.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainPanel,
                    "Error: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private List<ItemDTO> parseItems(String payload) {
        // This would normally use XmlUtils.fromXmlList, but it's not implemented
        // For now, we'll create a dummy list for demonstration
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

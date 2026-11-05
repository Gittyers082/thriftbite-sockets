package com.thriftbite.CRUDS.ClientHandlers;

import com.thriftbite.common.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

public class ReadClient {
    private final ClientSocketManager socketManager;
    private JPanel mainPanel;
    private JTable itemTable;
    private DefaultTableModel tableModel;
    private JTextField searchField;
    private TableRowSorter<DefaultTableModel> sorter;

    public ReadClient() {
        socketManager = ClientSocketManager.getInstance();
        initializeUI();
    }

    private void initializeUI() {
        mainPanel = new JPanel(new BorderLayout());

        // Create table model with columns
        String[] columnNames = {"ID", "Name", "Quantity", "Price", "Expiry Date", "Status"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Make table non-editable
            }
        };

        // Create table and set properties
        itemTable = new JTable(tableModel);
        itemTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        itemTable.setAutoCreateRowSorter(true);

        // Create sorter for filtering
        sorter = new TableRowSorter<>(tableModel);
        itemTable.setRowSorter(sorter);

        // Create search panel
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        searchField = new JTextField(20);
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                String text = searchField.getText();
                if (text.trim().length() == 0) {
                    sorter.setRowFilter(null);
                } else {
                    sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text, 1)); // Filter by name (column index 1)
                }
            }
        });

        searchPanel.add(new JLabel("Search by name: "), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);

        // Create refresh button
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refresh();
            }
        });
        searchPanel.add(refreshButton, BorderLayout.EAST);

        // Add components to main panel
        mainPanel.add(searchPanel, BorderLayout.NORTH);
        mainPanel.add(new JScrollPane(itemTable), BorderLayout.CENTER);

        // Initial data load
        refresh();
    }

    public void refresh() {
        try {
            // Send request to get available items
            Request request = new Request("GET_AVAILABLE", "");
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
                        "Failed to retrieve items: " + (response != null ? response.getMessage() : "No response from server"),
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
            // This is a placeholder and might need to be updated based on the actual implementation
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

    public JTable getItemTable() {
        return itemTable;
    }
}
package com.thriftbite.CRUDS.FoodHandlers;

import com.thriftbite.common.*;
import com.thriftbite.MVC.model.OwnerModel;
import com.thriftbite.MVC.view.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ReadHandler {
    private final OwnerModel model;
    private JPanel mainPanel;
    private JTable itemTable;
    private DefaultTableModel tableModel;
    private JTextField searchField;
    private TableRowSorter<DefaultTableModel> sorter;

    public ReadHandler(OwnerModel model) {
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


        JPanel topContainer = new JPanel(new BorderLayout(10, 10));
        topContainer.setBackground(Theme.BACKGROUND);

        JLabel titleLabel = new JLabel("My Listed Items");
        titleLabel.setFont(Theme.HEADER_FONT);
        titleLabel.setForeground(Theme.PRIMARY);
        topContainer.add(titleLabel, BorderLayout.NORTH);


        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        searchPanel.setBackground(Theme.BACKGROUND);

        JLabel searchLabel = new JLabel("Search by Item Name:");
        searchLabel.setFont(Theme.STANDARD_FONT);
        searchLabel.setForeground(Theme.TEXT_MAIN);

        searchField = new JTextField(20);
        searchField.setFont(Theme.STANDARD_FONT);
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));

        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                String text = searchField.getText();
                if (text.trim().length() == 0) {
                    sorter.setRowFilter(null);
                } else {

                    sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text, 1));
                }
            }
        });

        JButton refreshButton = new JButton("Refresh");
        Theme.stylePrimaryButton(refreshButton);
        refreshButton.addActionListener(e -> refresh());

        searchPanel.add(searchLabel);
        searchPanel.add(searchField);
        searchPanel.add(refreshButton);

        topContainer.add(searchPanel, BorderLayout.CENTER);
        mainPanel.add(topContainer, BorderLayout.NORTH);


        String[] columnNames = {"ID", "Item Name", "Quantity", "Pickup Location", "Original Price", "Discounted Price", "Expiry Time", "Status"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        itemTable = new JTable(tableModel);
        itemTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        styleTable(itemTable);


        applyTableRenderer();

        sorter = new TableRowSorter<>(tableModel);
        itemTable.setRowSorter(sorter);

        JScrollPane scrollPane = new JScrollPane(itemTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        refresh();
    }

    private void applyTableRenderer() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        LocalDateTime now = LocalDateTime.now();

        itemTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                if (isSelected) {
                    c.setBackground(table.getSelectionBackground());
                    c.setForeground(table.getSelectionForeground());
                    return c;
                }

                int modelRow = table.convertRowIndexToModel(row);

                String expiryStr = (String) table.getModel().getValueAt(modelRow, 6);
                String status = table.getModel().getValueAt(modelRow, 7).toString();

                c.setBackground(Color.WHITE);
                c.setForeground(Theme.TEXT_MAIN);

                if (!"AVAILABLE".equals(status)) {
                    c.setBackground(new Color(230, 230, 230));
                    c.setForeground(Color.GRAY);
                } else if (!"N/A".equals(expiryStr) && expiryStr != null && !expiryStr.isEmpty()) {
                    try {
                        LocalDateTime expiry = LocalDateTime.parse(expiryStr, formatter);
                        if (expiry.isBefore(now)) {
                            c.setBackground(new Color(255, 180, 180));
                            c.setForeground(Color.BLACK);
                        } else if (expiry.isBefore(now.plusHours(24))) {
                            c.setBackground(new Color(255, 220, 150));
                            c.setForeground(Color.BLACK);
                        }
                    } catch (Exception ignored) {}
                }
                return c;
            }
        });
    }

    public void refresh() {
        try {
            Response response = model.getMyItems();

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
                            item.getPickupLocation(),
                            item.getOriginalPrice(),
                            item.getPrice(),
                            item.getExpiryDate(),
                            item.getStatus()
                    };
                    tableModel.addRow(rowData);
                }
            } else {
                JOptionPane.showMessageDialog(mainPanel,
                        "Failed to retrieve items: " + (response != null ? response.getMessage() : "Not connected"),
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

    public JTable getItemTable() {
        return itemTable;
    }
}

package com.thriftbite.MVC.view.admin;

import com.thriftbite.MVC.model.AdminModel;
import com.thriftbite.MVC.model.ClientModel;
import com.thriftbite.common.*;
import com.thriftbite.MVC.view.Theme;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import com.thriftbite.MVC.controller.ItemController;
import com.thriftbite.common.ItemDTO;
import com.thriftbite.common.XmlUtils;

import java.util.List;
import java.util.stream.Collectors;


public class AdminItemsPanel extends JPanel {

    private final AdminModel model;
    private final DefaultTableModel tableModel;
    private final JTable table;
    private JComboBox<String> cbStatusFilter;
    private ClientModel basemodel;

    public AdminItemsPanel(AdminModel model) {
        this.model = model;
        setLayout(new BorderLayout());
        setBackground(Theme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setBackground(Theme.BACKGROUND);

        JLabel lblFilter = new JLabel("Filter by Status: ");
        lblFilter.setFont(Theme.STANDARD_FONT);
        lblFilter.setForeground(Theme.TEXT_MAIN);

        cbStatusFilter = new JComboBox<>(new String[]{"ALL", "AVAILABLE", "RESERVED", "PAID"});
        cbStatusFilter.setFont(Theme.STANDARD_FONT);
        cbStatusFilter.setBackground(Color.WHITE);

        topPanel.add(lblFilter);
        topPanel.add(cbStatusFilter);
        add(topPanel, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(new Object[]{"ID", "Item Name", "Owner", "Quantity", "Price", "Status",}, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        table = new JTable(tableModel);
        table.setFont(Theme.STANDARD_FONT);
        table.setRowHeight(30);

        table.getTableHeader().setFont(Theme.BUTTON_FONT);
        table.getTableHeader().setBackground(Theme.PRIMARY);
        table.getTableHeader().setForeground(Theme.TEXT_LIGHT);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        add(new JScrollPane(table), BorderLayout.CENTER);

        // bottom area (buttons)
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.setBackground(Theme.BACKGROUND);

        JButton btnRefresh = new JButton("Refresh Items");
        Theme.stylePrimaryButton(btnRefresh);

        JButton btnDelete = new JButton("Force Delete Item");
        Theme.styleAccentButton(btnDelete); // Orange warning button

        bottomPanel.add(btnRefresh);
        bottomPanel.add(btnDelete);
        add(bottomPanel, BorderLayout.SOUTH);

        // actionns

        btnRefresh.addActionListener(e -> loadItems());

        cbStatusFilter.addActionListener(e -> loadItems());

        btnDelete.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(this, "Please select an item to delete.", "Warning", JOptionPane.WARNING_MESSAGE);
                return;
            }

            String itemId = (String) tableModel.getValueAt(row, 0);
            String itemName = (String) tableModel.getValueAt(row, 1);

            int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to forcibly delete item: " + itemName + "?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                Response res = model.deleteItem(itemId);
                if (res.isSuccess()) {
                    JOptionPane.showMessageDialog(this, "Item deleted successfully.");
                    loadItems();
                } else {
                    JOptionPane.showMessageDialog(this, "Failed to delete: " + res.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        loadItems();
    }

        private void loadItems() {
            tableModel.setRowCount(0);
            Response res = basemodel.getAllItems();
            if (res.isSuccess() && res.getPayload() != null) {
                List<ItemDTO> items = XmlUtils.fromXmlList(res.getPayload(), ItemDTO.class);
                if (items != null) {
                    String selectedStatus = (String) cbStatusFilter.getSelectedItem();
                    if (!"ALL".equals(selectedStatus)) {
                        items = items.stream()
                                .filter(i -> i.getStatus().name().equals(selectedStatus))
                                .collect(Collectors.toList());
                    }
                    for (ItemDTO i : items) {
                        tableModel.addRow(new Object[]{
                                i.getId(),
                                i.getName(),
                                i.getOwnerId(),
                                i.getQuantity(),
                                "₱" + i.getPrice(),
                                i.getStatus()
                        });
                    }
                }
            }
        }
    }

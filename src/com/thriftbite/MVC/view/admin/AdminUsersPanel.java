package com.thriftbite.MVC.view.admin;

import com.thriftbite.MVC.model.AdminModel;
import com.thriftbite.MVC.model.ClientModel;
import com.thriftbite.common.Response;
import com.thriftbite.common.UserDTO;
import com.thriftbite.MVC.view.Theme;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class AdminUsersPanel extends JPanel {
    private JTable table;
    private DefaultTableModel tableModel;
    private AdminModel model;
    private ClientModel basemodel;

    public AdminUsersPanel(AdminModel model) {
        setLayout(new BorderLayout());
        setBackground(Theme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // --- STYLED TABLE ---
        tableModel = new DefaultTableModel(new Object[]{"Username", "Account Type"}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        table = new JTable(tableModel);
        table.setFont(Theme.STANDARD_FONT);
        table.setRowHeight(30); // Taller rows for readability

        // Custom Teal Table Headers
        table.getTableHeader().setFont(Theme.BUTTON_FONT);
        table.getTableHeader().setBackground(Theme.PRIMARY);
        table.getTableHeader().setForeground(Theme.TEXT_LIGHT);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        add(new JScrollPane(table), BorderLayout.CENTER);

        // --- BOTTOM PANEL (Buttons) ---
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.setBackground(Theme.BACKGROUND);

        JButton btnRefresh = new JButton("Refresh Users");
        Theme.stylePrimaryButton(btnRefresh);

        JButton btnDelete = new JButton("Delete User");
        Theme.styleAccentButton(btnDelete); // Orange warning button for deletion

        bottomPanel.add(btnRefresh);
        bottomPanel.add(btnDelete);
        add(bottomPanel, BorderLayout.SOUTH);

        // --- ACTIONS ---
        btnRefresh.addActionListener(e -> loadUsers());

        btnDelete.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(this, "Please select a user to delete.", "Warning", JOptionPane.WARNING_MESSAGE);
                return;
            }

            String username = (String) tableModel.getValueAt(row, 0);

            // Safety Check: Prevent admin from deleting themselves
            if (username.equalsIgnoreCase("admin") || username.equalsIgnoreCase(basemodel.getCurrentUser().getUsername())) {
                JOptionPane.showMessageDialog(this, "You cannot delete the admin account or yourself!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to permanently delete user: " + username + "?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                Response res = model.deleteUser(username);
                if (res.isSuccess()) {
                    JOptionPane.showMessageDialog(this, "User deleted successfully.");
                    loadUsers();
                } else {
                    JOptionPane.showMessageDialog(this, "Failed to delete: " + res.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        loadUsers();
    }

    private void loadUsers() {
        tableModel.setRowCount(0);

        List<UserDTO> users = model.getAllUsers();

        if (users != null) {
            for (UserDTO u : users) {
                tableModel.addRow(new Object[]{u.getUsername(), u.getRole()});
            }
        }
    }
}
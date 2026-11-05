package com.thriftbite.MVC.view.admin;

import com.thriftbite.MVC.model.AdminModel;
import com.thriftbite.MVC.controller.AdminController;
import com.thriftbite.common.TransactionDTO;
import com.thriftbite.common.UserDTO;
import com.thriftbite.common.ItemDTO;
import com.thriftbite.common.Response;
import com.thriftbite.common.XmlUtils;
import com.thriftbite.MVC.view.client.MainFrameLauncher;
import com.thriftbite.MVC.view.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.List;

public class AdminDashboardPanel extends JPanel {
    private AdminModel model;
    private AdminController controller;
    private JTabbedPane tabs;

    // UI Models
    private DefaultTableModel usersModel;
    private JTable usersTable;
    private DefaultTableModel txModel;
    private JTable txTable;
    private DefaultTableModel itemsModel;
    private JTable itemsTable;
    private DefaultListModel<String> activeUsersModel;

    public AdminDashboardPanel(AdminModel model) {
        this.model = model;
        this.controller = new AdminController(model);
        setLayout(new BorderLayout());
        setBackground(Theme.BACKGROUND);

        // --- NEW CONSISTENT HEADER ---
        add(createHeaderPanel(), BorderLayout.NORTH);

        // --- CONSISTENT TABS ---
        tabs = new JTabbedPane();
        tabs.setFont(Theme.STANDARD_FONT);
        tabs.setBackground(Theme.BACKGROUND);

        styleTabs(tabs); // Uses exact logic from ConsumerDashboard

        tabs.addTab("User Management", createUserPanel());
        tabs.addTab("Item Moderation", createItemPanel());
        tabs.addTab("System Logs", createTransactionPanel());
        tabs.addTab("Active Sessions", createActiveUsersPanel());

        add(tabs, BorderLayout.CENTER);

        refreshAllData();
    }

    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(Theme.BACKGROUND);
        headerPanel.setBorder(new EmptyBorder(10, 10, 10, 10)); // Matches Consumer spacing

        JLabel lblTitle = new JLabel("Admin Control Center");
        lblTitle.setFont(Theme.HEADER_FONT);
        lblTitle.setForeground(Theme.TEXT_MAIN);

        JButton btnLogout = new JButton("Logout");
        Theme.styleAccentButton(btnLogout);

        btnLogout.addActionListener(e -> {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    "Are you sure you want to log out?",
                    "Confirm Logout",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );

            if (choice == JOptionPane.YES_OPTION) {
                if (controller != null) controller.logout();
                SwingUtilities.getWindowAncestor(this).dispose();
                MainFrameLauncher.main(new String[]{});
            }
        });

        headerPanel.add(lblTitle, BorderLayout.WEST);
        headerPanel.add(btnLogout, BorderLayout.EAST);

        return headerPanel;
    }

    // ==========================================
    // EXACT SAME TABLE STYLING AS CONSUMER
    // ==========================================
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

    // ==========================================
    // TAB: USER MANAGEMENT
    // ==========================================
    private JPanel createUserPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(Theme.BACKGROUND);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        String[] columns = {"Username", "Account Type", "Status"};
        usersModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        usersTable = new JTable(usersModel);
        usersTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        styleTable(usersTable);

        panel.add(new JScrollPane(usersTable), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        bottom.setBackground(Theme.BACKGROUND);

        JButton btnRefresh = new JButton("Refresh");
        Theme.stylePrimaryButton(btnRefresh);
        btnRefresh.addActionListener(e -> refreshAllData());

        JButton btnApprove = new JButton("Approve User");
        Theme.stylePrimaryButton(btnApprove);
        btnApprove.addActionListener(e -> {
            int row = usersTable.getSelectedRow();
            if (row >= 0) {
                String username = (String) usersModel.getValueAt(row, 0);
                String status = (String) usersModel.getValueAt(row, 2);
                if (status.equals("PENDING")) {
                    if (controller.approveUser(username)) {
                        JOptionPane.showMessageDialog(this, username + " has been Approved!");
                        refreshAllData();
                    }
                } else {
                    JOptionPane.showMessageDialog(this, "User is already approved.");
                }
            } else {
                JOptionPane.showMessageDialog(this, "Please select a user first.");
            }
        });

        JButton btnDelete = new JButton("Delete User");
        Theme.styleAccentButton(btnDelete); // Kept Accent for danger actions
        btnDelete.addActionListener(e -> {
            int row = usersTable.getSelectedRow();
            if (row >= 0) {
                String username = (String) usersModel.getValueAt(row, 0);
                if (username.equalsIgnoreCase("admin")) {
                    JOptionPane.showMessageDialog(this, "You cannot delete the master Admin account!", "Security Block", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                int confirm = JOptionPane.showConfirmDialog(this, "PERMANENTLY delete user: " + username + "?", "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (confirm == JOptionPane.YES_OPTION) {
                    Response res = controller.deleteUser(username);
                    if (res.isSuccess()) {
                        refreshAllData();
                    } else {
                        JOptionPane.showMessageDialog(this, "Failed to delete user: " + res.getMessage());
                    }
                }
            } else {
                JOptionPane.showMessageDialog(this, "Please select a user first.");
            }
        });

        bottom.add(btnRefresh);
        bottom.add(btnApprove);
        bottom.add(btnDelete);
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    // ==========================================
    // TAB: ITEM MODERATION
    // ==========================================
    private JPanel createItemPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(Theme.BACKGROUND);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        String[] columns = {"Item ID", "Name", "Owner", "Price", "Status"};
        itemsModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        itemsTable = new JTable(itemsModel);
        itemsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        styleTable(itemsTable);

        panel.add(new JScrollPane(itemsTable), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        bottom.setBackground(Theme.BACKGROUND);

        JButton btnRefresh = new JButton("Refresh");
        Theme.stylePrimaryButton(btnRefresh);
        btnRefresh.addActionListener(e -> refreshAllData());

        JButton btnDelete = new JButton("Force Delete Item");
        Theme.styleAccentButton(btnDelete);
        btnDelete.addActionListener(e -> {
            int row = itemsTable.getSelectedRow();
            if (row >= 0) {
                String itemId = (String) itemsModel.getValueAt(row, 0);
                String itemName = (String) itemsModel.getValueAt(row, 1);

                int confirm = JOptionPane.showConfirmDialog(this, "Force delete item: " + itemName + "?\nThis action cannot be undone.", "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (confirm == JOptionPane.YES_OPTION) {
                    Response res = controller.deleteItem(itemId);
                    if (res.isSuccess()) {
                        refreshAllData();
                    } else {
                        JOptionPane.showMessageDialog(this, "Failed to delete item.");
                    }
                }
            } else {
                JOptionPane.showMessageDialog(this, "Please select an item first.");
            }
        });

        bottom.add(btnRefresh);
        bottom.add(btnDelete);
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    // ==========================================
    // TAB: TRANSACTIONS
    // ==========================================
    private JPanel createTransactionPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(Theme.BACKGROUND);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        top.setBackground(Theme.BACKGROUND);

        JLabel searchLabel = new JLabel("Search User:");
        searchLabel.setFont(Theme.STANDARD_FONT);
        searchLabel.setForeground(Theme.TEXT_MAIN);

        JTextField searchField = new JTextField(20);
        searchField.setFont(Theme.STANDARD_FONT);

        JButton btnRefresh = new JButton("Refresh Logs");
        Theme.stylePrimaryButton(btnRefresh);
        btnRefresh.addActionListener(e -> refreshAllData());

        top.add(searchLabel);
        top.add(searchField);
        top.add(btnRefresh);
        panel.add(top, BorderLayout.NORTH);

        String[] columns = {"Timestamp", "User", "Action", "Details"};
        txModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        txTable = new JTable(txModel);
        styleTable(txTable);

        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(txModel);
        txTable.setRowSorter(sorter);

        // Map Consumer Dashboard logic to the Admin Search feature
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            private void filter() {
                String text = searchField.getText();
                if (text.trim().length() == 0) {
                    sorter.setRowFilter(null);
                } else {
                    sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text, 1));
                }
            }
        });

        panel.add(new JScrollPane(txTable), BorderLayout.CENTER);
        return panel;
    }

    // ==========================================
    // TAB: ACTIVE SESSIONS
    // ==========================================
    private JPanel createActiveUsersPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(Theme.BACKGROUND);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        activeUsersModel = new DefaultListModel<>();
        JList<String> activeUsersList = new JList<>(activeUsersModel);
        activeUsersList.setFont(Theme.STANDARD_FONT);
        activeUsersList.setFixedCellHeight(28); // Match table row height
        activeUsersList.setBackground(Color.WHITE);
        activeUsersList.setForeground(Theme.TEXT_MAIN);
        activeUsersList.setSelectionBackground(Theme.ACCENT);
        activeUsersList.setSelectionForeground(Theme.TEXT_LIGHT);

        panel.add(new JScrollPane(activeUsersList), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        bottom.setBackground(Theme.BACKGROUND);

        JButton btnRefresh = new JButton("Refresh Sessions");
        Theme.stylePrimaryButton(btnRefresh);
        btnRefresh.addActionListener(e -> refreshAllData());

        bottom.add(btnRefresh);
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    // ==========================================
    // EXACT SAME TAB STYLING AS CONSUMER
    // ==========================================
    private void styleTabs(JTabbedPane tabs) {
        tabs.setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI() {
            @Override
            protected void installDefaults() {
                super.installDefaults();
                tabInsets = new Insets(8, 20, 8, 20);
            }

            @Override
            protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (isSelected) {
                    g2.setColor(Theme.PRIMARY);
                } else {
                    g2.setColor(new Color(240, 240, 240));
                }
                g2.fillRect(x, y, w, h);
                g2.dispose();
            }

            @Override
            protected void paintText(Graphics g, int tabPlacement, Font font, FontMetrics metrics, int tabIndex, String title, Rectangle textRect, boolean isSelected) {
                g.setFont(Theme.BUTTON_FONT);
                if (isSelected) {
                    g.setColor(Theme.TEXT_LIGHT);
                } else {
                    g.setColor(Theme.TEXT_MAIN);
                }

                int stringWidth = metrics.stringWidth(title);
                int stringHeight = metrics.getAscent();
                g.drawString(title, textRect.x + (textRect.width - stringWidth) / 2, textRect.y + (textRect.height + stringHeight) / 2 - 2);
            }

            @Override
            protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {}
            @Override
            protected void paintFocusIndicator(Graphics g, int tabPlacement, Rectangle[] rects, int tabIndex, Rectangle iconRect, Rectangle textRect, boolean isSelected) {}

            @Override
            protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
                g.setColor(Theme.PRIMARY);
                int width = tabPane.getWidth();
                int y = tabPane.getBoundsAt(selectedIndex).y + tabPane.getBoundsAt(selectedIndex).height;
                g.fillRect(0, y, width, 3);
            }
        });
    }

    public void refreshAllData() {
        usersModel.setRowCount(0);
        List<UserDTO> users = controller.getAllUsers();
        for (UserDTO u : users) {
            String status = u.isApproved() ? "APPROVED" : "PENDING";
            usersModel.addRow(new Object[]{u.getUsername(), u.getRole().toString(), status});
        }

        itemsModel.setRowCount(0);
        Response itemsRes = controller.getAllItems();
        if (itemsRes.isSuccess() && itemsRes.getPayload() != null) {
            List<ItemDTO> items = XmlUtils.fromXmlList(itemsRes.getPayload(), ItemDTO.class);
            if (items != null) {
                for (ItemDTO item : items) {
                    itemsModel.addRow(new Object[]{
                            item.getId(),
                            item.getName(),
                            item.getOwnerId(),
                            "₱" + item.getPrice(),
                            item.getStatus().toString()
                    });
                }
            }
        }

        txModel.setRowCount(0);
        List<TransactionDTO> txs = controller.getAllTransactions();
        for (TransactionDTO t : txs) {
            txModel.addRow(new Object[]{t.getTimestamp(), t.getUser(), t.getAction(), t.getDetails()});
        }

        activeUsersModel.clear();
        List<String> active = controller.getActiveUsers();
        if (active.isEmpty()) {
            activeUsersModel.addElement("No users currently logged in.");
        } else {
            for (String user : active) {
                activeUsersModel.addElement(">> " + user + " is currently active.");
            }
        }
    }
}
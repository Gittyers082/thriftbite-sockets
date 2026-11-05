package com.thriftbite.MVC.view.client;

import com.thriftbite.MVC.controller.OwnerController;
import com.thriftbite.MVC.view.Theme;
import com.thriftbite.common.ItemDTO;
import com.thriftbite.MVC.model.OwnerModel;
import com.thriftbite.CRUDS.FoodHandlers.*;
import com.thriftbite.common.ItemStatus;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import javax.swing.table.DefaultTableCellRenderer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class OwnerDashboard extends JPanel {

    private JTable itemTable;
    private OwnerController controller;
    private OwnerModel model;
    private JLabel lblExpiryWarning;

    public OwnerDashboard(OwnerModel model) {
        this.model = model;
        this.controller = new OwnerController(model);

        setLayout(new BorderLayout());
        setBackground(Theme.BACKGROUND);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(Theme.STANDARD_FONT);
        tabs.setBackground(Theme.BACKGROUND);

        styleTabs(tabs);
        tabs.addTab("Overview", buildOverviewPanel());
        tabs.addTab("Add Listing", new CreateHandler(model).getMainPanel());
        tabs.addTab("My Listed Items", new ReadHandler(model).getMainPanel());
        tabs.addTab("Reserved / Handover", new UpdateHandler(model).getMainPanel());
        tabs.addTab("Manage Listings", new DeleteHandler(model).getMainPanel());
        tabs.addTab("My Profile", new ProfilePanel(model.getClientModel()));

        add(tabs, BorderLayout.CENTER);
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

    private JPanel buildOverviewPanel() {
        JPanel overview = new JPanel(new BorderLayout(10, 10));
        overview.setBackground(Theme.BACKGROUND);
        overview.setBorder(new EmptyBorder(15, 15, 15, 15));


        JPanel topContainer = new JPanel(new BorderLayout());
        topContainer.setBackground(Theme.BACKGROUND);
        topContainer.setBorder(new EmptyBorder(0, 0, 10, 0));

        JLabel titleLabel = new JLabel("UN SDG 12: Responsible Consumption Overview");
        titleLabel.setFont(Theme.HEADER_FONT);
        titleLabel.setForeground(Theme.PRIMARY);
        topContainer.add(titleLabel, BorderLayout.NORTH);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        actionPanel.setBackground(Theme.BACKGROUND);

        JLabel searchLabel = new JLabel("Search:");
        searchLabel.setFont(Theme.STANDARD_FONT);
        searchLabel.setForeground(Theme.TEXT_MAIN);

        JTextField searchField = new JTextField(15);
        searchField.setFont(Theme.STANDARD_FONT);

        JButton btnSearch = new JButton("Search");
        Theme.stylePrimaryButton(btnSearch);

        JButton btnExpiryAlerts = new JButton("Expiring in 3 days");
        Theme.styleAccentButton(btnExpiryAlerts);

        lblExpiryWarning = new JLabel("");
        lblExpiryWarning.setForeground(Color.RED);
        lblExpiryWarning.setFont(Theme.BUTTON_FONT);

        actionPanel.add(searchLabel);
        actionPanel.add(searchField);
        actionPanel.add(btnSearch);
        actionPanel.add(btnExpiryAlerts);
        actionPanel.add(lblExpiryWarning);

        topContainer.add(actionPanel, BorderLayout.CENTER);
        overview.add(topContainer, BorderLayout.NORTH);

        itemTable = new JTable();
        itemTable.getTableHeader().setReorderingAllowed(false);
        styleTable(itemTable);

        JScrollPane scrollPane = new JScrollPane(itemTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        overview.add(scrollPane, BorderLayout.CENTER);


        btnSearch.addActionListener(e -> {
            String keyword = searchField.getText();
            List<ItemDTO> results = controller.searchItems(keyword);
            updateTable(results);
        });

        btnExpiryAlerts.addActionListener(e -> {
            List<ItemDTO> expiring = controller.getExpiryNotificationsList();
            if (expiring == null || expiring.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No products expiring within 3 days.", "Expiry Alerts", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            DefaultTableModel alertModel = new DefaultTableModel(new Object[]{"ID", "Name", "Qty", "Price", "Expiry"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) { return false; }
            };
            for (ItemDTO item : expiring) {
                alertModel.addRow(new Object[]{ item.getId(), item.getName(), item.getQuantity(), item.getPrice(), item.getExpiryDate() });
            }
            JTable alertTable = new JTable(alertModel);
            styleTable(alertTable); // Theme the popup table too!

            JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Products expiring in 3 days", true);
            dialog.setLayout(new BorderLayout(10, 10));
            dialog.getContentPane().setBackground(Theme.BACKGROUND);

            JScrollPane alertScroll = new JScrollPane(alertTable);
            alertScroll.setBorder(new EmptyBorder(10, 10, 10, 10));
            dialog.add(alertScroll, BorderLayout.CENTER);

            JLabel lblNotify = new JLabel("Notify clients: these items expire soon.");
            lblNotify.setFont(Theme.STANDARD_FONT);
            lblNotify.setBorder(new EmptyBorder(0, 10, 10, 10));
            dialog.add(lblNotify, BorderLayout.SOUTH);

            dialog.setSize(550, 350);
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);
        });

        loadItems();

        return overview;
    }

    private void loadItems() {
        List<ItemDTO> items = controller.loadItems();
        updateTable(items);
    }

    private void updateTable(List<ItemDTO> items) {
        DefaultTableModel tableModel = new DefaultTableModel(
                new Object[]{"ID", "Name", "Orig. Price", "Curr. Price", "Qty", "Status", "Buyer", "Expiry"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        boolean hasItemsExpiringSoon = false;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        LocalDateTime now = LocalDateTime.now();

        if (items != null && !items.isEmpty()) {
            for (ItemDTO item : items) {
                String buyer = (item.getBuyerName() != null && !item.getBuyerName().trim().isEmpty())
                        ? item.getBuyerName() : "None";

                String expiryStr = (item.getExpiryDate() != null) ? item.getExpiryDate() : "N/A";

                tableModel.addRow(new Object[]{
                        item.getId(),
                        item.getName(),
                        item.getOriginalPrice(),
                        item.getPrice(),
                        item.getQuantity(),
                        item.getStatus().name(),
                        buyer,
                        expiryStr
                });

                if (item.getStatus() == ItemStatus.AVAILABLE && !"N/A".equals(expiryStr)) {
                    try {
                        LocalDateTime expiry = LocalDateTime.parse(expiryStr, formatter);
                        if (expiry.isAfter(now) && expiry.isBefore(now.plusHours(24))) {
                            hasItemsExpiringSoon = true;
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        itemTable.setModel(tableModel);
        itemTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        if (hasItemsExpiringSoon) {
            lblExpiryWarning.setText(" ⚠️ ALERT: You have items expiring within 24 hours!");
        } else {
            lblExpiryWarning.setText("");
        }

        itemTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                if (isSelected) {
                    c.setBackground(table.getSelectionBackground());
                    c.setForeground(table.getSelectionForeground());
                    return c;
                }

                String status = (String) table.getModel().getValueAt(row, 5);
                String expiryStr = (String) table.getModel().getValueAt(row, 7);

                c.setBackground(Color.WHITE);
                c.setForeground(Theme.TEXT_MAIN);

                if (!"AVAILABLE".equals(status)) {
                    c.setBackground(new Color(230, 230, 230));
                    c.setForeground(Color.GRAY);
                }
                else if (!"N/A".equals(expiryStr)) {
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
            protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {

            }

            @Override
            protected void paintFocusIndicator(Graphics g, int tabPlacement, Rectangle[] rects, int tabIndex, Rectangle iconRect, Rectangle textRect, boolean isSelected) {

            }

            @Override
            protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {

                g.setColor(Theme.PRIMARY);
                int width = tabPane.getWidth();
                int y = tabPane.getBoundsAt(selectedIndex).y + tabPane.getBoundsAt(selectedIndex).height;
                g.fillRect(0, y, width, 3);
            }
        });
    }
}
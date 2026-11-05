package com.thriftbite.MVC.view.client;

import com.thriftbite.MVC.controller.ConsumerController;
import com.thriftbite.MVC.model.ClientModel;
import com.thriftbite.MVC.view.Theme;
import com.thriftbite.common.CartItemViewDTO;
import com.thriftbite.common.ItemDTO;
import com.thriftbite.MVC.model.ConsumerModel;
import com.thriftbite.common.Response;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class ConsumerDashboard extends JPanel {

    private JTabbedPane tabs;
    private JTable shopTable;
    private JTextField searchField;
    private JTable cartTable;
    private DefaultTableModel cartModel;
    private JTable ordersTable;
    private DefaultTableModel ordersModel;
    private ConsumerController controller;
    private ConsumerModel model;


    private final java.util.Set<String> shownSoldOutPopups = new java.util.HashSet<>();


    public ConsumerDashboard(ConsumerModel model) {
        this.model = model;
        this.controller = new ConsumerController(model);

        setLayout(new BorderLayout());

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(Theme.STANDARD_FONT);
        tabs.setBackground(Theme.BACKGROUND);

        styleTabs(tabs);
        tabs.addTab("Shop", buildShopTab());
        tabs.addTab("View Cart", buildCartTab());
        tabs.addTab("My Orders", buildOrdersTab());
        tabs.addTab("My Profile", new ProfilePanel(model.getClientModel()));

        add(tabs, BorderLayout.CENTER);

        refreshShop();
        refreshCart();
        refreshOrders();
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

    private JPanel buildShopTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(Theme.BACKGROUND);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        top.setBackground(Theme.BACKGROUND);

        JLabel searchLabel = new JLabel("Search:");
        searchLabel.setFont(Theme.STANDARD_FONT);
        searchLabel.setForeground(Theme.TEXT_MAIN);

        searchField = new JTextField(20);
        searchField.setFont(Theme.STANDARD_FONT);

        JButton btnSearch = new JButton("Search");
        JButton btnClear = new JButton("Clear");
        JButton btnRefresh = new JButton("Refresh");

        Theme.stylePrimaryButton(btnSearch);
        Theme.stylePrimaryButton(btnClear);
        Theme.stylePrimaryButton(btnRefresh);

        top.add(searchLabel);
        top.add(searchField);
        top.add(btnSearch);
        top.add(btnClear);
        top.add(btnRefresh);

        panel.add(top, BorderLayout.NORTH);

        // center: table
        shopTable = new JTable();
        shopTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        styleTable(shopTable);
        panel.add(new JScrollPane(shopTable), BorderLayout.CENTER);

        // bottom: add to cart
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.setBackground(Theme.BACKGROUND);

        JButton btnAddToCart = new JButton("Add to Cart");
        Theme.styleAccentButton(btnAddToCart); // Make this stand out
        bottom.add(btnAddToCart);
        panel.add(bottom, BorderLayout.SOUTH);

        // actions
        btnRefresh.addActionListener(e -> refreshShop());

        btnSearch.addActionListener(e -> {
            String key = searchField.getText().trim();
            if (key.isEmpty()) {
                refreshShop();
                return;
            }

            List<ItemDTO> all = controller.loadItems();
            java.util.List<ItemDTO> results = new java.util.ArrayList<>();

            String k = key.toLowerCase();
            if (all != null) {
                for (ItemDTO it : all) {
                    String name = (it.getName() == null) ? "" : it.getName().toLowerCase();
                    String id = (it.getId() == null) ? "" : it.getId().toLowerCase();

                    if (name.contains(k) || id.contains(k)) {
                        if (!results.contains(it)) {
                            results.add(it);
                        }
                    }
                }
            }
            updateShopTable(results);
        });

        btnClear.addActionListener(e -> {
            searchField.setText("");
            refreshShop();
        });

        btnAddToCart.addActionListener(e -> addSelectedShopItemToCart());

        return panel;
    }


    private JPanel buildCartTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(Theme.BACKGROUND);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        cartModel = new DefaultTableModel(new Object[]{"Item ID", "Name", "Quantity", "Price", "Expiry"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        cartTable = new JTable(cartModel);
        cartTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        styleTable(cartTable);

        panel.add(new JScrollPane(cartTable), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        bottom.setBackground(Theme.BACKGROUND);

        JButton btnRefresh = new JButton("Refresh");
        JButton btnEdit = new JButton("Edit Qty");
        JButton btnRemove = new JButton("Remove");
        JButton btnCheckout = new JButton("Checkout");

        Theme.stylePrimaryButton(btnRefresh);
        Theme.stylePrimaryButton(btnEdit);
        Theme.stylePrimaryButton(btnRemove);
        Theme.styleAccentButton(btnCheckout); // Highlight the checkout button

        bottom.add(btnRefresh);
        bottom.add(btnEdit);
        bottom.add(btnRemove);
        bottom.add(btnCheckout);

        panel.add(bottom, BorderLayout.SOUTH);

        btnRefresh.addActionListener(e -> refreshCart());
        btnRemove.addActionListener(e -> removeSelectedCartItem());
        btnEdit.addActionListener(e -> editSelectedCartQty());
        btnCheckout.addActionListener(e -> {
            // 1. Prevent checking out an empty cart
            if (cartModel.getRowCount() == 0) {
                JOptionPane.showMessageDialog(this, "Your cart is empty!", "Cart Empty", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // 2. Ask the Consumer for their preferred Pickup Location / Time
            String pickup = JOptionPane.showInputDialog(this,
                    "Enter your preferred Pickup Location & Time:\n(e.g., 'Main Cafeteria at 3:00 PM')",
                    "Checkout",
                    JOptionPane.QUESTION_MESSAGE);

            // 3. If they hit 'Cancel' or leave it blank, abort the checkout
            if (pickup == null || pickup.trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Checkout cancelled. A pickup location is required.", "Cancelled", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // 4. Send the checkout request with the actual pickup location!
            Response res = controller.checkoutCart(pickup.trim());
            JOptionPane.showMessageDialog(this, res.getMessage());

            if (res.isSuccess()) {
                refreshCart();
                refreshShop();
                refreshOrders();
            }
        });

        return panel;
    }

    private JPanel buildOrdersTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(Theme.BACKGROUND);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        ordersModel = new DefaultTableModel(
                new Object[]{"ID", "Name", "Qty", "Price", "Status", "Expiry", "Pickup Location"}, 0
        ) { @Override public boolean isCellEditable(int r, int c) { return false; } };

        ordersTable = new JTable(ordersModel);
        styleTable(ordersTable);
        panel.add(new JScrollPane(ordersTable), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.setBackground(Theme.BACKGROUND);

        JButton btnRefresh = new JButton("Refresh");
        Theme.stylePrimaryButton(btnRefresh);
        bottom.add(btnRefresh);
        panel.add(bottom, BorderLayout.SOUTH);

        btnRefresh.addActionListener(e -> refreshOrders());

        return panel;
    }

    private void updateShopTable(List<ItemDTO> items) {
        DefaultTableModel tm = new DefaultTableModel(
                new Object[]{"ID", "Name", "Orig. Price", "Curr. Price", "Quantity", "Status", "Expiry"}, 0
        ) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        if (items != null) {
            for (ItemDTO item : items) {

                if (!item.getStatus().toString().equals("AVAILABLE") || item.getQuantity()<= 0) {
                    continue;
                }

                String expiryStr = (item.getExpiryDate() != null) ? item.getExpiryDate() : "N/A";

                tm.addRow(new Object[]{
                        item.getId(),
                        item.getName(),
                        item.getOriginalPrice(),
                        item.getPrice(),
                        item.getQuantity(),
                        item.getStatus(),
                        expiryStr
                });
            }
        }
        shopTable.setModel(tm);
    }

    private void refreshShop() {
        List<ItemDTO> items = controller.loadItems();
        updateShopTable(items);
    }

    private void refreshCart() {
        cartModel.setRowCount(0);
        List<CartItemViewDTO> cartItems = controller.getCartItems();
        for (CartItemViewDTO c : cartItems) {
            cartModel.addRow(new Object[]{
                    c.getItemId(),
                    c.getItemName(),
                    c.getQuantity(),
                    c.getPrice(),
                    c.getExpiryDate()
            });
        }
        checkSoldOutInCartPopup(cartItems);
    }

    private void refreshOrders() {
        ordersModel.setRowCount(0);
        List<ItemDTO> orders = controller.getMyOrders();
        if (orders != null) {
            for (ItemDTO item : orders) {
                ordersModel.addRow(new Object[]{
                        item.getId(),
                        item.getName(),
                        item.getQuantity(),
                        item.getPrice(),
                        item.getStatus(),
                        item.getExpiryDate(),
                        item.getPickupLocation()
                });
            }
        }
    }


    private void addSelectedShopItemToCart() {
        int row = shopTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select an item to add to cart.");
            return;
        }

        String itemId = shopTable.getValueAt(row, 0).toString();


        String status = shopTable.getValueAt(row, 5).toString();

        if (!status.equals("AVAILABLE")) {
            JOptionPane.showMessageDialog(this, "Only available items can be added to cart.");
            return;
        }

        int availableQty = 1;
        try {

            availableQty = Integer.parseInt(shopTable.getValueAt(row, 4).toString());
        } catch (Exception ignored) {}

        String qtyStr = JOptionPane.showInputDialog(this, "Quantity to add:", "1");
        if (qtyStr == null) return;

        int qty;
        try {
            qty = Integer.parseInt(qtyStr.trim());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Invalid quantity. Numbers only.");
            return;
        }

        if (qty <= 0) {
            JOptionPane.showMessageDialog(this, "Quantity must be at least 1.");
            return;
        }
        if (qty > availableQty) {
            JOptionPane.showMessageDialog(this, "Max available: " + availableQty);
            return;
        }

        if (controller.addToCart(itemId, qty)) {
            JOptionPane.showMessageDialog(this, "Added to cart.");
            refreshCart();
        } else {
            JOptionPane.showMessageDialog(this, "Could not add to cart.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void removeSelectedCartItem() {
        int row = cartTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select an item first.");
            return;
        }
        String itemId = cartTable.getValueAt(row, 0).toString();
        if (controller.removeFromCart(itemId)) {
            cartModel.removeRow(row);
        }
    }

    private void editSelectedCartQty() {
        int row = cartTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select an item first.");
            return;
        }

        String itemId = cartTable.getValueAt(row, 0).toString();
        int oldQty = Integer.parseInt(cartTable.getValueAt(row, 2).toString());

        String qtyStr = JOptionPane.showInputDialog(this, "New quantity (0 = remove):", String.valueOf(oldQty));
        if (qtyStr == null) return;

        int newQty;
        try { newQty = Integer.parseInt(qtyStr.trim()); }
        catch (Exception ex) { newQty = oldQty; }

        if (newQty < 0) {
            JOptionPane.showMessageDialog(this, "Quantity cannot be negative.");
            return;
        }

        controller.removeFromCart(itemId);
        if (newQty > 0) {
            boolean ok = controller.addToCart(itemId, newQty);
            if (!ok) {
                controller.addToCart(itemId, oldQty);
                JOptionPane.showMessageDialog(this, "Edit failed (maybe stock changed).");
            }
        }

        refreshCart();
        refreshShop();
    }

    private void checkSoldOutInCartPopup(List<CartItemViewDTO> cartItems) {
        List<ItemDTO> items = controller.loadItems();

        for (CartItemViewDTO c : cartItems) {
            ItemDTO match = null;
            if (items != null) {
                for (ItemDTO i : items) {
                    if (i.getId().equals(c.getItemId())) {
                        match = i;
                        break;
                    }
                }
            }

            if (match == null) {
                if (shownSoldOutPopups.add("DEL:" + c.getItemId())) {
                    JOptionPane.showMessageDialog(this,
                            "The item \"" + c.getItemName() + "\" was deleted. Remove it from cart.",
                            "Item Deleted",
                            JOptionPane.WARNING_MESSAGE);
                }
                continue;
            }

            if (!match.getStatus().toString().equals("AVAILABLE") || match.getQuantity() <= 0) {
                if (shownSoldOutPopups.add("SO:" + c.getItemId())) {
                    JOptionPane.showMessageDialog(this,
                            "The item \"" + c.getItemName() + "\" has sold out. Remove it from cart.",
                            "Sold Out",
                            JOptionPane.WARNING_MESSAGE);
                }
            }
        }
    }

    private void logout(ClientModel model) {
        JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(this);
        if (frame == null) return;

        LoginPanel login = new LoginPanel();

        login.addLoginListener(e -> {});
        login.addCreateAccountListener(e -> {});

        frame.setContentPane(login);
        frame.revalidate();
        frame.repaint();
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
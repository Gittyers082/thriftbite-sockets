package com.thriftbite.server;

import com.thriftbite.common.*;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import com.thriftbite.common.TransactionDTO;

public class XMLRepository {
    private static final String USERS_FILE = "users.xml";
    private static final String ITEMS_FILE = "items.xml";
    private static final String CARTS_FILE = "carts.xml";
    private final String TRANSACTIONS_FILE = "transactions.xml";

    // Loads Transactions.
    public synchronized List<TransactionDTO> loadTransactions() {
        File file = new File(TRANSACTIONS_FILE);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        try (XMLDecoder decoder = new XMLDecoder(new BufferedInputStream(new FileInputStream(file)))) {
            Object obj = decoder.readObject();
            if (obj instanceof List) {
                return (List<TransactionDTO>) obj;
            }
        } catch (Exception e) {
            System.err.println("Error reading transactions.xml: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    public synchronized void saveTransactions(List<TransactionDTO> transactions) {
        try (XMLEncoder encoder = new XMLEncoder(new BufferedOutputStream(new FileOutputStream(TRANSACTIONS_FILE)))) {
            encoder.writeObject(transactions);
        } catch (Exception e) {
            System.err.println("Error writing transactions.xml: " + e.getMessage());
        }
    }

    public synchronized void addTransactionLog(TransactionDTO log) {
        List<TransactionDTO> logs = loadTransactions();
        logs.add(log);
        saveTransactions(logs);
    }

    public synchronized void saveUsers(List<UserDTO> users) {
        try (XMLEncoder encoder = new XMLEncoder(new BufferedOutputStream(new FileOutputStream(USERS_FILE)))) {
            encoder.writeObject(users);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    public synchronized List<UserDTO> loadUsers() {
        File file = new File(USERS_FILE);
        if (!file.exists()) {
            System.out.println("[SERVER] users.xml not found at: " + file.getAbsolutePath() + " (using empty list)");
            return new ArrayList<>();
        }
        try (XMLDecoder decoder = new XMLDecoder(new BufferedInputStream(new FileInputStream(USERS_FILE)))) {
            List<UserDTO> loaded = (List<UserDTO>) decoder.readObject();
            System.out.println("[SERVER] Loaded " + loaded.size() + " users from: " + file.getAbsolutePath());
            return loaded;
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public synchronized void saveItems(List<ItemDTO> items) {
        try (XMLEncoder encoder = new XMLEncoder(new BufferedOutputStream(new FileOutputStream(ITEMS_FILE)))) {
            encoder.writeObject(items);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    public synchronized List<ItemDTO> loadItems() {
        File file = new File(ITEMS_FILE);
        if (!file.exists()) return new ArrayList<>();

        try (XMLDecoder decoder = new XMLDecoder(new BufferedInputStream(new FileInputStream(ITEMS_FILE)))) {
            return (List<ItemDTO>)  decoder.readObject();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public synchronized boolean claimItem(String itemId, String userId) {
        List<ItemDTO> allItems = loadItems();
        boolean found = false;

        for (ItemDTO item : allItems) {
            if (item.getId().equals(itemId)) {
                found = true;

                item.setStatus(ItemStatus.RESERVED);
                item.setBuyerName(userId);
                item.setQuantity(0);

                if (userId != null && !userId.isEmpty()) {
                    removeCartEntry(userId, itemId);
                }

                System.out.println("[TRANSACTION] Item " + itemId + " claimed by " + userId);
                break;
            }
        }

        if (found) {
            saveItems(allItems);
        }
        return found;
    }

    public List<ItemDTO> findItemsByName(String name) {
        return loadItems().stream()
                .filter(item -> item.getName() != null &&
                        item.getName().toLowerCase().contains(name.toLowerCase()))
                .collect(Collectors.toList());
    }

    public List<ItemDTO> findItemsByStatus(ItemStatus status) {
        return loadItems().stream()
                .filter(item -> item.getStatus() == status)
                .collect(Collectors.toList());
    }

    public List<ItemDTO> findItemsByOwner(String ownerId) {
        return loadItems().stream()
                .filter(item -> ownerId.equals(item.getOwnerId()))
                .collect(Collectors.toList());
    }

    public List<ItemDTO> findExpiringSoonItems() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        LocalDateTime tomorrow = LocalDateTime.now().plusDays(1);

        return loadItems().stream()
                .filter(item -> {
                    if (item.getStatus() != ItemStatus.AVAILABLE) return false;
                    try {
                        LocalDateTime expiry = LocalDateTime.parse(item.getExpiryDate(), formatter);
                        return tomorrow.isAfter(expiry);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .collect(Collectors.toList());
    }

    public synchronized boolean updateItem(ItemDTO updatedItem) {
        List<ItemDTO> items = loadItems();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getId().equals(updatedItem.getId())) {
                items.set(i, updatedItem);
                saveItems(items);
                return true;
            }
        }
        return false;
    }

    public synchronized boolean deleteItem(String itemId) {
        List<ItemDTO> items = loadItems();
        boolean removed = items.removeIf(item -> item.getId().equals(itemId));
        if (removed) {
            saveItems(items);
        }
        return removed;
    }

    @SuppressWarnings("unchecked")
    public synchronized List<CartEntryDTO> loadCartEntries() {
        File file = new File(CARTS_FILE);
        if (!file.exists()) return new ArrayList<>();
        try (XMLDecoder decoder = new XMLDecoder(new BufferedInputStream(new FileInputStream(CARTS_FILE)))) {
            Object obj = decoder.readObject();
            return obj instanceof List ? (List<CartEntryDTO>) obj : new ArrayList<>();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public synchronized void saveCartEntries(List<CartEntryDTO> entries) {
        try (XMLEncoder encoder = new XMLEncoder(new BufferedOutputStream(new FileOutputStream(CARTS_FILE)))) {
            encoder.writeObject(entries != null ? entries : new ArrayList<>());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized boolean addOrUpdateCartEntry(String userId, String itemId, int quantity) {
        if (quantity <= 0) return removeCartEntry(userId, itemId);

        ItemDTO matchingItem = loadItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElse(null);

        if (matchingItem == null || matchingItem.getStatus() != ItemStatus.AVAILABLE) {
            return false;
        }

        int availableQty = matchingItem.getQuantity();

        List<CartEntryDTO> entries = loadCartEntries();
        for (CartEntryDTO e : entries) {
            if (e.getUserId().equals(userId) && e.getItemId().equals(itemId)) {
                int newTotal = e.getQuantity() + quantity;
                if (newTotal > availableQty) {
                    return false;
                }
                e.setQuantity(newTotal);
                saveCartEntries(entries);
                return true;
            }
        }

        if (quantity > availableQty) {
            return false;
        }

        entries.add(new CartEntryDTO(userId, itemId, quantity));
        saveCartEntries(entries);
        return true;
    }

    public synchronized boolean removeCartEntry(String userId, String itemId) {
        List<CartEntryDTO> entries = loadCartEntries();
        boolean removed = entries.removeIf(e -> e.getUserId().equals(userId) && e.getItemId().equals(itemId));
        if (removed) saveCartEntries(entries);
        return removed;
    }

    public List<CartEntryDTO> getCartEntriesByUser(String userId) {
        return loadCartEntries().stream()
                .filter(e -> e.getUserId().equals(userId))
                .collect(Collectors.toList());
    }

    public List<ItemDTO> findItemsExpiringWithinDaysByOwner(String ownerId, int days) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.plusDays(days);

        return loadItems().stream()
                .filter(item -> ownerId.equals(item.getOwnerId()))
                .filter(item -> {
                    try {
                        LocalDateTime expiry = LocalDateTime.parse(item.getExpiryDate(), formatter);
                        return !expiry.isBefore(now) && !expiry.isAfter(threshold);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .collect(Collectors.toList());
    }

    public synchronized Response checkoutCart(String userId, String pickupLocation) {
        List<CartEntryDTO> cartEntries = getCartEntriesByUser(userId);
        if (cartEntries == null || cartEntries.isEmpty()) {
            return new Response(false, "Cart is empty.", null);
        }

        List<ItemDTO> items = loadItems();
        
        for (CartEntryDTO ce : cartEntries) {
            ItemDTO it = items.stream()
                    .filter(i -> i.getId().equals(ce.getItemId()))
                    .findFirst()
                    .orElse(null);

            if (it == null) {
                return new Response(false, "Item deleted: " + ce.getItemId(), null);
            }
            if (it.getStatus() != ItemStatus.AVAILABLE) {
                return new Response(false, "Item \"" + it.getName() + "\" is no longer available.", null);
            }
            if (it.getQuantity() <= 0) {
                return new Response(false, "Sold out: " + it.getName(), null);
            }
            if (ce.getQuantity() > it.getQuantity()) {
                return new Response(false,
                        "Not enough stock for " + it.getName() + ". Available: " + it.getQuantity(), null);
            }
        }

        // Process the checkout - update original items instead of creating new ones
        for (CartEntryDTO ce : cartEntries) {
            ItemDTO it = items.stream()
                    .filter(i -> i.getId().equals(ce.getItemId()))
                    .findFirst()
                    .orElse(null);

            if (it != null) {
                it.setQuantity(it.getQuantity() - ce.getQuantity());
                
                if (it.getQuantity() == 0) {
                    it.setStatus(ItemStatus.RESERVED);
                }
                
                it.setBuyerName(userId);
                
                ItemDTO orderReference = new ItemDTO();
                orderReference.setId(it.getId()); 
                orderReference.setName(it.getName());
                orderReference.setQuantity(ce.getQuantity());
                orderReference.setPrice(it.getPrice());
                orderReference.setStatus(ItemStatus.RESERVED);
                orderReference.setExpiryDate(it.getExpiryDate());
                orderReference.setPickupLocation(pickupLocation);
                orderReference.setBuyerName(userId);
                orderReference.setOwnerId(it.getOwnerId());

                addOrderForUser(userId, orderReference);
            }
        }

        saveItems(items);
        clearCart(userId);

        return new Response(true, "Checkout successful.", null);
    }

    private synchronized void clearCart(String userId) {
        List<CartEntryDTO> entries = loadCartEntries();
        boolean changed = entries.removeIf(e -> e.getUserId().equals(userId));
        if (changed) saveCartEntries(entries);
    }

    private synchronized void addOrderForUser(String userId, ItemDTO order) {
        List<UserDTO> users = loadUsers();

        for (UserDTO u : users) {
            if (u.getUsername().equals(userId)) {

                List<ItemDTO> orders = u.getOrders();
                if (orders == null) {
                    orders = new ArrayList<>();
                    u.setOrders(orders);
                }

                orders.add(order);
                saveUsers(users);

                return;
            }
        }

        System.out.println("[SERVER] addOrderForUser: user not found: " + userId);
    }


    public synchronized boolean updateUsername(String oldUsername, String newUsername) {
        List<UserDTO> users = loadUsers();

        for (UserDTO u : users) {
            if (u.getUsername().equalsIgnoreCase(newUsername)) {
                return false;
            }
        }

        boolean found = false;
        for (UserDTO u : users) {
            if (u.getUsername().equals(oldUsername)) {
                u.setUsername(newUsername);
                found = true;
                break;
            }
        }
        if (!found) return false;
        saveUsers(users);

        List<ItemDTO> items = loadItems();
        boolean itemsChanged = false;
        for (ItemDTO i : items) {
            if (oldUsername.equals(i.getOwnerId())) {
                i.setOwnerId(newUsername);
                itemsChanged = true;
            }
            if (oldUsername.equals(i.getBuyerName())) {
                i.setBuyerName(newUsername);
                itemsChanged = true;
            }
        }
        if (itemsChanged) saveItems(items);

        List<CartEntryDTO> carts = loadCartEntries();
        boolean cartsChanged = false;
        for (CartEntryDTO c : carts) {
            if (oldUsername.equals(c.getUserId())) {
                c.setUserId(newUsername);
                cartsChanged = true;
            }
        }
        if (cartsChanged) saveCartEntries(carts);

        return true;
    }
}







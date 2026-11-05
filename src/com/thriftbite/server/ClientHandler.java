package com.thriftbite.server;

import com.thriftbite.common.*;
import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private XMLRepository xmlrepo;
    private PrintWriter out;
    private BufferedReader in;
    private ServerMain serverUI;
    private static final Set<String> activeSessions = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private String currentUserSession = null;

    public ClientHandler(Socket socket, XMLRepository xmlrepo, ServerMain serverUI) {
        this.clientSocket = socket;
        this.xmlrepo = xmlrepo;
        this.serverUI = serverUI;
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                Request req = XmlUtils.fromXml(inputLine, Request.class);
                Response res = handleRequest(req);
                String resXml = XmlUtils.toXml(res);
                out.println(resXml);
            }
        } catch (IOException e) {
            System.out.println("Client disconnected.");
        } finally {
            try { clientSocket.close(); } catch (IOException e) {}
            if (currentUserSession != null) {
                activeSessions.remove(currentUserSession);
                logTransaction(currentUserSession, "LOGOUT", "Client disconnected");
            }
        }
    }

    // Logs system activities.
    private void logTransaction(String user, String transaction, String dataAffected) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String logEntry = String.format("[%s] User: %s | Action: %s | Data: %s",
                timestamp, user, transaction, dataAffected);

        if(serverUI != null) {
            serverUI.log(logEntry);
        } else {
            System.out.println(logEntry);
        }

        TransactionDTO newLog = new TransactionDTO();
        newLog.setTimestamp(timestamp);
        newLog.setUser(user);
        newLog.setAction(transaction);
        newLog.setDetails(dataAffected);

        xmlrepo.addTransactionLog(newLog);
    }

    // Processes request, specifically the user action & payload.
    private Response handleRequest(Request req) {
        if (currentUserSession != null) {

            boolean stillExists = xmlrepo.loadUsers()
                    .stream()
                    .anyMatch(u -> u.getUsername().equals(currentUserSession));

            if (!stillExists) {
                activeSessions.remove(currentUserSession);

                return new Response(
                        false,
                        "BANNED",
                        "You have been banned by the admin."
                );
            }
        }
        String action = req.getAction();

        switch (action) {

            case "LOGIN":
                UserDTO loginUser = XmlUtils.fromXml(req.getPayload(), UserDTO.class);
                if (loginUser == null || loginUser.getUsername() == null || loginUser.getPasswordHash() == null) {
                    return new Response(false, "Invalid login request", null);
                }
                String loginUsername = loginUser.getUsername().trim();
                String loginPassword = loginUser.getPasswordHash().trim();

                if (activeSessions.contains(loginUsername)) {
                    return new Response(false, "Account is already logged in on another device!", null);
                }

                List<UserDTO> users = xmlrepo.loadUsers();
                for (UserDTO u : users) {
                    String storedUser = u.getUsername() != null ? u.getUsername().trim() : "";
                    String storedPass = u.getPasswordHash() != null ? u.getPasswordHash() : "";
                    if (storedUser.equals(loginUsername) && storedPass.equals(loginPassword)) {
                        if (!u.isApproved() && !u.getRole().equals("ADMIN")) {
                            return new Response(false, "Your account is pending Admin approval.", null);
                        }
                        currentUserSession = storedUser;
                        activeSessions.add(storedUser);
                        logTransaction(storedUser, "LOGIN", "Successful Login");
                        return new Response(true, "Login Successful", XmlUtils.toXml(u));
                    }
                }
                return new Response(false, "Invalid Credentials", null);

            case "REGISTER":
                UserDTO newUser = XmlUtils.fromXml(req.getPayload(), UserDTO.class);
                if (newUser == null || newUser.getUsername() == null || newUser.getUsername().trim().isEmpty()) {
                    return new Response(false, "Invalid registration request", null);
                }
                String newUsername = newUser.getUsername().trim();
                newUser.setUsername(newUsername);
                if (newUser.getPasswordHash() != null) {
                    newUser.setPasswordHash(newUser.getPasswordHash().trim());
                }
                List<UserDTO> currentUsers = xmlrepo.loadUsers();
                for (UserDTO u : currentUsers) {
                    String existing = u.getUsername() != null ? u.getUsername().trim() : "";
                    if (existing.equalsIgnoreCase(newUsername))
                        return new Response(false, "Username Already Exists!", null);
                }
                newUser.setApproved(false);
                currentUsers.add(newUser);
                xmlrepo.saveUsers(currentUsers);
                logTransaction(newUsername, "REGISTER", "Created new account");
                return new Response(true, "Registration Successful!", null);

            case "GET_USERS":
                List<UserDTO> allUsers = xmlrepo.loadUsers();
                return new Response(true, "Users Loaded", XmlUtils.toXml(allUsers));

            case "GET_ITEMS":
                List<ItemDTO> items = xmlrepo.loadItems();
                updateExpiringStatus(items);
                return new Response(true, "items Loaded", XmlUtils.toXml(items));

            case "DELETE_USER": {
                String delUsername = req.getPayload();

                if (delUsername == null || delUsername.trim().isEmpty()) {
                    return new Response(false, "User not specified", null);
                }

                final String targetUsername = delUsername.trim();

                if (delUsername.equalsIgnoreCase("godmode")) {
                    return new Response(false, "Cannot delete the default admin", null);
                }

                List<UserDTO> usersToDeleteFrom = xmlrepo.loadUsers();

                boolean userRemoved = usersToDeleteFrom.removeIf(u -> {
                    String name = (u.getUsername() != null) ? u.getUsername().trim() : "";
                    return name.equalsIgnoreCase(delUsername);
                });

                if (userRemoved) {
                    xmlrepo.saveUsers(usersToDeleteFrom);

                    // ✅ delete all items owned by this user (rename to avoid conflict)
                    List<ItemDTO> allItems = xmlrepo.loadItems();
                    allItems.removeIf(item ->
                            item.getOwnerId() != null &&
                                    item.getOwnerId().equalsIgnoreCase(delUsername)
                    );
                    xmlrepo.saveItems(allItems);

                    logTransaction("ADMIN", "DELETE_USER", "Deleted username: " + delUsername);
                    return new Response(true, "User deleted successfully", null);
                } else {
                    return new Response(false, "User not found", null);
                }
            }


            case "ADD_ITEM":
                ItemDTO newItem = XmlUtils.fromXml(req.getPayload(), ItemDTO.class);
                newItem.setId(UUID.randomUUID().toString());
                newItem.setStatus(ItemStatus.AVAILABLE);

                List<ItemDTO> currentItems = xmlrepo.loadItems();
                currentItems.add(newItem);
                xmlrepo.saveItems(currentItems);
                logTransaction(newItem.getOwnerId(), "CREATE_ITEM", "Item Name: " + newItem.getName() + ", Price: " + newItem.getPrice());
                return new Response(true, "Item Added to PlateShare", null);

            case "CLAIM_ITEM": {
                String payload = req.getPayload();

                CartEntryDTO claimEntry = null;
                try {
                    claimEntry = XmlUtils.fromXml(payload, CartEntryDTO.class);
                } catch (Exception ignored) {
                }

                String claimUserId;
                String claimItemId;

                if (claimEntry != null && claimEntry.getItemId() != null) {
                    claimUserId = (claimEntry.getUserId() != null && !claimEntry.getUserId().trim().isEmpty())
                            ? claimEntry.getUserId().trim()
                            : "UnknownBuyer";
                    claimItemId = claimEntry.getItemId();
                } else {
                    claimUserId = "UnknownBuyer";
                    claimItemId = payload;
                }
                
                List<ItemDTO> allItems = xmlrepo.loadItems();
                ItemDTO targetItem = null;
                for (ItemDTO item : allItems) {
                    if (item.getId().equals(claimItemId)) {
                        targetItem = item;
                        break;
                    }
                }

                if (targetItem == null) {
                    return new Response(false, "Item not found.", null);
                }

                if (targetItem.getStatus() != ItemStatus.AVAILABLE || targetItem.getQuantity() <= 0) {
                    return new Response(false, "Item is no longer available for claiming.", null);
                }

                boolean success = xmlrepo.claimItem(claimItemId, claimUserId);

                if (success) {
                    logTransaction(claimUserId, "CLAIM_ITEM", "Claimed Item ID: " + claimItemId);
                    return new Response(true, "Item claimed! Please wait for pickup confirmation.", null);
                } else {
                    return new Response(false, "Item unavailable or already taken.", null);
                }
            }

            case "SEARCH_ITEMS_BY_NAME":
                String searchTerm = req.getPayload();
                List<ItemDTO> allItems = xmlrepo.loadItems();

                List<ItemDTO> nameMatches = allItems.stream()
                        .filter(item -> item.getName() != null &&
                                item.getName().toLowerCase().contains(searchTerm.toLowerCase()))
                        .collect(Collectors.toList());

                updateExpiringStatus(nameMatches);

                System.out.println("[SEARCH] User searched for items by name: " + searchTerm + " - Found: " + nameMatches.size());
                return new Response(true, "Search by name completed",
                        XmlUtils.toXml(nameMatches));

            case "SEARCH_ITEMS_BY_STATUS":
                try {
                    ItemStatus status = ItemStatus.valueOf(req.getPayload());
                    List<ItemDTO> itemsByStatus = xmlrepo.loadItems().stream()
                            .filter(item -> item.getStatus() == status)
                            .collect(Collectors.toList());

                    updateExpiringStatus(itemsByStatus);

                    System.out.println("[SEARCH] User searched for items by status: " + status + " - Found: " + itemsByStatus.size());
                    return new Response(true, "Search by status completed",
                            XmlUtils.toXml(itemsByStatus));
                } catch (IllegalArgumentException e) {
                    return new Response(false, "Invalid status", null);
                }

            case "SEARCH_ITEMS_BY_OWNER":
                String ownerId = req.getPayload();
                List<ItemDTO> ownerItems = xmlrepo.loadItems().stream()
                        .filter(item -> ownerId.equals(item.getOwnerId()))
                        .collect(Collectors.toList());

                updateExpiringStatus(ownerItems);

                System.out.println("[SEARCH] User searched for items by owner: " + ownerId + " - Found: " + ownerItems.size());
                return new Response(true, "Search by owner completed",
                        XmlUtils.toXml(ownerItems));

            case "ADVANCED_SEARCH":
                String criteriaXml = req.getPayload();
                String name = extractXmlValue(criteriaXml, "name");
                String minPriceStr = extractXmlValue(criteriaXml, "minPrice");
                String maxPriceStr = extractXmlValue(criteriaXml, "maxPrice");
                String statusStr = extractXmlValue(criteriaXml, "status");
                String expiringSoonStr = extractXmlValue(criteriaXml, "expiringSoon");

                double minPrice = minPriceStr.isEmpty() ? -1 : Double.parseDouble(minPriceStr);
                double maxPrice = maxPriceStr.isEmpty() ? -1 : Double.parseDouble(maxPriceStr);
                boolean expiringSoon = Boolean.parseBoolean(expiringSoonStr);

                List<ItemDTO> filteredItems = xmlrepo.loadItems();

                if (!name.isEmpty()) {
                    filteredItems = filteredItems.stream()
                            .filter(item -> item.getName() != null &&
                                    item.getName().toLowerCase().contains(name.toLowerCase()))
                            .collect(Collectors.toList());
                }

                if (minPrice >= 0) {
                    filteredItems = filteredItems.stream()
                            .filter(item -> item.getPrice() >= minPrice)
                            .collect(Collectors.toList());
                }

                if (maxPrice >= 0) {
                    filteredItems = filteredItems.stream()
                            .filter(item -> item.getPrice() <= maxPrice)
                            .collect(Collectors.toList());
                }

                if (!statusStr.isEmpty()) {
                    try {
                        ItemStatus statusFilter = ItemStatus.valueOf(statusStr);
                        filteredItems = filteredItems.stream()
                                .filter(item -> item.getStatus() == statusFilter)
                                .collect(Collectors.toList());
                    } catch (IllegalArgumentException e) {

                    }
                }

                if (expiringSoon) {
                    filteredItems = filteredItems.stream()
                            .filter(ItemDTO::isExpiringSoon)
                            .collect(Collectors.toList());
                }

                updateExpiringStatus(filteredItems);

                System.out.println("[SEARCH] User performed advanced search - Found: " + filteredItems.size());
                return new Response(true, "Advanced search completed",
                        XmlUtils.toXml(filteredItems));

            case "UPDATE_ITEM":
                ItemDTO updatedItem = XmlUtils.fromXml(req.getPayload(), ItemDTO.class);
                List<ItemDTO> currentItemsList = xmlrepo.loadItems();
                boolean found = false;

                for (int i = 0; i < currentItemsList.size(); i++) {
                    if (currentItemsList.get(i).getId().equals(updatedItem.getId())) {

                        if (currentItemsList.get(i).getOwnerId().equals(updatedItem.getOwnerId())) {
                            currentItemsList.set(i, updatedItem);
                            found = true;
                        } else {
                            return new Response(false, "You don't have permission to update this item", null);
                        }
                        break;
                    }
                }

                if (found) {
                    xmlrepo.saveItems(currentItemsList);
                    String updateOwner = updatedItem.getOwnerId() != null ? updatedItem.getOwnerId() : "UnknownOwner";
                    logTransaction(updateOwner, "UPDATE_ITEM", "Updated Item ID: " + updatedItem.getId());
                    return new Response(true, "Item updated successfully", null);
                } else {
                    return new Response(false, "Item not found", null);
                }

            case "DELETE_ITEM":
                String deleteItemId = req.getPayload();
                List<ItemDTO> allCurrentItems = xmlrepo.loadItems();
                boolean removed = allCurrentItems.removeIf(item -> {
                    if (item.getId().equals(deleteItemId)) {

                        return true;
                    }
                    return false;
                });

                if (removed) {
                    xmlrepo.saveItems(allCurrentItems);
                    logTransaction("System/User", "DELETE_ITEM", "Deleted Item ID: " + deleteItemId);

                    return new Response(true, "Item deleted successfully", null);
                } else {
                    return new Response(false, "Item not found or couldn't be deleted", null);
                }

            case "ADD_TO_CART":
                CartEntryDTO addEntry = XmlUtils.fromXml(req.getPayload(), CartEntryDTO.class);
                if (addEntry == null || addEntry.getQuantity() <= 0) {
                    return new Response(false, "Invalid cart entry", null);
                }
                boolean cartUpdated = xmlrepo.addOrUpdateCartEntry(addEntry.getUserId(), addEntry.getItemId(), addEntry.getQuantity());
                if (!cartUpdated) {
                    return new Response(false, "Requested quantity exceeds available stock or item is unavailable.", null);
                }
                logTransaction(addEntry.getUserId(), "ADD_TO_CART", "ItemId: " + addEntry.getItemId() + ", Qty: " + addEntry.getQuantity());
                return new Response(true, "Added to cart", null);

            case "REMOVE_FROM_CART":
                CartEntryDTO removeEntry = XmlUtils.fromXml(req.getPayload(), CartEntryDTO.class);
                if (removeEntry == null) {
                    return new Response(false, "Invalid cart entry", null);
                }
                boolean removedFromCart = xmlrepo.removeCartEntry(removeEntry.getUserId(), removeEntry.getItemId());
                if (removedFromCart) {
                    logTransaction(removeEntry.getUserId(), "REMOVE_FROM_CART", "ItemId: " + removeEntry.getItemId());
                    return new Response(true, "Removed from cart", null);
                }
                return new Response(false, "Item not in cart", null);

            case "GET_CART":
                String cartUserId = req.getPayload();
                if (cartUserId == null || cartUserId.isEmpty()) {
                    return new Response(false, "User not specified", null);
                }
                List<CartEntryDTO> userCartEntries = xmlrepo.getCartEntriesByUser(cartUserId);
                List<ItemDTO> allItemsForCart = xmlrepo.loadItems();
                List<CartItemViewDTO> cartViews = new ArrayList<>();
                for (CartEntryDTO ce : userCartEntries) {
                    ItemDTO item = allItemsForCart.stream()
                            .filter(i -> i.getId().equals(ce.getItemId()))
                            .findFirst()
                            .orElse(null);
                    if (item != null && item.getStatus() == ItemStatus.AVAILABLE && item.getQuantity() > 0) {
                        cartViews.add(new CartItemViewDTO(item.getId(), item.getName(), ce.getQuantity(), item.getPrice(), item.getExpiryDate()));
                    }
                }
                CartViewWrapper cartWrapper = new CartViewWrapper();
                cartWrapper.setItems(cartViews);
                return new Response(true, "Cart loaded", XmlUtils.toXml(cartWrapper));

            case "CHECKOUT_CART": {
                CheckoutRequestDTO checkout = XmlUtils.fromXml(req.getPayload(), CheckoutRequestDTO.class);
                if (checkout == null || checkout.getUserId() == null || checkout.getUserId().trim().isEmpty()) {
                    return new Response(false, "User not specified", null);
                }

                String userId = checkout.getUserId().trim();
                String pickup = checkout.getPickupLocation();

                Response res = xmlrepo.checkoutCart(userId, pickup);

                if (res != null && res.isSuccess()) {
                    logTransaction(userId, "CHECKOUT_CART", "Pickup: " + pickup);
                }
                return res;
            }


            case "GET_EXPIRY_NOTIFICATIONS":
                String ownerUsername = req.getPayload();
                if (ownerUsername == null || ownerUsername.isEmpty()) {
                    return new Response(false, "User not specified", null);
                }
                List<ItemDTO> expiringItems = xmlrepo.findItemsExpiringWithinDaysByOwner(ownerUsername, 3);
                ItemListWrapper expiryWrapper = new ItemListWrapper();
                expiryWrapper.setItems(expiringItems);
                logTransaction(ownerUsername, "GET_EXPIRY_NOTIFICATIONS", "Items expiring in 3 days: " + expiringItems.size());
                return new Response(true, "Expiry notifications", XmlUtils.toXml(expiryWrapper));

            case "CONFIRM_HANDOVER": {
                String handoverItemId = req.getPayload();
                if (handoverItemId == null || handoverItemId.trim().isEmpty()) {
                    return new Response(false, "Item not specified", null);
                }

                List<ItemDTO> itemsForHandover = xmlrepo.loadItems();
                boolean handoverFound = false;
                String buyerId = null;

                for (ItemDTO item : itemsForHandover) {
                    if (item.getId().equals(handoverItemId.trim())) {
                        if (item.getStatus() == ItemStatus.RESERVED || item.getStatus() == ItemStatus.PAID) {
                            item.setStatus(ItemStatus.PAID);
                            buyerId = item.getBuyerName();
                            xmlrepo.saveItems(itemsForHandover);
                            logTransaction(item.getOwnerId() != null ? item.getOwnerId() : "Unknown", "CONFIRM_HANDOVER", "Item ID: " + handoverItemId);
                            handoverFound = true;
                        } else {
                            return new Response(false, "Item must be reserved or paid to mark as ready for pickup", null);
                        }
                        break;
                    }
                }

                if (handoverFound && buyerId != null) {
                    List<UserDTO> usersList = xmlrepo.loadUsers();
                    for (UserDTO u : usersList) {
                        if (u.getUsername().equals(buyerId)) {
                            List<ItemDTO> userOrders = u.getOrders();
                            if (userOrders != null) {
                                for (ItemDTO o : userOrders) {
                                    if (o.getId().equals(handoverItemId.trim())) {
                                        o.setStatus(ItemStatus.PAID);
                                        break;
                                    }
                                }
                            }
                            xmlrepo.saveUsers(usersList);
                            break;
                        }
                    }
                    return new Response(true, "Item marked as ready for pickup", null);
                }

                if (handoverFound) {
                    return new Response(true, "Item marked as ready for pickup", null);
                } else {
                    return new Response(false, "Item not found", null);
                }
            }

            case "GET_MY_ORDERS": {
                String buyerId = req.getPayload();
                if (buyerId == null || buyerId.trim().isEmpty()) {
                    return new Response(false, "User not specified", null);
                }
                String trimmedBuyer = buyerId.trim();

                List<UserDTO> usersList = xmlrepo.loadUsers();
                for (UserDTO u : usersList) {
                    if (u.getUsername().equals(trimmedBuyer)) {
                        List<ItemDTO> orders = u.getOrders();
                        if (orders == null) orders = new ArrayList<>();
                        return new Response(true, "Orders loaded", XmlUtils.toXml(orders));
                    }
                }
                return new Response(true, "Orders loaded", XmlUtils.toXml(new ArrayList<ItemDTO>()));
            }

            case "UPDATE_PASSWORD":
                UserDTO updatedUser = XmlUtils.fromXml(req.getPayload(), UserDTO.class);
                List<UserDTO> updatePassUsersList = xmlrepo.loadUsers();
                boolean passwordUpdated = false;

                for (UserDTO u : updatePassUsersList) {
                    if (u.getUsername().equals(updatedUser.getUsername())) {
                        u.setPasswordHash(updatedUser.getPasswordHash());
                        passwordUpdated = true;
                        break;
                    }
                }

                if (passwordUpdated) {
                    xmlrepo.saveUsers(updatePassUsersList);
                    logTransaction(updatedUser.getUsername(), "UPDATE_PASSWORD", "Changed password");
                    return new Response(true, "Password updated successfully", null);
                } else {
                    return new Response(false, "User not found", null);
                }

            case "UPDATE_USERNAME":

                String[] parts = req.getPayload().split("::");
                if (parts.length != 2) return new Response(false, "Invalid format", null);

                String oldName = parts[0];
                String newName = parts[1].trim();

                if (newName.isEmpty()) return new Response(false, "Username cannot be empty", null);

                boolean nameSuccess = xmlrepo.updateUsername(oldName, newName);
                if (nameSuccess) {
                    logTransaction(oldName, "UPDATE_USERNAME", "Changed name to: " + newName);
                    return new Response(true, "Username updated successfully", null);
                } else {
                    return new Response(false, "Username is already taken!", null);
                }

            case "APPROVE_USER":
                String userToApprove = req.getPayload().trim();
                List<UserDTO> approvalList = xmlrepo.loadUsers();
                for (UserDTO u : approvalList) {
                    if (u.getUsername().equals(userToApprove)) {
                        u.setApproved(true);
                        xmlrepo.saveUsers(approvalList);
                        logTransaction("ADMIN", "APPROVE_USER", "Approved account: " + userToApprove);
                        return new Response(true, "User " + userToApprove + " has been approved!", null);
                    }
                }
                return new Response(false, "User not found.", null);

            case "GET_ACTIVE_USERS":
                String activeListStr = String.join(",", activeSessions);
                return new Response(true, "Active Users Loaded", activeListStr);

            case "GET_ALL_TRANSACTIONS":
                List<TransactionDTO> allLogs = xmlrepo.loadTransactions();
                return new Response(true, "Logs loaded", XmlUtils.toXml(allLogs));

            default:
                return new Response(false, "Unknown Action: " + action, null);
        }

    }

    private void updateExpiringStatus(List<ItemDTO> items) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.plusDays(3);

        for (ItemDTO item : items) {
            if (item.getStatus() == ItemStatus.AVAILABLE) {
                try {
                    LocalDateTime itemExpiry = LocalDateTime.parse(item.getExpiryDate(), formatter);
                    item.setExpiringSoon(!itemExpiry.isBefore(now) && !itemExpiry.isAfter(threshold));
                } catch (Exception e) {
                }
            }
        }
    }

    private String extractXmlValue(String xml, String tag) {
        String openTag = "<" + tag + ">";
        String closeTag = "</" + tag + ">";
        int start = xml.indexOf(openTag);
        int end = xml.indexOf(closeTag);

        if (start >= 0 && end > start) {
            return xml.substring(start + openTag.length(), end);
        }
        return "";
    }

}
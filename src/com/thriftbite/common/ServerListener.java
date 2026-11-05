package com.thriftbite.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import javax.swing.JOptionPane;

public class ServerListener extends Thread {
    private Socket socket;
    private BufferedReader in;
    private boolean running;
    private final String host;
    private final int port;
    
    public ServerListener(String host, int port) {
        this.host = host;
        this.port = port;
        this.running = true;
    }
    
    @Override
    public void run() {
        try {
            // Connect to the server on a separate socket for notifications
            socket = new Socket(host, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            // Send a request to register for notifications
            // This would be implemented on the server side
            
            // Listen for notifications from the server
            while (running) {
                String message = in.readLine();
                if (message != null) {
                    // Parse the notification
                    Response notification = XmlUtils.fromXml(message, Response.class);
                    
                    // Handle different types of notifications
                    if (notification != null) {
                        handleNotification(notification);
                    }
                }
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("Error in server listener: " + e.getMessage());
                // Try to reconnect after a delay
                try {
                    Thread.sleep(5000);
                    run();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        } finally {
            closeConnection();
        }
    }
    
    private void handleNotification(Response notification) {
        // Handle different types of notifications based on the action
        String action = notification.getMessage();
        
        if ("ITEM_EXPIRED".equals(action)) {
            // An item has expired
            String itemId = notification.getPayload();
            showNotification("Item Expired", "The item you claimed has expired: " + itemId);
        } else if ("ITEM_CLAIMED".equals(action)) {
            // An item has been claimed by someone else
            String itemId = notification.getPayload();
            showNotification("Item Claimed", "An item you were viewing has been claimed: " + itemId);
        }
        
        // Trigger a refresh of the UI if needed
        // This would require a callback mechanism to the UI components
    }
    
    private void showNotification(String title, String message) {
        // Show a notification to the user
        // This is a simple implementation using JOptionPane
        // In a real application, you might want to use a more sophisticated notification system
        JOptionPane.showMessageDialog(null, message, title, JOptionPane.INFORMATION_MESSAGE);
    }
    
    public void stopListening() {
        running = false;
        closeConnection();
    }
    
    private void closeConnection() {
        try {
            if (in != null) in.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }
    }
}
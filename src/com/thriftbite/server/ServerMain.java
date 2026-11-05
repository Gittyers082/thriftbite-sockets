package com.thriftbite.server;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

public class ServerMain extends JFrame {
    private static final int PORT = 16767;

    private JTextArea consoleArea;
    private JButton btnStart;
    private JButton btnStop;

    private ServerSocket serverSocket;
    private boolean isRunning = false;
    private XMLRepository sharedRepo;

    private final java.util.List<Socket> activeClients = new java.util.concurrent.CopyOnWriteArrayList<>();

    public ServerMain() {
        setTitle("ThriftBite - Server Control Panel");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        consoleArea = new JTextArea();
        consoleArea.setEditable(false);
        consoleArea.setBackground(new Color(30, 30, 30));
        consoleArea.setForeground(new Color(0, 255, 0));

        DefaultCaret caret = (DefaultCaret) consoleArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        JScrollPane scrollPane = new JScrollPane(consoleArea);
        add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setBackground(Color.DARK_GRAY);

        btnStart = new JButton("▶ START SERVER");
        btnStart.setBackground(new Color(40, 167, 69));
        btnStart.setForeground(Color.WHITE);
        btnStart.setFocusPainted(false);

        btnStop = new JButton("⏹ STOP SERVER");
        btnStop.setBackground(new Color(220, 53, 69));
        btnStop.setForeground(Color.WHITE);
        btnStop.setEnabled(false);
        btnStop.setFocusPainted(false);

        buttonPanel.add(btnStart);
        buttonPanel.add(btnStop);
        add(buttonPanel, BorderLayout.SOUTH);

        btnStart.addActionListener(e -> startServer());
        btnStop.addActionListener(e -> stopServer());
    }

    public void log(String message) {
        SwingUtilities.invokeLater(() -> {
            consoleArea.append(message + "\n");
        });
        System.out.println(message);
    }

    private void startServer() {
        btnStart.setEnabled(false);
        btnStop.setEnabled(true);
        isRunning = true;

        log("---------------------------------------");
        log("   ThriftBite Server Starting...");
        log("---------------------------------------");

        // Multithreading, everytime a user logs in it creates a thread that contains the ClientHandler.java for each user.
        new Thread(() -> {
            try {
                sharedRepo = new XMLRepository();
                int userCount = sharedRepo.loadUsers().size();
                int itemCount = sharedRepo.loadItems().size();
                log("[INIT] Database Connected. Loaded " + userCount + " users and " + itemCount + " items.");

                serverSocket = new ServerSocket(PORT);
                log("[READY] Server is listening on Port " + PORT);
                log("[INFO] Waiting for client connections...");

                while (isRunning) {
                    Socket clientSocket = serverSocket.accept();
                    log("[NEW CONNECTION] Client joined: " + clientSocket.getInetAddress());

                    activeClients.add(clientSocket);

                    ClientHandler handler = new ClientHandler(clientSocket, sharedRepo, this);
                    new Thread(handler).start();
                }
            } catch (SocketException e) {
                log("[INFO] Server socket closed.");
            } catch (IOException e) {
                log("[ERROR] Server crashed: " + e.getMessage());
            } finally {
                log("---------------------------------------");
                log("   Server Offline.");
                log("---------------------------------------");
            }
        }).start();
    }

    private void stopServer() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }

            for (Socket client : activeClients) {
                if (client != null && !client.isClosed()) {
                    client.close();
                }
            }
        } catch (IOException e) {
            log("[ERROR] Could not close server: " + e.getMessage());
        }
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ServerMain serverWindow = new ServerMain();
            serverWindow.setVisible(true);
        });
    }
}
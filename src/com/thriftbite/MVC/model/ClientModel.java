package com.thriftbite.MVC.model;

import com.thriftbite.common.*;
import java.io.*;
import java.net.Socket;

public class ClientModel {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private UserDTO currentUser;
    private boolean connected;

    public ClientModel() {
        this.connected = false;
    }

    public boolean connect(String ip, int port) {
        try {
            socket = new Socket(ip, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            connected = true;
            System.out.println("[MODEL] Connected to server at " + ip + ":" + port);
            return true;
        } catch (IOException e) {
            System.err.println("[MODEL] Connection failed: " + e.getMessage());
            connected = false;
            return false;
        }
    }

    public synchronized Response sendRequest(Request req) {
        if (!connected || socket == null || socket.isClosed()) {
            return new Response(false, "Not connected to server", null);
        }

        try {
            String reqXml = XmlUtils.toXml(req);
            out.println(reqXml);

            String responseLine = in.readLine();
            if (responseLine != null) {
                Response res = XmlUtils.fromXml(responseLine, Response.class);

                if (res != null && "BANNED".equals(res.getMessage())) {
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        javax.swing.JOptionPane.showMessageDialog(null,
                                res.getPayload() != null ? res.getPayload() : "You have been banned.",
                                "Access Revoked", javax.swing.JOptionPane.ERROR_MESSAGE);
                        System.exit(0);
                    });
                }
                return res;
            } else {
                return new Response(false, "Empty response from server", null);
            }
        } catch (IOException e) {
            return new Response(false, "Communication error: " + e.getMessage(), null);
        }
    }

    public void disconnect() {
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) { }
        finally {
            connected = false;
            currentUser = null;
        }
    }

    public Response login(String username, String password) {
        UserDTO loginUser = new UserDTO(username.trim(), password.trim(), null);
        Response res = sendRequest(new Request("LOGIN", XmlUtils.toXml(loginUser)));
        if (res.isSuccess() && res.getPayload() != null) {
            currentUser = XmlUtils.fromXml(res.getPayload(), UserDTO.class);
        }
        return res;
    }

    public Response register(String username, String password, Role role) {
        UserDTO newUser = new UserDTO(username.trim(), password.trim(), role);
        return sendRequest(new Request("REGISTER", XmlUtils.toXml(newUser)));
    }

    public void logout() {
        currentUser = null;
    }

    public UserDTO getCurrentUser() { return currentUser; }
    public boolean isConnected() { return connected && socket != null && !socket.isClosed(); }

    public Response getAllUsers() { return sendRequest(new Request("GET_USERS", "")); }
    public Response getAllItems() { return sendRequest(new Request("GET_ITEMS", "")); }

    public Response updateUsername(String newUsername) {
        if (currentUser == null) return new Response(false, "Not logged in", null);

        String oldUsername = currentUser.getUsername();
        if (oldUsername.equals(newUsername.trim())) {
            return new Response(true, "Username is the same", null);
        }

        Request req = new Request("UPDATE_USERNAME", oldUsername + "::" + newUsername.trim());
        Response res = sendRequest(req);

        if (res.isSuccess()) {
            currentUser.setUsername(newUsername.trim());
        }
        return res;
    }

    public Response updatePassword(String newPassword) {
        if (currentUser == null) return new Response(false, "Not logged in", null);
        currentUser.setPasswordHash(newPassword.trim());
        Request req = new Request("UPDATE_PASSWORD", XmlUtils.toXml(currentUser));
        return sendRequest(req);
    }
}
package com.thriftbite.common;

import java.io.*;
import java.net.Socket;

public class ClientSocketManager {
    private static ClientSocketManager instance;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private final String host;
    private final int port;
    
    private ClientSocketManager(String host, int port) {
        this.host = host;
        this.port = port;
        connect();
    }
    
    public static synchronized ClientSocketManager getInstance() {
        if (instance == null) {
            instance = new ClientSocketManager("localhost", 16767);
        }
        return instance;
    }
    
    private void connect() {
        try {
            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            System.err.println("Error connecting to server: " + e.getMessage());
        }
    }
    
    public Response sendRequest(Request request) {
        try {
            if (socket == null || socket.isClosed()) {
                connect();
            }
            
            String requestXml = XmlUtils.toXml(request);
            out.println(requestXml);
            
            String responseXml = in.readLine();
            return XmlUtils.fromXml(responseXml, Response.class);
        } catch (IOException e) {
            System.err.println("Error sending request: " + e.getMessage());
            return new Response(false, "Error: " + e.getMessage(), null);
        }
    }
    
    public void close() {
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }
    }
}
package com.thriftbite.common;

import java.io.Serializable;

public class TransactionDTO implements Serializable {
    private String timestamp;
    private String user;
    private String action;
    private String details;

    public TransactionDTO() {

    }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
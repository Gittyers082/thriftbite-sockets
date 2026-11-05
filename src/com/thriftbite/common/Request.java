package com.thriftbite.common;

import java.io.Serializable;

public class Request implements Serializable {
    private static final long serialVersionUID = 1L;

    private String action;
    private String payload;

    public Request() {}

    public Request(String action, String payload) {
        this.action = action;
        this.payload = payload;
    }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
}

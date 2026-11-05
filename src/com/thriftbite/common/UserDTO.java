package com.thriftbite.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


public class UserDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<ItemDTO> orders = new ArrayList<>();
    private String username;
    private String passwordHash;
    private Role role;
    private boolean isApproved = false;

    public UserDTO() {}

    public UserDTO(String username, String passwordHash, Role role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public List<ItemDTO> getOrders() { return orders; }
    public void setOrders(List<ItemDTO> orders) { this.orders = orders; }

    public boolean isApproved() { return isApproved; }
    public void setApproved(boolean approved) { isApproved = approved; }

}
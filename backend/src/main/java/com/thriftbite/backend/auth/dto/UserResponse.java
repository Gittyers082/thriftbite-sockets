package com.thriftbite.backend.auth.dto;

import com.thriftbite.backend.user.Role;
import com.thriftbite.backend.user.User;

import java.util.UUID;

/** What the API exposes about a user. The password hash is never included. */
public record UserResponse(UUID id, String username, Role role, boolean approved, boolean staff) {

    public static UserResponse from(User user) {
        return from(user, false);
    }

    /** @param staff a store account that works at the store but doesn't own it: fewer screens */
    public static UserResponse from(User user, boolean staff) {
        return new UserResponse(user.getId(), user.getUsername(), user.getRole(), user.isApproved(), staff);
    }
}

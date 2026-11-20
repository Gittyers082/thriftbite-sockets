package com.thriftbite.backend.auth.dto;

import com.thriftbite.backend.user.Role;
import com.thriftbite.backend.user.User;

import java.util.List;
import java.util.UUID;

/**
 * The new account, plus its recovery codes.
 *
 * @param recoveryCodes shown to the person once, right now. Only their hashes are stored,
 *                      so they can never be read again.
 */
public record RegisterResponse(UUID id, String username, Role role, boolean approved, List<String> recoveryCodes) {

    public static RegisterResponse from(User user, List<String> recoveryCodes) {
        return new RegisterResponse(user.getId(), user.getUsername(), user.getRole(), user.isApproved(), recoveryCodes);
    }
}

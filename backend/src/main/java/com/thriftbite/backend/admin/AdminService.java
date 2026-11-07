package com.thriftbite.backend.admin;

import com.thriftbite.backend.auth.dto.UserResponse;
import com.thriftbite.backend.common.ApiExceptions.NotFoundException;
import com.thriftbite.backend.user.Role;
import com.thriftbite.backend.user.User;
import com.thriftbite.backend.user.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Admin-only operations. The URL rule in SecurityConfig already blocks
 * non-admins; @PreAuthorize is a second lock in case this service is ever
 * called from somewhere else.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminService {

    private final UserRepository users;

    public AdminService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> pendingOwners() {
        return users.findByRoleAndApprovedFalseOrderByCreatedAtAsc(Role.OWNER).stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional
    public UserResponse approve(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        user.approve();   // saved automatically when the transaction commits
        return UserResponse.from(user);
    }
}

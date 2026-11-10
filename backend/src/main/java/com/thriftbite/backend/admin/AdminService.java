package com.thriftbite.backend.admin;

import com.thriftbite.backend.live.LiveChange.ApprovalsChanged;
import org.springframework.context.ApplicationEventPublisher;
import com.thriftbite.backend.auth.dto.UserResponse;
import com.thriftbite.backend.common.ApiExceptions.ForbiddenException;
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
    private final ApplicationEventPublisher live;

    public AdminService(UserRepository users, ApplicationEventPublisher live) {
        this.users = users;
        this.live = live;
    }

    /**
     * Stores waiting for approval. The public demo's admin (anyone can log in as it)
     * only sees the demo's sample stores, never real sign-ups.
     */
    @Transactional(readOnly = true)
    public List<UserResponse> pendingOwners(UUID adminId) {
        List<User> pending = actor(adminId).isDemo()
                ? users.findByRoleAndApprovedFalseAndDemoTrueOrderByCreatedAtAsc(Role.OWNER)
                : users.findByRoleAndApprovedFalseOrderByCreatedAtAsc(Role.OWNER);
        return pending.stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse approve(UUID adminId, UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (actor(adminId).isDemo() && !user.isDemo()) {
            throw new ForbiddenException("The demo admin can only approve the demo's sample stores");
        }
        user.approve();   // saved automatically when the transaction commits
        live.publishEvent(new ApprovalsChanged());   // other admins' screens reload
        return UserResponse.from(user);
    }

    private User actor(UUID adminId) {
        return users.findById(adminId)
                .orElseThrow(() -> new ForbiddenException("Your account no longer exists. Log in again."));
    }
}

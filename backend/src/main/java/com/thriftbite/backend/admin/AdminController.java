package com.thriftbite.backend.admin;

import com.thriftbite.backend.auth.dto.UserResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService admin;

    public AdminController(AdminService admin) {
        this.admin = admin;
    }

    @GetMapping("/owners/pending")
    public List<UserResponse> pendingOwners() {
        return admin.pendingOwners();
    }

    @PostMapping("/users/{userId}/approve")
    public UserResponse approve(@PathVariable UUID userId) {
        return admin.approve(userId);
    }
}

package com.thriftbite.backend.admin;

import com.thriftbite.backend.admin.dto.AdminReport;
import com.thriftbite.backend.admin.dto.AdminSummary;
import com.thriftbite.backend.admin.dto.AdminUser;
import com.thriftbite.backend.admin.dto.ResolveReportRequest;
import com.thriftbite.backend.admin.dto.SuspendRequest;
import com.thriftbite.backend.auth.TokenService;
import com.thriftbite.backend.auth.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public List<UserResponse> pendingOwners(@AuthenticationPrincipal Jwt jwt) {
        return admin.pendingOwners(TokenService.userId(jwt));
    }

    @GetMapping("/summary")
    public AdminSummary summary(@AuthenticationPrincipal Jwt jwt) {
        return admin.summary(TokenService.userId(jwt));
    }

    /** @param status "open" (the default) or "resolved" */
    @GetMapping("/reports")
    public List<AdminReport> reports(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "open") String status) {
        return admin.reports(TokenService.userId(jwt), !"resolved".equals(status));
    }

    @PostMapping("/reports/{reportId}/resolve")
    public AdminReport resolve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID reportId,
                               @Valid @RequestBody ResolveReportRequest request) {
        return admin.resolveReport(TokenService.userId(jwt), reportId, request.reply());
    }

    @GetMapping("/activity")
    public List<AdminLog.Entry> activity(@AuthenticationPrincipal Jwt jwt) {
        return admin.activity(TokenService.userId(jwt));
    }

    /**
     * @param q    part of a username or store name
     * @param show "buyers", "stores" or "suspended"; leave out for everyone
     */
    @GetMapping("/users")
    public List<AdminUser> accounts(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "") String q,
                                    @RequestParam(defaultValue = "") String show) {
        return admin.accounts(TokenService.userId(jwt), q, show);
    }

    @PostMapping("/users/{userId}/suspend")
    public AdminUser suspend(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId,
                             @Valid @RequestBody SuspendRequest request) {
        return admin.suspend(TokenService.userId(jwt), userId, request.reason());
    }

    @PostMapping("/users/{userId}/unsuspend")
    public AdminUser liftSuspension(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId) {
        return admin.liftSuspension(TokenService.userId(jwt), userId);
    }

    @PostMapping("/users/{userId}/approve")
    public UserResponse approve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId) {
        return admin.approve(TokenService.userId(jwt), userId);
    }
}

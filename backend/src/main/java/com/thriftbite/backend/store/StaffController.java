package com.thriftbite.backend.store;

import com.thriftbite.backend.auth.TokenService;
import com.thriftbite.backend.store.StaffService.NewStaff;
import com.thriftbite.backend.store.StaffService.Staff;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** The owner's staff list. Every call checks the caller owns the store (see StaffService). */
@RestController
@RequestMapping("/api/owner/staff")
public class StaffController {

    /** The same username rules as signing up. */
    public record AddStaffRequest(
            @NotBlank @Size(min = 3, max = 30)
            @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "may only contain letters, numbers, '.', '_' and '-'")
            String username) {
    }

    private final StaffService staff;

    StaffController(StaffService staff) {
        this.staff = staff;
    }

    @GetMapping
    public List<Staff> list(@AuthenticationPrincipal Jwt jwt) {
        return staff.list(TokenService.userId(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NewStaff add(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddStaffRequest request) {
        return staff.add(TokenService.userId(jwt), request.username());
    }

    @PostMapping("/{staffId}/password")
    public NewStaff newPassword(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID staffId) {
        return staff.newPassword(TokenService.userId(jwt), staffId);
    }

    @DeleteMapping("/{staffId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID staffId) {
        staff.remove(TokenService.userId(jwt), staffId);
    }
}

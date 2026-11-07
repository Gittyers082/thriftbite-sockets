package com.thriftbite.backend.demo;

import com.thriftbite.backend.auth.TokenService;
import com.thriftbite.backend.auth.dto.LoginResponse;
import com.thriftbite.backend.auth.dto.UserResponse;
import com.thriftbite.backend.common.ApiExceptions.NotFoundException;
import com.thriftbite.backend.user.User;
import com.thriftbite.backend.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * One-tap demo logins, so a visitor can try each role without signing up.
 * Only exists when app.demo.enabled is true; otherwise these paths are 404.
 */
@RestController
@RequestMapping("/api/demo")
@ConditionalOnProperty(prefix = "app.demo", name = "enabled", havingValue = "true")
public class DemoController {

    public enum DemoRole { BUYER, OWNER, ADMIN }

    public record DemoAccount(DemoRole as, String username, String description) {
    }

    public record DemoInfo(List<DemoAccount> accounts, Instant seededAt, Instant resetsAt) {
    }

    public record DemoLoginRequest(@NotNull DemoRole as) {
    }

    private static final List<DemoAccount> ACCOUNTS = List.of(
            new DemoAccount(DemoRole.BUYER, DemoData.BUYER, "Has one pickup waiting at Sunrise Bakery"),
            new DemoAccount(DemoRole.OWNER, DemoData.OWNER, "Sunrise Bakery, with pickups to hand over"),
            new DemoAccount(DemoRole.ADMIN, DemoData.ADMIN, "Three stores waiting for approval"));

    private final DemoData demo;
    private final UserRepository users;
    private final TokenService tokens;

    public DemoController(DemoData demo, UserRepository users, TokenService tokens) {
        this.demo = demo;
        this.users = users;
        this.tokens = tokens;
    }

    /** Lets the frontend know the demo is on, and when the data next resets. */
    @GetMapping
    public DemoInfo info() {
        demo.ensureFresh();
        return new DemoInfo(ACCOUNTS, demo.seededAt(), demo.resetsAt());
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody DemoLoginRequest request) {
        demo.ensureFresh();
        String username = ACCOUNTS.stream().filter(a -> a.as() == request.as()).findFirst().orElseThrow().username();
        User user = users.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("Demo account is being reset. Try again in a moment."));
        TokenService.IssuedToken token = tokens.issue(user);
        return new LoginResponse(token.value(), token.expiresAt(), UserResponse.from(user));
    }
}

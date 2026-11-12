package com.thriftbite.backend.auth;

import com.thriftbite.backend.auth.dto.LoginRequest;
import com.thriftbite.backend.auth.dto.RecoverRequest;
import com.thriftbite.backend.auth.dto.RecoverResponse;
import com.thriftbite.backend.auth.dto.RegisterResponse;
import com.thriftbite.backend.common.ratelimit.ClientAddress;
import jakarta.servlet.http.HttpServletRequest;
import com.thriftbite.backend.auth.dto.LoginResponse;
import com.thriftbite.backend.auth.dto.RegisterRequest;
import com.thriftbite.backend.auth.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final ClientAddress clientAddress;

    public AuthController(AuthService auth, ClientAddress clientAddress) {
        this.auth = auth;
        this.clientAddress = clientAddress;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return auth.login(request, clientAddress.of(http));
    }

    /**
     * Forgot your password: set a new one with a recovery code. Leave {@code newPassword}
     * out to only check the code first.
     */
    @PostMapping("/recover")
    public RecoverResponse recover(@Valid @RequestBody RecoverRequest request) {
        return auth.recover(request);
    }

    /** Who am I? The id comes from the verified token, not from anything the client sends. */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return auth.currentUser(TokenService.userId(jwt));
    }
}

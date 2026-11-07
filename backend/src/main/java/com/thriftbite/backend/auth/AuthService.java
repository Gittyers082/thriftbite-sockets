package com.thriftbite.backend.auth;

import com.thriftbite.backend.auth.dto.LoginRequest;
import com.thriftbite.backend.auth.dto.LoginResponse;
import com.thriftbite.backend.auth.dto.RegisterRequest;
import com.thriftbite.backend.auth.dto.UserResponse;
import com.thriftbite.backend.common.ApiExceptions.AccountNotApprovedException;
import com.thriftbite.backend.common.ApiExceptions.ConflictException;
import com.thriftbite.backend.common.ApiExceptions.NotFoundException;
import com.thriftbite.backend.common.ApiExceptions.TooManyRequestsException;
import com.thriftbite.backend.common.ratelimit.LoginAttempts;
import com.thriftbite.backend.store.StoreService;
import com.thriftbite.backend.user.Role;
import com.thriftbite.backend.user.User;
import com.thriftbite.backend.user.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final LoginAttempts loginAttempts;
    private final StoreService stores;

    /**
     * Compared against when a username doesn't exist, so a failed login takes
     * about as long either way and response timing doesn't reveal which usernames exist.
     */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens,
                       LoginAttempts loginAttempts, StoreService stores) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.loginAttempts = loginAttempts;
        this.stores = stores;
        this.dummyHash = passwordEncoder.encode("timing-equalizer-not-a-real-password");
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String username = normalize(request.username());
        if (users.existsByUsername(username)) {
            throw new ConflictException("Username is already taken");
        }
        Role role = switch (request.accountType()) {
            case OWNER -> Role.OWNER;
            case CONSUMER -> Role.CONSUMER;
        };
        User user = users.save(new User(username, passwordEncoder.encode(request.password()), role));
        if (role == Role.OWNER) {
            // The store exists from sign-up, but it can't list food until an admin approves the owner.
            stores.createForOwner(user, request.storeName());
        }
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String username = normalize(request.username());
        // Checked before the password, so a paused username can't keep guessing.
        Duration wait = loginAttempts.blockedFor(username);
        if (wait != null) {
            throw new TooManyRequestsException(
                    "Too many wrong passwords for this username. Wait a few minutes and try again.",
                    wait, "login_locked");
        }

        Optional<User> found = users.findByUsername(username);
        String hash = found.map(User::getPasswordHash).orElse(dummyHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);

        if (found.isEmpty() || !passwordMatches) {
            loginAttempts.recordFailure(username);
            throw new BadCredentialsException("Invalid username or password");
        }
        loginAttempts.recordSuccess(username);
        User user = found.get();
        if (!user.isApproved()) {
            throw new AccountNotApprovedException();
        }
        TokenService.IssuedToken token = tokens.issue(user);
        return new LoginResponse(token.value(), token.expiresAt(), UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(UUID userId) {
        return users.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new NotFoundException("User no longer exists"));
    }

    /** Usernames are case-insensitive: "Steven" and "steven" are the same account. */
    static String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}

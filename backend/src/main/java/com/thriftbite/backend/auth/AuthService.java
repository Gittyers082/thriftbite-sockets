package com.thriftbite.backend.auth;

import com.thriftbite.backend.account.RecoveryCodes;
import com.thriftbite.backend.auth.dto.LoginRequest;
import com.thriftbite.backend.auth.dto.LoginResponse;
import com.thriftbite.backend.auth.dto.RecoverRequest;
import com.thriftbite.backend.auth.dto.RecoverResponse;
import com.thriftbite.backend.auth.dto.RegisterRequest;
import com.thriftbite.backend.auth.dto.RegisterResponse;
import com.thriftbite.backend.auth.dto.UserResponse;
import com.thriftbite.backend.common.ApiExceptions.AccountNotApprovedException;
import com.thriftbite.backend.common.ApiExceptions.BadRequestException;
import com.thriftbite.backend.common.ApiExceptions.ConflictException;
import com.thriftbite.backend.common.ApiExceptions.NotFoundException;
import com.thriftbite.backend.common.ApiExceptions.TooManyRequestsException;
import com.thriftbite.backend.common.ratelimit.LoginAttempts;
import com.thriftbite.backend.live.LiveChange.ApprovalsChanged;
import com.thriftbite.backend.store.StoreService;
import com.thriftbite.backend.user.Role;
import com.thriftbite.backend.user.User;
import com.thriftbite.backend.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
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
    private final RecoveryCodes recoveryCodes;
    private final ApplicationEventPublisher live;

    /**
     * Compared against when a username doesn't exist, so a failed login takes
     * about as long either way and response timing doesn't reveal which usernames exist.
     */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens,
                       LoginAttempts loginAttempts, StoreService stores, RecoveryCodes recoveryCodes,
                       ApplicationEventPublisher live) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.loginAttempts = loginAttempts;
        this.stores = stores;
        this.recoveryCodes = recoveryCodes;
        this.live = live;
        this.dummyHash = passwordEncoder.encode("timing-equalizer-not-a-real-password");
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
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
            live.publishEvent(new ApprovalsChanged());   // admins' approval screens reload
        }
        // There's no email to send a reset link to, so every account starts with recovery codes.
        users.flush();   // the codes are written with plain SQL, which needs the account row to exist first
        return RegisterResponse.from(user, recoveryCodes.replace(user.getId()));
    }

    /**
     * @param address the visitor's address, so wrong guesses from one place don't lock the
     *                real owner out everywhere (see {@link LoginAttempts})
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request, String address) {
        String username = normalize(request.username());
        String fromHere = "login|" + username + "|" + address;
        String fromAnywhere = "login|" + username;
        // Checked before the password, so a paused username can't keep guessing.
        Duration wait = longer(loginAttempts.blockedFor(fromHere), loginAttempts.blockedFor(fromAnywhere, LoginAttempts.WIDE));
        if (wait != null) {
            throw new TooManyRequestsException(
                    "Too many wrong passwords for this username. Wait a few minutes and try again.",
                    wait, "login_locked");
        }

        Optional<User> found = users.findByUsername(username);
        String hash = found.map(User::getPasswordHash).orElse(dummyHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (found.isEmpty() || !passwordMatches) {
            loginAttempts.recordFailure(fromHere);
            loginAttempts.recordFailure(fromAnywhere, LoginAttempts.WIDE);
            throw new BadCredentialsException("Invalid username or password");
        }
        loginAttempts.reset(fromHere);

        User user = found.get();
        if (!user.isApproved()) {
            throw new AccountNotApprovedException();
        }
        TokenService.IssuedToken token = tokens.issue(user);
        return new LoginResponse(token.value(), token.expiresAt(), UserResponse.from(user));
    }

    /**
     * Sets a new password with a one-time recovery code, and logs the person in.
     * With no new password in the request it only checks the code, without using it up.
     *
     * The answer for a wrong code, a used code and a username that doesn't exist is the
     * same, and wrong tries are counted per username, so this can't be used to find out
     * which usernames exist or to guess codes.
     */
    // Not rolled back for a store still waiting for approval: its password change must stick.
    @Transactional(noRollbackFor = AccountNotApprovedException.class)
    public RecoverResponse recover(RecoverRequest request) {
        String username = normalize(request.username());
        String key = "recover|" + username;
        Duration wait = loginAttempts.blockedFor(key);
        if (wait != null) {
            throw new TooManyRequestsException(
                    "Too many wrong codes for this username. Wait a few minutes and try again.",
                    wait, "recovery_locked");
        }

        Optional<User> found = users.findByUsername(username).filter(user -> !user.isDeleted());
        boolean checkOnly = request.newPassword() == null;
        boolean codeWorks = found.isPresent() && (checkOnly
                ? recoveryCodes.matches(found.get().getId(), request.recoveryCode())
                : recoveryCodes.use(found.get().getId(), request.recoveryCode()));
        if (!codeWorks) {
            int left = loginAttempts.recordFailure(key);
            throw new BadRequestException("That code doesn't match or was already used. " + (left == 0
                    ? "Wait a few minutes before trying again."
                    : left + (left == 1 ? " try" : " tries") + " left before a wait."));
        }
        User user = found.get();
        if (checkOnly) {
            return new RecoverResponse(null, null, null, recoveryCodes.status(user.getId()).left());
        }

        user.changePassword(passwordEncoder.encode(request.newPassword()));   // also logs out every other device
        users.saveAndFlush(user);   // so the new token carries the new version
        loginAttempts.reset(key);
        loginAttempts.reset("login|" + username);   // a lockout from wrong passwords ends here
        int codesLeft = recoveryCodes.status(user.getId()).left();
        if (!user.isApproved()) {
            // A store still waiting for an admin: the password is changed, but there's nothing to log in to yet.
            throw new AccountNotApprovedException();
        }
        TokenService.IssuedToken token = tokens.issue(user);
        return new RecoverResponse(token.value(), token.expiresAt(), UserResponse.from(user), codesLeft);
    }

    private static Duration longer(Duration a, Duration b) {
        if (a == null) {
            return b;
        }
        return b == null || a.compareTo(b) >= 0 ? a : b;
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

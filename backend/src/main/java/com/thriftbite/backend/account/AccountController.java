package com.thriftbite.backend.account;

import com.thriftbite.backend.account.dto.AccountExport;
import com.thriftbite.backend.account.dto.AccountResponse;
import com.thriftbite.backend.account.dto.ChangePasswordRequest;
import com.thriftbite.backend.account.dto.PasswordRequest;
import com.thriftbite.backend.account.dto.RecoveryCodesResponse;
import com.thriftbite.backend.auth.TokenService;
import com.thriftbite.backend.auth.dto.LoginResponse;
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

/** The signed-in person's own account. Which account comes from the token, never from the request. */
@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    public AccountResponse summary(@AuthenticationPrincipal Jwt jwt) {
        return accounts.summary(TokenService.userId(jwt));
    }

    /** Logs out every other device. The response is a fresh login for this one. */
    @PostMapping("/password")
    public LoginResponse changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
        return accounts.changePassword(TokenService.userId(jwt), request.currentPassword(), request.newPassword());
    }

    /** Makes a new set of recovery codes; the old ones stop working. */
    @PostMapping("/recovery-codes")
    public RecoveryCodesResponse newRecoveryCodes(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PasswordRequest request) {
        return new RecoveryCodesResponse(accounts.newRecoveryCodes(TokenService.userId(jwt), request.password()));
    }

    /** "Download my data". */
    @GetMapping("/export")
    public AccountExport export(@AuthenticationPrincipal Jwt jwt) {
        return accounts.export(TokenService.userId(jwt));
    }

    /** The owner closes the store for good (see AccountService). */
    @PostMapping("/close-store")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void closeStore(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PasswordRequest request) {
        accounts.closeStore(TokenService.userId(jwt), request.password());
    }

    /** POST rather than DELETE, because it needs the password in the body. */
    @PostMapping("/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PasswordRequest request) {
        accounts.delete(TokenService.userId(jwt), request.password());
    }
}

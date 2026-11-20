package com.thriftbite.backend.store;

import com.thriftbite.backend.auth.TokenService;
import com.thriftbite.backend.store.dto.StoreProfileRequest;
import com.thriftbite.backend.store.dto.StoreResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in owner's own store profile. Which store it is comes from the token, never from the request. */
@RestController
@RequestMapping("/api/owner/store")
@PreAuthorize("hasRole('OWNER')")
public class OwnerStoreController {

    private final StoreProfileService profiles;

    public OwnerStoreController(StoreProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public StoreResponse mine(@AuthenticationPrincipal Jwt jwt) {
        return profiles.mine(TokenService.userId(jwt));
    }

    @PutMapping
    public StoreResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody StoreProfileRequest request) {
        return profiles.update(TokenService.userId(jwt), request);
    }
}

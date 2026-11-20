package com.thriftbite.backend.storefront;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Public: anyone can look at a store's page without logging in. */
@RestController
@RequestMapping("/api/stores")
public class StorePageController {

    private final StorePageService pages;

    public StorePageController(StorePageService pages) {
        this.pages = pages;
    }

    /**
     * No login needed. A logged-in buyer also gets told whether they follow the store.
     */
    @GetMapping("/{slug}")
    public StorePageResponse page(@PathVariable String slug, Authentication viewer) {
        boolean buyer = viewer instanceof JwtAuthenticationToken
                && viewer.getAuthorities().stream().anyMatch(a -> "ROLE_CONSUMER".equals(a.getAuthority()));
        return pages.page(slug, buyer ? UUID.fromString(viewer.getName()) : null);
    }
}

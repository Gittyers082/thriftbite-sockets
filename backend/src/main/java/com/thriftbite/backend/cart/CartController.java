package com.thriftbite.backend.cart;

import com.thriftbite.backend.auth.TokenService;
import com.thriftbite.backend.cart.dto.CartResponse;
import com.thriftbite.backend.cart.dto.SetQuantityRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/cart")
@PreAuthorize("hasRole('CONSUMER')")
public class CartController {

    private final CartService cart;

    public CartController(CartService cart) {
        this.cart = cart;
    }

    @GetMapping
    public CartResponse view(@AuthenticationPrincipal Jwt jwt) {
        return cart.view(TokenService.userId(jwt));
    }

    /** Adds the item, changes its quantity, or removes it when quantity is 0. */
    @PutMapping("/items/{itemId}")
    public CartResponse setQuantity(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID itemId,
                                    @Valid @RequestBody SetQuantityRequest request) {
        return cart.setQuantity(TokenService.userId(jwt), itemId, request.quantity());
    }

    @DeleteMapping("/items/{itemId}")
    public CartResponse remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID itemId) {
        return cart.setQuantity(TokenService.userId(jwt), itemId, 0);
    }
}

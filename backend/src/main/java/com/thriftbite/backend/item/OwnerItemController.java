package com.thriftbite.backend.item;

import com.thriftbite.backend.auth.TokenService;
import com.thriftbite.backend.item.dto.ItemRequest;
import com.thriftbite.backend.item.dto.ItemResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** An owner's own listings. Who the owner is comes from the token, never from the request. */
@RestController
@RequestMapping("/api/owner/items")
@PreAuthorize("hasRole('OWNER')")
public class OwnerItemController {

    private final ItemService items;

    public OwnerItemController(ItemService items) {
        this.items = items;
    }

    @GetMapping
    public List<ItemResponse> listMine(@AuthenticationPrincipal Jwt jwt) {
        return items.listMine(TokenService.userId(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ItemResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ItemRequest request) {
        return items.create(TokenService.userId(jwt), request);
    }

    @PutMapping("/{itemId}")
    public ItemResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID itemId,
                               @Valid @RequestBody ItemRequest request) {
        return items.update(TokenService.userId(jwt), itemId, request);
    }

    @DeleteMapping("/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID itemId) {
        items.remove(TokenService.userId(jwt), itemId);
    }
}

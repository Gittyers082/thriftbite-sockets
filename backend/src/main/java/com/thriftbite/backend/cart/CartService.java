package com.thriftbite.backend.cart;

import com.thriftbite.backend.cart.dto.CartResponse;
import com.thriftbite.backend.common.ApiExceptions.ConflictException;
import com.thriftbite.backend.common.ApiExceptions.NotFoundException;
import com.thriftbite.backend.item.Item;
import com.thriftbite.backend.item.ItemRepository;
import com.thriftbite.backend.item.ItemStatus;
import com.thriftbite.backend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * The cart is a wish list, not a promise: stock is only taken at checkout.
 * The checks here just give early, friendly errors.
 */
@Service
public class CartService {

    private final CartItemRepository cart;
    private final ItemRepository items;
    private final UserRepository users;

    public CartService(CartItemRepository cart, ItemRepository items, UserRepository users) {
        this.cart = cart;
        this.items = items;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public CartResponse view(UUID userId) {
        return CartResponse.from(cart.findByIdUserId(userId), Instant.now());
    }

    @Transactional
    public CartResponse setQuantity(UUID userId, UUID itemId, int quantity) {
        CartItemId id = new CartItemId(userId, itemId);
        if (quantity == 0) {
            cart.deleteById(id);
            cart.flush();
            return view(userId);
        }

        Item item = items.findWithStoreById(itemId)
                .filter(found -> !found.isRemoved())
                .orElseThrow(() -> new NotFoundException("Item not found"));
        if (item.getStatus() != ItemStatus.AVAILABLE || !item.getExpiresAt().isAfter(Instant.now())) {
            throw new ConflictException("This item is no longer available");
        }
        if (quantity > item.getQuantity()) {
            throw new ConflictException("Only " + item.getQuantity() + " left");
        }

        cart.findById(id).ifPresentOrElse(
                existing -> existing.changeQuantity(quantity),
                () -> cart.save(new CartItem(users.getReferenceById(userId), item, quantity)));
        cart.flush();
        return view(userId);
    }
}

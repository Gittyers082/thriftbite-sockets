package com.thriftbite.backend.cart;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, CartItemId> {

    List<CartItem> findByIdUserId(UUID userId);

    @Transactional
    void deleteByIdUserId(UUID userId);
}

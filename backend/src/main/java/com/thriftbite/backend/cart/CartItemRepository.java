package com.thriftbite.backend.cart;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, CartItemId> {

    /** A user's cart, with each item and its owner loaded in the same query. */
    @EntityGraph(attributePaths = {"item", "item.owner"})
    List<CartItem> findByIdUserId(UUID userId);

    @Transactional
    @Modifying
    @Query("delete from CartItem c where c.id.userId = :userId")
    int deleteAllForUser(@Param("userId") UUID userId);
}

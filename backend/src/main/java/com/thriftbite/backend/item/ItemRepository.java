package com.thriftbite.backend.item;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ItemRepository extends JpaRepository<Item, UUID> {

    List<Item> findByStatusAndExpiresAtAfterOrderByExpiresAtAsc(ItemStatus status, Instant now);

    List<Item> findByOwnerIdOrderByExpiresAtAsc(UUID ownerId);

    /**
     * Takes {@code quantity} units of stock in ONE statement: the check
     * ("is there enough, is it still for sale?") and the write happen together
     * inside the database, so two buyers can never both get the last unit.
     *
     * @return 1 if the stock was reserved, 0 if there wasn't enough left
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Item i
               set i.quantity = i.quantity - :quantity,
                   i.version  = i.version + 1,
                   i.updatedAt = :now
             where i.id = :itemId
               and i.status = :available
               and i.quantity >= :quantity
               and i.expiresAt > :now
            """)
    int reserveStock(@Param("itemId") UUID itemId,
                     @Param("quantity") int quantity,
                     @Param("now") Instant now,
                     @Param("available") ItemStatus available);

    /** Marks an item SOLD_OUT once its stock reaches zero. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Item i
               set i.status = :soldOut
             where i.id = :itemId
               and i.quantity = 0
            """)
    int markSoldOutIfEmpty(@Param("itemId") UUID itemId,
                           @Param("soldOut") ItemStatus soldOut);
}

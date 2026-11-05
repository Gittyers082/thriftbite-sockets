package com.thriftbite.backend.item;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemRepository extends JpaRepository<Item, UUID> {

    /**
     * Public browse: food that's still for sale, soonest-expiring first
     * (that's what buyers want to grab before it's gone).
     *
     * @param namePattern a LIKE pattern built by ItemService, with '!' as the escape character
     */
    @Query(value = """
            select i from Item i join fetch i.owner
             where i.status = :status
               and i.expiresAt > :now
               and lower(i.name) like :namePattern escape '!'
             order by i.expiresAt asc
            """,
            countQuery = """
            select count(i) from Item i
             where i.status = :status
               and i.expiresAt > :now
               and lower(i.name) like :namePattern escape '!'
            """)
    Page<Item> browse(@Param("status") ItemStatus status,
                      @Param("now") Instant now,
                      @Param("namePattern") String namePattern,
                      Pageable pageable);

    /** Loads the owner in the same query, so mapping to a response needs no extra lookups. */
    @EntityGraph(attributePaths = "owner")
    Optional<Item> findWithOwnerById(UUID id);

    @EntityGraph(attributePaths = "owner")
    List<Item> findByOwnerIdAndStatusNotOrderByExpiresAtAsc(UUID ownerId, ItemStatus excluded);

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

    /** Puts stock back after a cancelled reservation. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Item i
               set i.quantity = i.quantity + :quantity,
                   i.version  = i.version + 1,
                   i.updatedAt = :now
             where i.id = :itemId
            """)
    int restock(@Param("itemId") UUID itemId,
                @Param("quantity") int quantity,
                @Param("now") Instant now);

    /** A sold-out item that got stock back is for sale again. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Item i
               set i.status = :available
             where i.id = :itemId
               and i.status = :soldOut
               and i.quantity > 0
            """)
    int markAvailableIfRestocked(@Param("itemId") UUID itemId,
                                 @Param("available") ItemStatus available,
                                 @Param("soldOut") ItemStatus soldOut);

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

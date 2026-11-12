package com.thriftbite.backend.live;

import java.util.UUID;

/**
 * Something changed that open screens should hear about.
 *
 * Publish one with ApplicationEventPublisher inside the transaction that makes
 * the change. {@link LiveBroadcaster} sends it only after that transaction
 * commits, so a screen is never told about a change that was rolled back.
 */
public sealed interface LiveChange {

    /** One listing's stock or status changed (reserved, restocked, edited, posted, taken down). Sent to everyone. */
    record ItemChanged(UUID itemId) implements LiveChange {
    }

    /** Many listings changed at once (food expired, demo reset). Sent to everyone. */
    record DealsChanged() implements LiveChange {
    }

    /** A store's pickups or listings changed. Sent to that store's own screens. */
    record StoreChanged(UUID storeId) implements LiveChange {
    }

    /** One of a buyer's reservations changed. Sent to that buyer's own screens. */
    record ReservationsChanged(UUID buyerId) implements LiveChange {
    }

    /** A buyer got a new alert, or one went away. Sent to that buyer's own screens. */
    record AlertsChanged(UUID userId) implements LiveChange {
    }

    /** The list of stores waiting for approval changed. Sent to admins. */
    record ApprovalsChanged() implements LiveChange {
    }
}

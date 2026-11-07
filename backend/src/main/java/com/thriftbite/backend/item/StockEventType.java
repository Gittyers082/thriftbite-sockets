package com.thriftbite.backend.item;

/** Kinds of rows in item_events. */
public enum StockEventType {
    /** The owner put the food up (qty_delta = the starting stock). */
    LISTED,
    /** The owner changed the stock by hand. */
    ADJUSTED,
    /** A buyer's checkout took stock. */
    RESERVED,
    /** A cancelled or missed reservation put stock back. */
    RELEASED,
    /** The last unit was reserved (qty_delta 0). Sell-out times come from these. */
    SOLD_OUT,
    /** The food passed its expiry time; qty_after is what was left over. */
    EXPIRED,
    /** The owner took the listing down; qty_after is what was left. */
    REMOVED
}

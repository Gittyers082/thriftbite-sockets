package com.thriftbite.backend.report;

/** What went wrong, in the buyer's terms. The database has a matching CHECK constraint. */
public enum ReportKind {
    /** The store was closed when the buyer came to pick up. */
    STORE_CLOSED,
    /** The store was open but didn't have the reserved food. */
    NO_FOOD,
    /** The food wasn't what the listing said (less of it, something else, not the price shown). */
    NOT_AS_LISTED,
    /** The food looked or smelled unsafe to eat. */
    UNSAFE_FOOD,
    /** The buyer was asked to pay more than the app showed. */
    OVERCHARGED,
    /** Anything else; the details are required. */
    OTHER
}

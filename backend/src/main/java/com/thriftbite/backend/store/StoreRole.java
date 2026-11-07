package com.thriftbite.backend.store;

/** OWNER runs the store; STAFF (coming later) can hand over pickups but not see sales. */
public enum StoreRole {
    OWNER,
    STAFF
}

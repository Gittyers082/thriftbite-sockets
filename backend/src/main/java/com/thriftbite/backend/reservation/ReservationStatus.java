package com.thriftbite.backend.reservation;

public enum ReservationStatus {
    RESERVED,   // paid for (or promised), waiting for pickup
    COMPLETED,  // owner checked the claim code and handed the food over
    CANCELLED,
    EXPIRED     // buyer missed the pickup deadline
}

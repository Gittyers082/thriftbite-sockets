package com.thriftbite.backend.store.dto;

import com.thriftbite.backend.store.Store;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** A store as buyers and its owner see it. Everything here is public. */
public record StoreResponse(
        UUID id,
        String slug,
        String name,
        String address,
        String barangay,
        String landmark,
        boolean gcashAccepted,
        LocalTime defaultPickupStart,
        LocalTime defaultPickupEnd,
        List<StoreHoursEntry> hours,
        UUID photoId,
        long version
) {

    /** Call inside a transaction: the hours are loaded on demand. */
    public static StoreResponse from(Store store) {
        List<StoreHoursEntry> hours = Arrays.stream(DayOfWeek.values())   // Monday first
                .filter(day -> store.getHours().containsKey(day))
                .map(day -> new StoreHoursEntry(day, store.getHours().get(day).getOpens(), store.getHours().get(day).getCloses()))
                .toList();
        return new StoreResponse(store.getId(), store.getSlug(), store.getName(), store.getAddress(),
                store.getBarangay(), store.getLandmark(), store.isGcashAccepted(),
                store.getDefaultPickupStart(), store.getDefaultPickupEnd(), hours, store.getPhotoId(), store.getVersion());
    }
}

package com.thriftbite.backend.store.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Body for saving the store profile. Rules that involve two fields (opens
 * before closes, one entry per day) are checked in StoreProfileService.
 *
 * @param photoId a photo uploaded to /api/owner/photos, or null for none
 * @param hours   one entry per open day; a day left out is closed
 * @param version the version you loaded, so two people editing at once can't overwrite each other
 */
public record StoreProfileRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 200) String address,
        @Size(max = 80) String barangay,
        @Size(max = 200) String landmark,
        boolean gcashAccepted,
        LocalTime defaultPickupStart,
        LocalTime defaultPickupEnd,
        @NotNull @Size(max = 7) List<@Valid @NotNull StoreHoursEntry> hours,
        @NotNull Long version,
        UUID photoId
) {
}

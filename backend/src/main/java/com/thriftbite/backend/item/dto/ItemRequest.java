package com.thriftbite.backend.item.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Body for creating or updating a listing. Rules that involve two fields
 * (price vs. original price, pickup window vs. expiry) are checked in ItemService.
 *
 * @param version required when updating: the version you last saw, so you can't
 *                overwrite a change you didn't know about (e.g. a sale)
 */
public record ItemRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
        @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal originalPrice,
        @PositiveOrZero @Max(10_000) int quantity,
        @NotNull Instant expiresAt,
        @NotBlank @Size(max = 200) String pickupLocation,
        @NotNull Instant pickupStart,
        @NotNull Instant pickupEnd,
        Long version
) {
}

package com.thriftbite.backend.item.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Body for posting several listings at once, all with the same pickup window. */
public record BatchListingRequest(
        @NotNull Instant pickupStart,
        @NotNull Instant pickupEnd,
        @NotEmpty @Size(max = 30) List<@Valid @NotNull Entry> items
) {

    public record Entry(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
            @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal originalPrice,
            @Min(1) @Max(10_000) int quantity,
            @NotBlank @Size(max = 200) String pickupLocation,
            UUID photoId
    ) {
    }
}

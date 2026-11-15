package com.thriftbite.backend.report.dto;

import com.thriftbite.backend.report.ReportKind;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Body for "Report a problem". Give exactly one of the two targets.
 *
 * @param reservationId one of the buyer's own reservations, when the problem is with a pickup
 * @param storeSlug     the store, when it's about the store in general
 * @param details       what happened, in the buyer's words; required when {@code kind} is OTHER
 */
public record ReportRequest(
        UUID reservationId,
        @Size(max = 80) String storeSlug,
        @NotNull ReportKind kind,
        @Size(max = 1000) String details
) {
}

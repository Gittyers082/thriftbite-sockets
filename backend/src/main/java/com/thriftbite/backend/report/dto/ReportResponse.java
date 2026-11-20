package com.thriftbite.backend.report.dto;

import com.thriftbite.backend.report.ReportKind;

import java.time.Instant;
import java.util.UUID;

/**
 * A report as the buyer who made it sees it.
 *
 * @param reservationId null when the report is about the store in general
 * @param status        OPEN until an admin has looked into it, then RESOLVED
 * @param reply         what the admin told the buyer when closing it, if anything
 */
public record ReportResponse(
        UUID id,
        ReportKind kind,
        String storeName,
        String storeSlug,
        UUID reservationId,
        String details,
        String status,
        String reply,
        Instant createdAt,
        Instant resolvedAt
) {
}

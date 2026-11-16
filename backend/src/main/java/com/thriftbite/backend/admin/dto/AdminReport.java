package com.thriftbite.backend.admin.dto;

import com.thriftbite.backend.report.ReportKind;

import java.time.Instant;
import java.util.UUID;

/**
 * A problem report as an admin sees it, with what's needed to judge it.
 *
 * @param reporter           the buyer's username ("Deleted account" if they've left)
 * @param pickup             what was reserved and how it ended, e.g. "2 × Pandesal, reserved 12 Nov, picked up";
 *                           null when the report is about the store in general
 * @param storeReports       reports about this store in the last 90 days, this one included
 * @param reporterReports    reports this buyer has sent in the last 90 days, this one included
 * @param resolvedBy         the admin who answered it
 */
public record AdminReport(
        UUID id,
        ReportKind kind,
        String details,
        String status,
        String reply,
        Instant createdAt,
        Instant resolvedAt,
        String resolvedBy,
        String reporter,
        String storeName,
        String storeSlug,
        String pickup,
        int storeReports,
        int reporterReports
) {
}

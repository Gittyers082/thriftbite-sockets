package com.thriftbite.backend.admin.dto;

/** How much is waiting for an admin, for the numbers on the nav. */
public record AdminSummary(int pendingStores, int openReports) {
}

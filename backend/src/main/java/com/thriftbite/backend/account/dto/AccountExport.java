package com.thriftbite.backend.account.dto;

import com.thriftbite.backend.item.dto.ItemResponse;
import com.thriftbite.backend.report.dto.ReportResponse;
import com.thriftbite.backend.reservation.dto.ReservationResponse;
import com.thriftbite.backend.store.dto.StoreResponse;

import java.time.Instant;
import java.util.List;

/**
 * "Download my data": everything ThriftBite keeps about one account, as JSON.
 * Parts that don't apply to the kind of account are null.
 *
 * @param reservations buyers: every reservation, with claim codes
 * @param following    buyers: the stores they follow
 * @param reports      buyers: the problems they reported
 * @param store        stores: the store profile
 * @param listings     stores: every listing that hasn't been taken down
 */
public record AccountExport(
        Instant exportedAt,
        AccountResponse account,
        List<ReservationResponse> reservations,
        List<String> following,
        List<ReportResponse> reports,
        StoreResponse store,
        List<ItemResponse> listings
) {
}

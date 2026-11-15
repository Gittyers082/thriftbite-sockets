package com.thriftbite.backend.report;

import com.thriftbite.backend.common.ApiExceptions.BadRequestException;
import com.thriftbite.backend.common.ApiExceptions.ConflictException;
import com.thriftbite.backend.common.ApiExceptions.NotFoundException;
import com.thriftbite.backend.common.ApiExceptions.TooManyRequestsException;
import com.thriftbite.backend.report.dto.ReportRequest;
import com.thriftbite.backend.report.dto.ReportResponse;
import com.thriftbite.backend.store.Store;
import com.thriftbite.backend.store.StoreMemberRepository;
import com.thriftbite.backend.store.StoreRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * "Report a problem". A buyer tells the admins that something went wrong with
 * one of their pickups, or with a store. The store isn't told who reported it.
 *
 * A report is an accusation, so it's kept honest: a pickup report must be about
 * the buyer's own reservation, there's one per pickup, and one buyer can send
 * only a handful a day.
 */
@Service
public class Reports {

    static final int MAX_PER_DAY = 5;
    /** After this long, a pickup is too old to look into. */
    static final Duration REPORT_WITHIN = Duration.ofDays(30);
    private static final int MIN_DETAILS_FOR_OTHER = 10;

    private static final RowMapper<ReportResponse> ROW = (rs, row) -> {
        Timestamp resolved = rs.getTimestamp("resolved_at");
        return new ReportResponse(
                rs.getObject("id", UUID.class), ReportKind.valueOf(rs.getString("kind")),
                rs.getString("store_name"), rs.getString("store_slug"), rs.getObject("reservation_id", UUID.class),
                rs.getString("details"), rs.getString("status"), rs.getString("reply"),
                rs.getTimestamp("created_at").toInstant(), resolved == null ? null : resolved.toInstant());
    };
    private static final String SELECT = """
            SELECT r.id, r.kind, s.name AS store_name, s.slug AS store_slug, r.reservation_id, r.details,
                   r.status, r.reply, r.created_at, r.resolved_at
              FROM reports r JOIN stores s ON s.id = r.store_id
            """;

    private final JdbcTemplate jdbc;
    private final StoreRepository stores;
    private final StoreMemberRepository members;

    Reports(JdbcTemplate jdbc, StoreRepository stores, StoreMemberRepository members) {
        this.jdbc = jdbc;
        this.stores = stores;
        this.members = members;
    }

    @Transactional
    public ReportResponse create(UUID buyerId, ReportRequest request) {
        boolean aboutPickup = request.reservationId() != null;
        boolean aboutStore = request.storeSlug() != null && !request.storeSlug().isBlank();
        if (aboutPickup == aboutStore) {
            throw new BadRequestException("Say which pickup or which store the problem is with (one of them)");
        }
        String details = request.details() == null || request.details().isBlank() ? null : request.details().trim();
        if (request.kind() == ReportKind.OTHER && (details == null || details.length() < MIN_DETAILS_FOR_OTHER)) {
            throw new BadRequestException("Tell us what happened, in a sentence or two");
        }
        checkDailyLimit(buyerId);

        UUID storeId = aboutPickup ? storeOfOwnReservation(buyerId, request.reservationId())
                : publicStore(request.storeSlug()).getId();
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO reports (id, reporter_id, store_id, reservation_id, kind, details)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, id, buyerId, storeId, request.reservationId(), request.kind().name(), details);
        } catch (DuplicateKeyException e) {
            throw new ConflictException(aboutPickup
                    ? "You've already reported a problem with this pickup"
                    : "You already have an open report about this store. We'll look into that one first.");
        }
        return jdbc.queryForObject(SELECT + " WHERE r.id = ?", ROW, id);
    }

    /** The buyer's own reports, newest first. */
    @Transactional(readOnly = true)
    public List<ReportResponse> mine(UUID buyerId) {
        return jdbc.query(SELECT + " WHERE r.reporter_id = ? ORDER BY r.created_at DESC LIMIT 50", ROW, buyerId);
    }

    /**
     * When an account is deleted: the report stays, because the store may still need looking
     * into, but what the buyer wrote goes.
     */
    @Transactional
    public void forget(UUID buyerId) {
        jdbc.update("UPDATE reports SET details = NULL WHERE reporter_id = ?", buyerId);
    }

    private void checkDailyLimit(UUID buyerId) {
        jdbc.query("""
                SELECT COUNT(*) AS sent, MIN(created_at) AS oldest
                  FROM reports WHERE reporter_id = ? AND created_at > now() - interval '24 hours'
                """, rs -> {
                    if (rs.getInt("sent") >= MAX_PER_DAY) {
                        Instant free = rs.getTimestamp("oldest").toInstant().plus(Duration.ofHours(24));
                        throw new TooManyRequestsException(
                                "You've sent " + MAX_PER_DAY + " reports in the last day, which is the most we take. "
                                        + "Try again tomorrow.",
                                Duration.between(Instant.now(), free), "report_limit");
                    }
                }, buyerId);
    }

    /** Someone else's reservation looks the same as one that doesn't exist. */
    private UUID storeOfOwnReservation(UUID buyerId, UUID reservationId) {
        return jdbc.query("SELECT store_id, created_at FROM reservations WHERE id = ? AND buyer_id = ?", rs -> {
            if (!rs.next()) {
                throw new NotFoundException("Reservation not found");
            }
            if (rs.getTimestamp("created_at").toInstant().isBefore(Instant.now().minus(REPORT_WITHIN))) {
                throw new BadRequestException("That pickup was more than " + REPORT_WITHIN.toDays()
                        + " days ago, which is too long ago for us to look into");
            }
            return rs.getObject("store_id", UUID.class);
        }, reservationId, buyerId);
    }

    private Store publicStore(String slug) {
        return stores.findBySlug(slug.trim().toLowerCase(Locale.ROOT))
                .filter(store -> members.hasApprovedMember(store.getId()))
                .orElseThrow(() -> new NotFoundException("Store not found"));
    }
}

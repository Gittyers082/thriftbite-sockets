package com.thriftbite.backend.admin;

import com.thriftbite.backend.admin.dto.AdminReport;
import com.thriftbite.backend.alert.Alerts;
import com.thriftbite.backend.common.ApiExceptions.ConflictException;
import com.thriftbite.backend.common.ApiExceptions.NotFoundException;
import com.thriftbite.backend.live.LiveChange.ReportsChanged;
import com.thriftbite.backend.report.ReportKind;
import com.thriftbite.backend.user.User;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * The admins' side of "Report a problem": read the reports, answer them, close them.
 *
 * The public demo's admin (anyone can log in as it) only sees and answers reports
 * that demo buyers made about demo stores, the same rule as for approvals.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminReports {

    /** True for every report when the viewer is a real admin; only demo-on-demo ones for the demo admin. */
    private static final String VISIBLE = """
            (NOT ? OR (reporter.demo AND EXISTS (
                SELECT 1 FROM store_members m JOIN users owner ON owner.id = m.user_id
                 WHERE m.store_id = r.store_id AND owner.demo)))
            """;

    private static final String SELECT = """
            SELECT r.id, r.kind, r.details, r.status, r.reply, r.created_at, r.resolved_at,
                   resolver.username AS resolved_by,
                   CASE WHEN reporter.deleted_at IS NULL THEN reporter.username ELSE 'Deleted account' END AS reporter,
                   s.name AS store_name, s.slug AS store_slug,
                   (SELECT string_agg(ri.quantity || ' × ' || ri.item_name, ', ' ORDER BY ri.item_name)
                           || ', reserved ' || to_char(res.created_at AT TIME ZONE 'Asia/Manila', 'FMDD Mon')
                           || CASE res.status WHEN 'COMPLETED' THEN ', picked up'
                                              WHEN 'CANCELLED' THEN ', cancelled'
                                              WHEN 'EXPIRED' THEN ', not picked up'
                                              ELSE ', still waiting' END
                      FROM reservations res JOIN reservation_items ri ON ri.reservation_id = res.id
                     WHERE res.id = r.reservation_id
                     GROUP BY res.id) AS pickup,
                   (SELECT COUNT(*) FROM reports x
                     WHERE x.store_id = r.store_id AND x.created_at > now() - interval '90 days') AS store_reports,
                   (SELECT COUNT(*) FROM reports x
                     WHERE x.reporter_id = r.reporter_id AND x.created_at > now() - interval '90 days') AS reporter_reports
              FROM reports r
              JOIN stores s ON s.id = r.store_id
              JOIN users reporter ON reporter.id = r.reporter_id
              LEFT JOIN users resolver ON resolver.id = r.resolved_by
            """;

    private static final RowMapper<AdminReport> ROW = (rs, row) -> {
        Timestamp resolved = rs.getTimestamp("resolved_at");
        return new AdminReport(rs.getObject("id", UUID.class), ReportKind.valueOf(rs.getString("kind")),
                rs.getString("details"), rs.getString("status"), rs.getString("reply"),
                rs.getTimestamp("created_at").toInstant(), resolved == null ? null : resolved.toInstant(),
                rs.getString("resolved_by"), rs.getString("reporter"), rs.getString("store_name"),
                rs.getString("store_slug"), rs.getString("pickup"), rs.getInt("store_reports"), rs.getInt("reporter_reports"));
    };

    private final JdbcTemplate jdbc;
    private final Alerts alerts;
    private final AdminLog log;
    private final ApplicationEventPublisher live;

    AdminReports(JdbcTemplate jdbc, Alerts alerts, AdminLog log, ApplicationEventPublisher live) {
        this.jdbc = jdbc;
        this.alerts = alerts;
        this.log = log;
        this.live = live;
    }

    /** Open reports oldest first (the longest wait on top); answered ones newest first. */
    @Transactional(readOnly = true)
    public List<AdminReport> list(User admin, boolean open) {
        return jdbc.query(SELECT + " WHERE r.status = ? AND " + VISIBLE
                        + (open ? " ORDER BY r.created_at ASC" : " ORDER BY r.resolved_at DESC") + " LIMIT 200",
                ROW, open ? "OPEN" : "RESOLVED", admin.isDemo());
    }

    @Transactional(readOnly = true)
    public int openCount(User admin) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM reports r JOIN users reporter ON reporter.id = r.reporter_id
                 WHERE r.status = 'OPEN' AND
                """ + VISIBLE, Integer.class, admin.isDemo());
        return count == null ? 0 : count;
    }

    /**
     * Closes a report with a reply to the buyer. The reply shows on the buyer's account
     * page and they get an alert; the store is told nothing by this.
     */
    @Transactional
    public AdminReport resolve(User admin, UUID reportId, String reply) {
        List<AdminReport> found = jdbc.query(SELECT + " WHERE r.id = ? AND " + VISIBLE, ROW, reportId, admin.isDemo());
        if (found.isEmpty()) {
            throw new NotFoundException("Report not found");
        }
        String text = reply.trim();
        // The status check is in the UPDATE itself, so two admins answering at once can't both win.
        int closed = jdbc.update("""
                UPDATE reports SET status = 'RESOLVED', reply = ?, resolved_at = now(), resolved_by = ?
                 WHERE id = ? AND status = 'OPEN'
                """, text, admin.getId(), reportId);
        if (closed == 0) {
            throw new ConflictException("Another admin has already answered this report");
        }
        AdminReport before = found.get(0);
        alerts.reportAnswered(reportId);
        log.record(admin, "ANSWER_REPORT", "Report about " + before.storeName() + " from " + before.reporter(), text);
        live.publishEvent(new ReportsChanged());
        return jdbc.queryForObject(SELECT + " WHERE r.id = ? AND " + VISIBLE, ROW, reportId, admin.isDemo());
    }
}

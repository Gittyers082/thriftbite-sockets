package com.thriftbite.backend.admin;

import com.thriftbite.backend.user.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The record of what admins did: who, what, to whom, when. Admins can approve
 * stores and close reports, so there has to be a way to see afterwards who did.
 * There is no update or delete here on purpose.
 */
@Service
public class AdminLog {

    /** @param note the admin's own words where there are any (the reply to a report) */
    public record Entry(UUID id, String adminName, String action, String subject, String note, Instant at) {
    }

    private final JdbcTemplate jdbc;

    AdminLog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Written in the same transaction as the action itself, so there is never an action
     * without its line, or a line for something that was rolled back.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(User admin, String action, String subject, String note) {
        jdbc.update("""
                INSERT INTO admin_actions (admin_id, admin_name, action, subject, note, demo)
                VALUES (?, ?, ?, ?, ?, ?)
                """, admin.getId(), admin.getUsername(), action, cut(subject, 160), cut(note, 500), admin.isDemo());
    }

    /** Newest first. The public demo's admin sees only what demo admins did. */
    @Transactional(readOnly = true)
    public List<Entry> recent(User viewer) {
        return jdbc.query("""
                SELECT id, admin_name, action, subject, note, created_at
                  FROM admin_actions
                 WHERE demo OR NOT ?
                 ORDER BY created_at DESC LIMIT 200
                """, (rs, row) -> new Entry(rs.getObject("id", UUID.class), rs.getString("admin_name"),
                rs.getString("action"), rs.getString("subject"), rs.getString("note"),
                rs.getTimestamp("created_at").toInstant()), viewer.isDemo());
    }

    private static String cut(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }
}

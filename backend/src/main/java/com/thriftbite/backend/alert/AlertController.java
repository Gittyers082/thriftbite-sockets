package com.thriftbite.backend.alert;

import com.thriftbite.backend.auth.TokenService;
import com.thriftbite.backend.live.LiveChange.AlertsChanged;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A buyer's own alerts. Whose they are comes from the login token, never from the request. */
@RestController
@RequestMapping("/api/alerts")
@PreAuthorize("hasRole('CONSUMER')")
public class AlertController {

    /** The page shows the newest of the last two weeks; anything older has had its chance. */
    private static final int MAX_SHOWN = 50;

    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher live;

    public AlertController(JdbcTemplate jdbc, ApplicationEventPublisher live) {
        this.jdbc = jdbc;
        this.live = live;
    }

    /** @param link a page in the app the alert leads to, e.g. "/stores/sunrise-bakery" */
    public record Alert(UUID id, String kind, String title, String body, String link, Instant createdAt, boolean read) {
    }

    public record AlertList(int unread, List<Alert> alerts) {
    }

    public record Unread(int unread) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public AlertList list(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = TokenService.userId(jwt);
        List<Alert> alerts = jdbc.query("""
                SELECT id, kind, title, body, link, created_at, read_at FROM alerts
                 WHERE user_id = ? AND created_at >= now() - interval '14 days'
                 ORDER BY created_at DESC LIMIT ?
                """,
                (rs, row) -> new Alert(rs.getObject("id", UUID.class), rs.getString("kind"), rs.getString("title"),
                        rs.getString("body"), rs.getString("link"), rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("read_at") != null),
                userId, MAX_SHOWN);
        return new AlertList(unreadCount(userId), alerts);
    }

    /** For the number on the nav. Cheap enough to ask for on every page. */
    @GetMapping("/count")
    public Unread count(@AuthenticationPrincipal Jwt jwt) {
        return new Unread(unreadCount(TokenService.userId(jwt)));
    }

    @PostMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = TokenService.userId(jwt);
        jdbc.update("UPDATE alerts SET read_at = now() WHERE user_id = ? AND read_at IS NULL", userId);
        live.publishEvent(new AlertsChanged(userId));   // the count on the buyer's other screens drops too
    }

    /** Someone else's alert id simply matches nothing. */
    @PostMapping("/{alertId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID alertId) {
        UUID userId = TokenService.userId(jwt);
        if (jdbc.update("UPDATE alerts SET read_at = now() WHERE id = ? AND user_id = ? AND read_at IS NULL",
                alertId, userId) == 1) {
            live.publishEvent(new AlertsChanged(userId));
        }
    }

    private int unreadCount(UUID userId) {
        Integer unread = jdbc.queryForObject("""
                SELECT COUNT(*) FROM alerts
                 WHERE user_id = ? AND read_at IS NULL AND created_at >= now() - interval '14 days'
                """, Integer.class, userId);
        return unread == null ? 0 : unread;
    }
}

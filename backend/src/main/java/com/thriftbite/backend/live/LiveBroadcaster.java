package com.thriftbite.backend.live;

import com.thriftbite.backend.live.LiveChange.AlertsChanged;
import com.thriftbite.backend.live.LiveChange.ApprovalsChanged;
import com.thriftbite.backend.live.LiveChange.DealsChanged;
import com.thriftbite.backend.live.LiveChange.ItemChanged;
import com.thriftbite.backend.live.LiveChange.ReservationsChanged;
import com.thriftbite.backend.live.LiveChange.StoreChanged;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Turns {@link LiveChange}s into messages for open screens.
 *
 * Two rules:
 * 1. Send only after the transaction commits. A checkout that rolls back
 *    (something sold out) must not tell anyone that stock went down.
 * 2. Messages are hints, not data. Apart from a listing's public stock count,
 *    a message only says "this part of your screen is out of date"; the screen
 *    then asks the normal API, which checks the login as always. So nothing
 *    private ever travels over the socket.
 *
 * Sending happens on its own thread, in order, so a slow browser can't slow
 * down the checkout that caused the message.
 */
@Component
public class LiveBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(LiveBroadcaster.class);

    private final LiveSessions sessions;
    private final JdbcTemplate jdbc;

    /** One thread keeps messages in order. If it ever falls far behind, the oldest hints are dropped. */
    private final ExecutorService sender = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(2_000),
            task -> {
                Thread thread = new Thread(task, "live-sender");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.DiscardOldestPolicy());

    public LiveBroadcaster(LiveSessions sessions, JdbcTemplate jdbc) {
        this.sessions = sessions;
        this.jdbc = jdbc;
    }

    /** fallbackExecution: a change published outside any transaction is sent right away. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onChange(LiveChange change) {
        if (sessions.isEmpty()) {
            return;   // nobody is listening: no work, and no database query
        }
        sender.execute(() -> {
            try {
                send(change);
            } catch (RuntimeException e) {
                log.warn("Couldn't send live update {}: {}", change, e.toString());
            }
        });
    }

    private void send(LiveChange change) {
        switch (change) {
            case ItemChanged c -> itemMessage(c.itemId()).ifPresent(sessions::sendToAll);
            case DealsChanged c -> sessions.sendToAll("{\"type\":\"deals\"}");
            case StoreChanged c -> sessions.sendToStore(c.storeId(), "{\"type\":\"store\"}");
            case ReservationsChanged c -> sessions.sendToUser(c.buyerId(), "{\"type\":\"reservations\"}");
            case AlertsChanged c -> sessions.sendToUser(c.userId(), "{\"type\":\"alerts\"}");
            case ApprovalsChanged c -> sessions.sendToAdmins("{\"type\":\"approvals\"}");
        }
    }

    /**
     * The listing's stock as it is in the database now, after the commit. This is the
     * same number anyone can see on the browse page, so it's safe to send to everyone.
     */
    private Optional<String> itemMessage(UUID itemId) {
        return jdbc.query("""
                SELECT quantity,
                       CASE WHEN status = 'AVAILABLE' AND expires_at <= now() THEN 'EXPIRED' ELSE status END AS status
                  FROM items WHERE id = ?
                """,
                rs -> rs.next()
                        ? Optional.of("{\"type\":\"item\",\"id\":\"%s\",\"quantity\":%d,\"status\":\"%s\"}"
                                .formatted(itemId, rs.getInt("quantity"), rs.getString("status")))
                        : Optional.<String>empty(),
                itemId);
    }

    @PreDestroy
    void stop() {
        sender.shutdownNow();
    }
}

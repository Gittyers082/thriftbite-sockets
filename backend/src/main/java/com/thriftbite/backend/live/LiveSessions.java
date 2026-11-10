package com.thriftbite.backend.live;

import com.thriftbite.backend.user.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Every open live connection, and who (if anyone) is logged in on it.
 *
 * Kept in memory: one app instance, no Redis. With several instances behind a
 * load balancer, a change made on one would have to be passed to the others
 * (for example with Redis pub/sub) before being sent out.
 */
@Component
public class LiveSessions {

    private static final Logger log = LoggerFactory.getLogger(LiveSessions.class);

    /** A slow or stuck browser is dropped instead of holding up everyone else's messages. */
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int SEND_BUFFER_LIMIT_BYTES = 64 * 1024;

    /** One open connection. Starts anonymous; {@link #signIn} ties it to an account. */
    public static final class Client {
        private final WebSocketSession session;
        private final String address;
        private volatile boolean authTried;
        private volatile UUID userId;
        private volatile UUID storeId;
        private volatile Role role;
        private volatile Instant loginExpiresAt;

        private Client(WebSocketSession session, String address) {
            this.session = session;
            this.address = address;
        }

        /** True the first time it's called, false after: a connection gets one login attempt. */
        synchronized boolean claimAuthAttempt() {
            if (authTried) {
                return false;
            }
            authTried = true;
            return true;
        }

        void signIn(UUID userId, Role role, UUID storeId, Instant loginExpiresAt) {
            this.userId = userId;
            this.role = role;
            this.storeId = storeId;
            this.loginExpiresAt = loginExpiresAt;
        }

        /** Once the login token runs out, the connection goes back to hearing only public changes. */
        private boolean loggedIn(Instant now) {
            Instant expires = loginExpiresAt;
            return userId != null && expires != null && now.isBefore(expires);
        }

        WebSocketSession session() {
            return session;
        }
    }

    private final Map<String, Client> clients = new ConcurrentHashMap<>();
    private final int maxConnections;
    private final int maxPerAddress;

    public LiveSessions(@Value("${app.live.max-connections:500}") int maxConnections,
                        @Value("${app.live.max-connections-per-address:60}") int maxPerAddress) {
        this.maxConnections = maxConnections;
        this.maxPerAddress = maxPerAddress;
    }

    public boolean isEmpty() {
        return clients.isEmpty();
    }

    public int count() {
        return clients.size();
    }

    /** Whether the app is at its connection limit (every connection holds memory, so there is one). */
    boolean isFull() {
        return clients.size() >= maxConnections;
    }

    /** Whether this address already has as many connections as one address may hold. */
    boolean isFullFor(String address) {
        return clients.values().stream().filter(c -> c.address.equals(address)).count() >= maxPerAddress;
    }

    Client add(WebSocketSession session, String address) {
        Client client = new Client(
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES), address);
        clients.put(session.getId(), client);
        return client;
    }

    Client get(String sessionId) {
        return clients.get(sessionId);
    }

    void remove(String sessionId) {
        clients.remove(sessionId);
    }

    // ---- sending ----

    void sendToAll(String json) {
        send(json, client -> true);
    }

    void sendToStore(UUID storeId, String json) {
        Instant now = Instant.now();
        send(json, client -> client.loggedIn(now) && storeId.equals(client.storeId));
    }

    void sendToUser(UUID userId, String json) {
        Instant now = Instant.now();
        send(json, client -> client.loggedIn(now) && userId.equals(client.userId));
    }

    void sendToAdmins(String json) {
        Instant now = Instant.now();
        send(json, client -> client.loggedIn(now) && client.role == Role.ADMIN);
    }

    private void send(String json, Predicate<Client> audience) {
        TextMessage message = new TextMessage(json);
        clients.values().stream().filter(audience).forEach(client -> send(client, message));
    }

    /** Sends to one connection, and drops the connection if that fails. */
    void send(Client client, WebSocketMessage<?> message) {
        try {
            client.session.sendMessage(message);
        } catch (IOException | RuntimeException e) {
            log.debug("Dropping live connection {}: {}", client.session.getId(), e.toString());
            drop(client, CloseStatus.SESSION_NOT_RELIABLE);
        }
    }

    void drop(Client client, CloseStatus status) {
        clients.remove(client.session.getId());
        try {
            client.session.close(status);
        } catch (IOException | RuntimeException e) {
            // already gone
        }
    }

    /**
     * A ping every so often keeps proxies from closing a quiet connection (Cloudflare, in
     * front of Render, closes one after about 100 seconds of silence) and finds dead ones.
     */
    @Scheduled(fixedDelayString = "${app.live.ping-interval:PT25S}", initialDelayString = "${app.live.ping-interval:PT25S}")
    public void pingAll() {
        if (clients.isEmpty()) {
            return;
        }
        PingMessage ping = new PingMessage();
        clients.values().forEach(client -> send(client, ping));
    }
}

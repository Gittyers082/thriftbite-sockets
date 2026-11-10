package com.thriftbite.backend.live;

import com.thriftbite.backend.auth.CurrentAccountConverter;
import com.thriftbite.backend.common.ApiExceptions.ForbiddenException;
import com.thriftbite.backend.live.LiveSessions.Client;
import com.thriftbite.backend.store.StoreService;
import com.thriftbite.backend.user.Role;
import com.thriftbite.backend.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

/**
 * The live connection at /ws. The server talks, the browser listens.
 *
 * A connection starts anonymous and hears public changes (stock counts). To
 * also hear about its own store's pickups or its own reservations, the browser
 * sends one message: {"type":"auth","token":"<the login token>"}. The token is
 * checked exactly like on an API call. It goes in a message, not the URL,
 * because URLs end up in server and proxy logs.
 *
 * Anything else a client sends closes the connection.
 */
@Component
public class LiveSocketHandler extends TextWebSocketHandler {

    static final String ADDRESS_ATTRIBUTE = "clientAddress";

    /** A login token is a few hundred characters; nothing a client sends should be bigger. */
    private static final int MAX_MESSAGE_BYTES = 4 * 1024;

    private static final Logger log = LoggerFactory.getLogger(LiveSocketHandler.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private record Inbound(String type, String token) {
    }

    private final LiveSessions sessions;
    private final JwtDecoder jwtDecoder;
    private final CurrentAccountConverter accounts;
    private final StoreService stores;

    public LiveSocketHandler(LiveSessions sessions, JwtDecoder jwtDecoder, CurrentAccountConverter accounts,
                             StoreService stores) {
        this.sessions = sessions;
        this.jwtDecoder = jwtDecoder;
        this.accounts = accounts;
        this.stores = stores;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        session.setTextMessageSizeLimit(MAX_MESSAGE_BYTES);
        session.setBinaryMessageSizeLimit(MAX_MESSAGE_BYTES);
        Object address = session.getAttributes().get(ADDRESS_ATTRIBUTE);
        sessions.add(session, address == null ? "unknown" : address.toString());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Client client = sessions.get(session.getId());
        if (client == null) {
            return;
        }
        Inbound inbound;
        try {
            inbound = JSON.readValue(message.getPayload(), Inbound.class);
        } catch (RuntimeException e) {
            sessions.drop(client, CloseStatus.BAD_DATA);
            return;
        }
        // One login attempt per connection. The browser reconnects when someone logs in or out,
        // so a second attempt (or any other message) is a script, not our app.
        if (inbound == null || !"auth".equals(inbound.type()) || !client.claimAuthAttempt()) {
            sessions.drop(client, CloseStatus.POLICY_VIOLATION);
            return;
        }
        sessions.send(client, new TextMessage(signIn(client, inbound.token())
                ? "{\"type\":\"ready\"}"
                : "{\"type\":\"auth_failed\"}"));
    }

    /** Same checks as an API call: signature, expiry, and the account as the database has it now. */
    private boolean signIn(Client client, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            Jwt jwt = jwtDecoder.decode(token);
            User user = accounts.accountFor(jwt);
            Instant expiresAt = jwt.getExpiresAt();
            if (expiresAt == null) {
                return false;
            }
            client.signIn(user.getId(), user.getRole(), storeOf(user), expiresAt);
            return true;
        } catch (JwtException | AuthenticationException e) {
            log.debug("Live login refused: {}", e.getMessage());
            return false;
        }
    }

    private UUID storeOf(User user) {
        if (user.getRole() != Role.OWNER) {
            return null;
        }
        try {
            return stores.storeIdFor(user.getId());
        } catch (ForbiddenException e) {
            return null;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session.getId());
    }
}

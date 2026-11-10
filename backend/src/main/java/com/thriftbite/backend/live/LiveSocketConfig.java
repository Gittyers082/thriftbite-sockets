package com.thriftbite.backend.live;

import com.thriftbite.backend.common.ratelimit.ClientAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Opens the live connection at /ws.
 *
 * Only pages served from our own frontend may connect (the same list of
 * origins as CORS), so another website can't open a connection from a
 * visitor's browser. The number of connections is capped overall and per
 * address, and {@code RateLimitFilter} limits how fast one address can open them.
 */
@Configuration
@EnableWebSocket
public class LiveSocketConfig implements WebSocketConfigurer {

    private final LiveSocketHandler handler;
    private final LiveSessions sessions;
    private final ClientAddress clientAddress;
    private final String[] allowedOrigins;

    public LiveSocketConfig(LiveSocketHandler handler, LiveSessions sessions, ClientAddress clientAddress,
                            @Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
        this.handler = handler;
        this.sessions = sessions;
        this.clientAddress = clientAddress;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws")
                .addInterceptors(new ConnectionLimits())
                .setAllowedOrigins(allowedOrigins);
    }

    /** Runs on the plain HTTP request that asks to become a WebSocket, before the switch. */
    private final class ConnectionLimits implements HandshakeInterceptor {

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) {
            String address = request instanceof ServletServerHttpRequest servlet
                    ? clientAddress.of(servlet.getServletRequest())
                    : "unknown";
            if (sessions.isFull()) {
                response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                return false;
            }
            if (sessions.isFullFor(address)) {
                response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                return false;
            }
            attributes.put(LiveSocketHandler.ADDRESS_ATTRIBUTE, address);
            return true;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
            // nothing to clean up
        }
    }
}

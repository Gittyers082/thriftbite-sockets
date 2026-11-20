package com.thriftbite.backend.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Works out the visitor's own address. Behind a host's proxy the connection comes from the
 * proxy, so every visitor would share one address (and one limit) unless the host's header is read.
 *
 * On Render that header is CF-Connecting-IP: Cloudflare sets it and overwrites anything a
 * client sends. X-Forwarded-For is no good there, because Render appends to whatever the
 * client sent, so a client could pick its own "address".
 */
@Component
public class ClientAddress {

    /** The longest IPv6 address in text form is 45 characters. */
    private static final int MAX_ADDRESS_LENGTH = 45;

    private final String header;

    public ClientAddress(RateLimitProperties properties) {
        this.header = properties.clientIpHeader();
    }

    public String of(HttpServletRequest request) {
        if (!header.isBlank()) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) {
                // For X-Forwarded-For style lists, the last entry is the one our own proxy added.
                String[] parts = value.split(",");
                String address = parts[parts.length - 1].trim();
                // An address is short. Anything longer isn't one, and mustn't become a map key.
                if (!address.isEmpty() && address.length() <= MAX_ADDRESS_LENGTH) {
                    return address;
                }
            }
        }
        return request.getRemoteAddr();
    }
}

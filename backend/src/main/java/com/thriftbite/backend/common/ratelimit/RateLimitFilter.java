package com.thriftbite.backend.common.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;

/**
 * Per-address limits. The endpoints a script would hammer (log in, sign up,
 * checkout, claim-code entry) each have a tight limit; every other API call
 * shares one generous limit, so a script can't run up the free database's
 * hours or drown out real visitors.
 *
 * Runs after Spring Security's filters, so a 429 still carries CORS headers
 * and the browser can read it.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class RateLimitFilter extends OncePerRequestFilter {

    /** @param method the HTTP method the rule is for, or null for any */
    private record Rule(String name, String method, Pattern path, ToIntFunction<RateLimitProperties> limit) {
        boolean matches(HttpServletRequest request) {
            return (method == null || method.equals(request.getMethod()))
                    && path.matcher(request.getRequestURI()).matches();
        }
    }

    private static final List<Rule> RULES = List.of(
            new Rule("login", "POST", Pattern.compile("/api/auth/login"), RateLimitProperties::loginPerWindow),
            new Rule("register", "POST", Pattern.compile("/api/auth/register"), RateLimitProperties::registerPerWindow),
            new Rule("demo-login", "POST", Pattern.compile("/api/demo/login"), RateLimitProperties::loginPerWindow),
            new Rule("checkout", "POST", Pattern.compile("/api/checkout"), RateLimitProperties::checkoutPerWindow),
            new Rule("claim-code", "POST", Pattern.compile("/api/owner/reservations/[^/]+/complete"),
                    RateLimitProperties::claimCodePerWindow),
            // Every API call counts here too, whatever else it matched.
            new Rule("general", null, Pattern.compile("/api/.*"), RateLimitProperties::generalPerWindow));

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    /** The longest IPv6 address in text form is 45 characters. */
    private static final int MAX_ADDRESS_LENGTH = 45;

    private final RateLimitProperties properties;
    private final Clock clock = Clock.systemUTC();
    private final FixedWindowCounter counter = new FixedWindowCounter();

    public RateLimitFilter(RateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled() || RULES.stream().noneMatch(rule -> rule.matches(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String address = clientAddress(request);
        // Turn on with LOGGING_LEVEL_COM_THRIFTBITE_BACKEND_COMMON_RATELIMIT=DEBUG to check, once
        // deployed, that the address is the visitor's own and not the host's proxy.
        log.debug("Rate limit address {} (remote {}, {}: {})", address, request.getRemoteAddr(),
                properties.clientIpHeader(), properties.clientIpHeader().isBlank() ? "-" : request.getHeader(properties.clientIpHeader()));

        // Count the request against every rule it matches; the longest wait wins.
        Duration retryAfter = null;
        for (Rule rule : RULES) {
            if (!rule.matches(request)) {
                continue;
            }
            Duration wait = counter.hit(rule.name() + "|" + address, rule.limit().applyAsInt(properties),
                    properties.window(), clock.instant());
            if (wait != null && (retryAfter == null || wait.compareTo(retryAfter) > 0)) {
                retryAfter = wait;
            }
        }
        if (retryAfter == null) {
            chain.doFilter(request, response);
            return;
        }
        writeTooManyRequests(request, response, retryAfter);
    }

    /**
     * The visitor's own address. Behind a host's proxy the connection comes from the proxy,
     * so every visitor would share one address (and one limit) unless the host's header is read.
     * On Render that header is CF-Connecting-IP: Cloudflare sets it and overwrites anything a
     * client sends. X-Forwarded-For is no good there, because Render appends to whatever the
     * client sent, so a client could pick its own "address".
     */
    private String clientAddress(HttpServletRequest request) {
        String header = properties.clientIpHeader();
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

    /** Same RFC 9457 shape as every other error, written by hand because this runs outside Spring MVC. */
    private static void writeTooManyRequests(HttpServletRequest request, HttpServletResponse response,
                                             Duration retryAfter) throws IOException {
        long seconds = Math.max(1, retryAfter.toSeconds());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(seconds));
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"type":"about:blank","title":"Too Many Requests","status":429,\
                "detail":"Too many requests from this device. Wait a few minutes and try again.",\
                "instance":"%s","code":"rate_limited"}""".formatted(request.getRequestURI().replace("\"", "")));
    }

    /** Drops finished windows now and then, so memory doesn't grow with every address ever seen. */
    @Scheduled(fixedDelay = 10 * 60 * 1000, initialDelay = 10 * 60 * 1000)
    public void evictExpired() {
        counter.evictExpired(properties.window(), Instant.now(clock));
    }
}

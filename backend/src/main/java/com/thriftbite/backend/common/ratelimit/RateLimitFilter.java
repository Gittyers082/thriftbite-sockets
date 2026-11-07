package com.thriftbite.backend.common.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
 * Per-address limits on the endpoints a script would hammer: log in, sign up,
 * checkout and claim-code entry. Everything else passes straight through.
 *
 * Runs after Spring Security's filters, so a 429 still carries CORS headers
 * and the browser can read it.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private record Rule(String name, String method, Pattern path, ToIntFunction<RateLimitProperties> limit) {
        boolean matches(HttpServletRequest request) {
            return method.equals(request.getMethod()) && path.matcher(request.getRequestURI()).matches();
        }
    }

    private static final List<Rule> RULES = List.of(
            new Rule("login", "POST", Pattern.compile("/api/auth/login"), RateLimitProperties::loginPerWindow),
            new Rule("register", "POST", Pattern.compile("/api/auth/register"), RateLimitProperties::registerPerWindow),
            new Rule("checkout", "POST", Pattern.compile("/api/checkout"), RateLimitProperties::checkoutPerWindow),
            new Rule("claim-code", "POST", Pattern.compile("/api/owner/reservations/[^/]+/complete"),
                    RateLimitProperties::claimCodePerWindow));

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
        Rule rule = RULES.stream().filter(r -> r.matches(request)).findFirst().orElseThrow();
        String key = rule.name() + "|" + clientAddress(request);
        Duration retryAfter = counter.hit(key, rule.limit().applyAsInt(properties), properties.window(), clock.instant());
        if (retryAfter == null) {
            chain.doFilter(request, response);
            return;
        }
        writeTooManyRequests(request, response, retryAfter);
    }

    private String clientAddress(HttpServletRequest request) {
        String header = properties.clientIpHeader();
        if (!header.isBlank()) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) {
                // For X-Forwarded-For style lists, the last entry is the one our own proxy added.
                String[] parts = value.split(",");
                return parts[parts.length - 1].trim();
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
                "detail":"Too many tries from this device. Wait a few minutes and try again.",\
                "instance":"%s","code":"rate_limited"}""".formatted(request.getRequestURI().replace("\"", "")));
    }

    /** Drops finished windows now and then, so memory doesn't grow with every address ever seen. */
    @Scheduled(fixedDelay = 10 * 60 * 1000, initialDelay = 10 * 60 * 1000)
    public void evictExpired() {
        counter.evictExpired(properties.window(), Instant.now(clock));
    }
}

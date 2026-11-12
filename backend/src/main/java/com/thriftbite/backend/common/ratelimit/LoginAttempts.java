package com.thriftbite.backend.common.ratelimit;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * Pauses guessing after several wrong tries in a row: wrong passwords at log in,
 * wrong recovery codes, wrong "current password" on the account page.
 *
 * The caller picks the key. Log in uses two:
 * - username + address, with the normal limit. Someone guessing from their own
 *   address locks only themselves out, not the real owner on another address.
 * - username alone, with a higher limit ({@link #WIDE}), which stops guessing spread
 *   over many addresses. The IP limit in {@link RateLimitFilter} can't do that.
 *
 * Works the same for usernames that don't exist, so a pause doesn't reveal
 * which accounts are real.
 */
@Component
public class LoginAttempts {

    /** How many times the normal limit a username-wide key allows. */
    public static final int WIDE = 4;

    private final RateLimitProperties properties;
    private final Clock clock = Clock.systemUTC();
    private final FixedWindowCounter failures = new FixedWindowCounter();

    public LoginAttempts(RateLimitProperties properties) {
        this.properties = properties;
    }

    /** How long this key must wait, or null if it can try now. */
    public Duration blockedFor(String key) {
        return blockedFor(key, 1);
    }

    /** @param factor multiplies the limit, for keys shared by many addresses */
    public Duration blockedFor(String key, int factor) {
        if (!properties.enabled()) {
            return null;
        }
        return failures.blockedFor(key, properties.loginFailuresBeforeLock() * factor, properties.loginLock(),
                clock.instant());
    }

    /** @return how many tries are left before this key is paused (0 means it is paused now) */
    public int recordFailure(String key) {
        return recordFailure(key, 1);
    }

    public int recordFailure(String key, int factor) {
        int limit = properties.loginFailuresBeforeLock() * factor;
        if (!properties.enabled()) {
            return limit;
        }
        failures.hit(key, limit, properties.loginLock(), clock.instant());
        return Math.max(0, limit - failures.count(key, properties.loginLock(), clock.instant()));
    }

    public void reset(String key) {
        failures.reset(key);
    }
}

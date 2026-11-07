package com.thriftbite.backend.common.ratelimit;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * Pauses one username after several wrong passwords in a row, whatever
 * address they come from. The IP limit in {@link RateLimitFilter} can be
 * dodged with many addresses; this can't.
 *
 * Works the same for usernames that don't exist, so the pause doesn't reveal
 * which accounts are real.
 */
@Component
public class LoginAttempts {

    private final RateLimitProperties properties;
    private final Clock clock = Clock.systemUTC();
    private final FixedWindowCounter failures = new FixedWindowCounter();

    public LoginAttempts(RateLimitProperties properties) {
        this.properties = properties;
    }

    /** How long this username must wait, or null if it can try now. */
    public Duration blockedFor(String username) {
        if (!properties.enabled()) {
            return null;
        }
        return failures.blockedFor(username, properties.loginFailuresBeforeLock(), properties.loginLock(),
                clock.instant());
    }

    public void recordFailure(String username) {
        if (properties.enabled()) {
            failures.hit(username, properties.loginFailuresBeforeLock(), properties.loginLock(), clock.instant());
        }
    }

    public void recordSuccess(String username) {
        failures.reset(username);
    }
}

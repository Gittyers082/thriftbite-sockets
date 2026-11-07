package com.thriftbite.backend.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Limits for {@link RateLimitFilter} and {@link LoginAttempts}, under {@code app.rate-limit}.
 *
 * @param enabled               turn everything off (the API tests do, since they all come from one address)
 * @param clientIpHeader        header the host's proxy puts the real client address in; empty means use the
 *                              connection's address. Only set this when the app is reachable through that proxy alone,
 *                              because anyone can send the header to an app that's reachable directly.
 * @param loginPerWindow        login attempts from one address per window
 * @param registerPerWindow     sign-ups from one address per window
 * @param checkoutPerWindow     checkouts from one address per window
 * @param claimCodePerWindow    claim codes entered from one address per window (a busy counter enters a lot)
 * @param window                length of the window for the limits above
 * @param loginFailuresBeforeLock wrong passwords in a row before one username is paused
 * @param loginLock             how long that pause lasts
 */
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("") String clientIpHeader,
        @DefaultValue("30") int loginPerWindow,
        @DefaultValue("5") int registerPerWindow,
        @DefaultValue("20") int checkoutPerWindow,
        @DefaultValue("60") int claimCodePerWindow,
        @DefaultValue("PT10M") Duration window,
        @DefaultValue("5") int loginFailuresBeforeLock,
        @DefaultValue("PT15M") Duration loginLock) {
}

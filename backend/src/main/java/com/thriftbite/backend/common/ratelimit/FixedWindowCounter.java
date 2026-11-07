package com.thriftbite.backend.common.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts events per key in fixed time windows, in memory.
 *
 * Simple on purpose: one app instance, no Redis. Counts reset when the app
 * restarts, which is fine for slowing down scripts. With several instances
 * behind a load balancer, this would need a shared store instead.
 */
final class FixedWindowCounter {

    /** Stops a flood of random keys (e.g. spoofed usernames) from using up memory. */
    private static final int MAX_KEYS = 50_000;

    private record Window(Instant start, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * Counts one event for {@code key}.
     *
     * @return null if it's within {@code limit}, otherwise how long until the window resets
     */
    Duration hit(String key, int limit, Duration length, Instant now) {
        if (windows.size() > MAX_KEYS) {
            evictExpired(length, now);
            if (windows.size() > MAX_KEYS) {
                windows.clear();
            }
        }
        Window window = windows.compute(key, (k, old) ->
                old == null || !now.isBefore(old.start().plus(length))
                        ? new Window(now, 1)
                        : new Window(old.start(), old.count() + 1));
        if (window.count() <= limit) {
            return null;
        }
        return Duration.between(now, window.start().plus(length));
    }

    /** How long until {@code key}'s window resets, if it's already over {@code limit}; else null. Doesn't count. */
    Duration blockedFor(String key, int limit, Duration length, Instant now) {
        Window window = windows.get(key);
        if (window == null || window.count() < limit || !now.isBefore(window.start().plus(length))) {
            return null;
        }
        return Duration.between(now, window.start().plus(length));
    }

    void reset(String key) {
        windows.remove(key);
    }

    void evictExpired(Duration length, Instant now) {
        windows.entrySet().removeIf(e -> !now.isBefore(e.getValue().start().plus(length)));
    }
}

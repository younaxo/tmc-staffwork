package su.twomc.staffwork.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RateLimiter<K> {
    private final Clock clock;
    private final Duration interval;
    private final Map<K, Instant> attempts = new ConcurrentHashMap<>();

    public RateLimiter(Clock clock, Duration interval) {
        this.clock = clock;
        this.interval = interval;
    }

    public boolean tryAcquire(K key) {
        Instant now = clock.instant();
        java.util.concurrent.atomic.AtomicBoolean acquired = new java.util.concurrent.atomic.AtomicBoolean();
        attempts.compute(key, (ignored, previous) -> {
            if (previous == null || !previous.plus(interval).isAfter(now)) {
                acquired.set(true);
                return now;
            }
            return previous;
        });
        return acquired.get();
    }
}

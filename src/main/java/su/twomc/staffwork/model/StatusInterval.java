package su.twomc.staffwork.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record StatusInterval(WorkStatus status, Instant startedAt, Instant endedAt) {
    public StatusInterval {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(startedAt, "startedAt");
        if (endedAt != null && endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("Конец интервала не может быть раньше начала");
        }
    }

    public boolean isOpen() {
        return endedAt == null;
    }

    public Duration overlap(Instant from, Instant to, Instant now) {
        Instant effectiveEnd = endedAt == null ? now : endedAt;
        Instant start = startedAt.isAfter(from) ? startedAt : from;
        Instant end = effectiveEnd.isBefore(to) ? effectiveEnd : to;
        return end.isAfter(start) ? Duration.between(start, end) : Duration.ZERO;
    }
}

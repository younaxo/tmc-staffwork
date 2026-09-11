package su.twomc.staffwork.model;

import java.time.Duration;

public record WorkStatistics(Duration countedTime, Duration currentSessionTime) {
    public static final WorkStatistics EMPTY = new WorkStatistics(Duration.ZERO, Duration.ZERO);
}

package su.twomc.staffwork.placeholder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import su.twomc.staffwork.model.StatisticsPeriod;
import su.twomc.staffwork.model.WorkStatus;
import su.twomc.staffwork.service.WorkSessionService;

public final class PlaceholderCache {
    private final WorkSessionService workService;
    private final Clock clock;
    private final Map<UUID, Snapshot> cache = new ConcurrentHashMap<>();

    public PlaceholderCache(WorkSessionService workService, Clock clock) {
        this.workService = workService;
        this.clock = clock;
    }

    public CompletableFuture<Void> refresh(UUID uuid) {
        var activeFuture = workService.activeSession(uuid);
        var todayFuture = workService.statistics(uuid, StatisticsPeriod.TODAY);
        var weekFuture = workService.statistics(uuid, StatisticsPeriod.WEEK);
        var monthFuture = workService.statistics(uuid, StatisticsPeriod.MONTH);
        var allFuture = workService.statistics(uuid, StatisticsPeriod.ALL);
        return CompletableFuture.allOf(activeFuture, todayFuture, weekFuture, monthFuture, allFuture)
                .thenRun(() -> {
                    var active = activeFuture.join();
                    cache.put(
                            uuid,
                            new Snapshot(
                                    active.map(session -> session.currentStatus())
                                            .orElse(WorkStatus.OFF_DUTY),
                                    active.map(session -> session.startedAt()).orElse(null),
                                    todayFuture.join().countedTime(),
                                    weekFuture.join().countedTime(),
                                    monthFuture.join().countedTime(),
                                    allFuture.join().countedTime()));
                });
    }

    public Snapshot get(UUID uuid) {
        return cache.getOrDefault(uuid, Snapshot.EMPTY);
    }

    public Duration currentSessionTime(Snapshot snapshot) {
        return snapshot.sessionStartedAt() == null
                ? Duration.ZERO
                : Duration.between(snapshot.sessionStartedAt(), clock.instant()).isNegative()
                        ? Duration.ZERO
                        : Duration.between(snapshot.sessionStartedAt(), clock.instant());
    }

    public record Snapshot(
            WorkStatus status, Instant sessionStartedAt, Duration today, Duration week, Duration month, Duration all) {
        private static final Snapshot EMPTY =
                new Snapshot(WorkStatus.OFF_DUTY, null, Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO);
    }
}

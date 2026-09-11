package su.twomc.staffwork.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;
import su.twomc.staffwork.model.StaffMember;
import su.twomc.staffwork.model.StatisticsPeriod;
import su.twomc.staffwork.model.StatusInterval;
import su.twomc.staffwork.model.WorkSession;
import su.twomc.staffwork.model.WorkStatistics;
import su.twomc.staffwork.model.WorkStatus;
import su.twomc.staffwork.storage.StaffRepository;

public final class WorkSessionService {
    private final StaffRepository repository;
    private final Executor executor;
    private final Clock clock;
    private final String serverId;
    private final ConcurrentHashMap<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();
    private volatile Set<WorkStatus> countedStatuses;
    private volatile ZoneId displayZone;

    public WorkSessionService(
            StaffRepository repository,
            Executor executor,
            Clock clock,
            String serverId,
            Set<WorkStatus> countedStatuses,
            ZoneId displayZone) {
        this.repository = repository;
        this.executor = executor;
        this.clock = clock;
        this.serverId = serverId;
        this.countedStatuses = Set.copyOf(countedStatuses);
        this.displayZone = displayZone;
    }

    public void updateSettings(Set<WorkStatus> statuses, ZoneId zone) {
        countedStatuses = Set.copyOf(statuses);
        displayZone = zone;
    }

    public CompletableFuture<OperationResult<WorkSession>> start(UUID uuid) {
        return locked(uuid, () -> {
            StaffMember member = repository.findStaff(uuid).orElse(null);
            if (member == null) {
                return OperationResult.failure("staff.not-found");
            }
            if (!member.enabled()) {
                return OperationResult.failure("staff.not-enabled");
            }
            if (repository.findActiveSession(uuid).isPresent()) {
                return OperationResult.failure("session.already-active");
            }
            Instant now = clock.instant();
            WorkSession session = new WorkSession(
                    0,
                    uuid,
                    serverId,
                    now,
                    null,
                    WorkStatus.WORKING,
                    List.of(new StatusInterval(WorkStatus.WORKING, now, null)));
            return OperationResult.success("session.started", repository.createSession(session));
        });
    }

    public CompletableFuture<OperationResult<WorkSession>> changeStatus(UUID uuid, WorkStatus status) {
        return locked(uuid, () -> {
            WorkSession session = repository.findActiveSession(uuid).orElse(null);
            if (session == null) {
                return OperationResult.failure("session.not-active");
            }
            WorkSession updated = WorkTimeCalculator.changeStatus(session, status, clock.instant());
            repository.saveSession(updated);
            return OperationResult.success("session.status-changed", updated);
        });
    }

    public CompletableFuture<OperationResult<WorkSession>> stop(UUID uuid) {
        return locked(uuid, () -> {
            WorkSession session = repository.findActiveSession(uuid).orElse(null);
            if (session == null) {
                return OperationResult.failure("session.not-active");
            }
            WorkSession stopped = WorkTimeCalculator.stop(session, clock.instant());
            repository.saveSession(stopped);
            return OperationResult.success("session.stopped", stopped);
        });
    }

    public CompletableFuture<WorkStatistics> statistics(UUID uuid, StatisticsPeriod period) {
        return CompletableFuture.supplyAsync(
                () -> {
                    Instant now = clock.instant();
                    StatisticsPeriod.TimeRange range = period.range(now, displayZone);
                    List<WorkSession> sessions = repository.findSessions(uuid, range.from(), range.to());
                    Duration counted =
                            WorkTimeCalculator.countedTime(sessions, range.from(), range.to(), now, countedStatuses);
                    Duration current = repository
                            .findActiveSession(uuid)
                            .map(session -> WorkTimeCalculator.currentSessionTime(session, now))
                            .orElse(Duration.ZERO);
                    return new WorkStatistics(counted, current);
                },
                executor);
    }

    public CompletableFuture<java.util.Optional<WorkSession>> activeSession(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> repository.findActiveSession(uuid), executor);
    }

    public CompletableFuture<Integer> recoverOpenSessions() {
        return CompletableFuture.supplyAsync(
                () -> {
                    Instant now = clock.instant();
                    List<WorkSession> active = repository.findAllActiveSessions().stream()
                            .filter(session -> session.serverId().equals(serverId))
                            .toList();
                    active.forEach(session -> repository.saveSession(WorkTimeCalculator.stop(session, now)));
                    return active.size();
                },
                executor);
    }

    private <T> CompletableFuture<T> locked(UUID uuid, java.util.function.Supplier<T> operation) {
        return CompletableFuture.supplyAsync(
                () -> {
                    ReentrantLock lock = locks.computeIfAbsent(uuid, ignored -> new ReentrantLock());
                    lock.lock();
                    try {
                        return operation.get();
                    } finally {
                        lock.unlock();
                        if (!lock.hasQueuedThreads()) {
                            locks.remove(uuid, lock);
                        }
                    }
                },
                executor);
    }
}

package su.twomc.staffwork.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import su.twomc.staffwork.model.StatusInterval;
import su.twomc.staffwork.model.WorkSession;
import su.twomc.staffwork.model.WorkStatus;

public final class WorkTimeCalculator {
    private WorkTimeCalculator() {}

    public static Duration countedTime(
            Collection<WorkSession> sessions, Instant from, Instant to, Instant now, Set<WorkStatus> countedStatuses) {
        return sessions.stream()
                .flatMap(session -> session.intervals().stream())
                .filter(interval -> countedStatuses.contains(interval.status()))
                .map(interval -> interval.overlap(from, to, now))
                .reduce(Duration.ZERO, Duration::plus);
    }

    public static Duration currentSessionTime(WorkSession session, Instant now) {
        Instant end = session.endedAt() == null ? now : session.endedAt();
        return end.isAfter(session.startedAt()) ? Duration.between(session.startedAt(), end) : Duration.ZERO;
    }

    public static WorkSession changeStatus(WorkSession session, WorkStatus next, Instant at) {
        if (!session.isOpen()) {
            throw new IllegalStateException("Нельзя изменить статус завершённой сессии");
        }
        var intervals = new java.util.ArrayList<StatusInterval>(session.intervals());
        if (!intervals.isEmpty()) {
            StatusInterval current = intervals.get(intervals.size() - 1);
            if (current.isOpen()) {
                if (current.status() == next) {
                    return session;
                }
                intervals.set(intervals.size() - 1, new StatusInterval(current.status(), current.startedAt(), at));
            }
        }
        intervals.add(new StatusInterval(next, at, null));
        return new WorkSession(
                session.id(), session.staffUuid(), session.serverId(), session.startedAt(), null, next, intervals);
    }

    public static WorkSession stop(WorkSession session, Instant at) {
        if (!session.isOpen()) {
            return session;
        }
        var intervals = new java.util.ArrayList<StatusInterval>(session.intervals());
        if (!intervals.isEmpty()) {
            StatusInterval current = intervals.get(intervals.size() - 1);
            if (current.isOpen()) {
                intervals.set(intervals.size() - 1, new StatusInterval(current.status(), current.startedAt(), at));
            }
        }
        return new WorkSession(
                session.id(),
                session.staffUuid(),
                session.serverId(),
                session.startedAt(),
                at,
                WorkStatus.OFF_DUTY,
                intervals);
    }
}

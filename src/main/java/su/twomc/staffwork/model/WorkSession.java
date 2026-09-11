package su.twomc.staffwork.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record WorkSession(
        long id,
        UUID staffUuid,
        String serverId,
        Instant startedAt,
        Instant endedAt,
        WorkStatus currentStatus,
        List<StatusInterval> intervals) {

    public WorkSession {
        Objects.requireNonNull(staffUuid, "staffUuid");
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(currentStatus, "currentStatus");
        intervals = List.copyOf(intervals);
        if (endedAt != null && endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("Конец сессии не может быть раньше начала");
        }
    }

    public boolean isOpen() {
        return endedAt == null;
    }
}

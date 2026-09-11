package su.twomc.staffwork.support;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import su.twomc.staffwork.model.StaffMember;
import su.twomc.staffwork.model.WorkSession;
import su.twomc.staffwork.storage.StaffRepository;

public final class InMemoryStaffRepository implements StaffRepository {
    private final Map<UUID, StaffMember> staff = new ConcurrentHashMap<>();
    private final Map<Long, WorkSession> sessions = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1);

    @Override
    public void initialize() {}

    @Override
    public Optional<StaffMember> findStaff(UUID uuid) {
        return Optional.ofNullable(staff.get(uuid));
    }

    @Override
    public Optional<StaffMember> findStaffByName(String name) {
        return staff.values().stream()
                .filter(member -> member.lastKnownName().equalsIgnoreCase(name))
                .findFirst();
    }

    @Override
    public List<StaffMember> findAllStaff() {
        return staff.values().stream()
                .sorted(Comparator.comparing(StaffMember::lastKnownName))
                .toList();
    }

    @Override
    public void saveStaff(StaffMember member) {
        staff.put(member.uuid(), member);
    }

    @Override
    public boolean deleteStaff(UUID uuid) {
        return staff.remove(uuid) != null;
    }

    @Override
    public Optional<WorkSession> findActiveSession(UUID uuid) {
        return sessions.values().stream()
                .filter(session -> session.staffUuid().equals(uuid) && session.isOpen())
                .findFirst();
    }

    @Override
    public List<WorkSession> findSessions(UUID uuid, Instant from, Instant to) {
        return sessions.values().stream()
                .filter(session -> session.staffUuid().equals(uuid))
                .filter(session -> session.startedAt().isBefore(to))
                .filter(session ->
                        session.endedAt() == null || session.endedAt().isAfter(from))
                .toList();
    }

    @Override
    public WorkSession createSession(WorkSession session) {
        long id = sequence.getAndIncrement();
        WorkSession created = new WorkSession(
                id,
                session.staffUuid(),
                session.serverId(),
                session.startedAt(),
                session.endedAt(),
                session.currentStatus(),
                session.intervals());
        sessions.put(id, created);
        return created;
    }

    @Override
    public void saveSession(WorkSession session) {
        sessions.put(session.id(), session);
    }

    @Override
    public List<WorkSession> findAllActiveSessions() {
        return sessions.values().stream().filter(WorkSession::isOpen).toList();
    }

    @Override
    public void close() {}
}

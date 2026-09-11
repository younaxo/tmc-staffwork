package su.twomc.staffwork.storage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import su.twomc.staffwork.model.StaffMember;
import su.twomc.staffwork.model.WorkSession;

public interface StaffRepository extends AutoCloseable {
    void initialize() throws StorageException;

    Optional<StaffMember> findStaff(UUID uuid) throws StorageException;

    Optional<StaffMember> findStaffByName(String name) throws StorageException;

    List<StaffMember> findAllStaff() throws StorageException;

    void saveStaff(StaffMember member) throws StorageException;

    boolean deleteStaff(UUID uuid) throws StorageException;

    Optional<WorkSession> findActiveSession(UUID uuid) throws StorageException;

    List<WorkSession> findSessions(UUID uuid, Instant from, Instant to) throws StorageException;

    WorkSession createSession(WorkSession session) throws StorageException;

    void saveSession(WorkSession session) throws StorageException;

    List<WorkSession> findAllActiveSessions() throws StorageException;

    @Override
    void close();
}

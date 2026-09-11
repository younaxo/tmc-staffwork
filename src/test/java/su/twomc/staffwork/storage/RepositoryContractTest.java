package su.twomc.staffwork.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import su.twomc.staffwork.model.StaffMember;
import su.twomc.staffwork.model.StatusInterval;
import su.twomc.staffwork.model.WorkSession;
import su.twomc.staffwork.model.WorkStatus;

class RepositoryContractTest {
    private static final UUID UUID_VALUE = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @TempDir
    Path temporaryDirectory;

    static Stream<Arguments> jdbcTypes() {
        return Stream.of(Arguments.of(StorageType.SQLITE), Arguments.of(StorageType.H2));
    }

    @ParameterizedTest
    @MethodSource("jdbcTypes")
    void jdbcRepositoriesPersistStaffSessionsAndMigrations(StorageType type) {
        Path directory = temporaryDirectory.resolve(type.name().toLowerCase());
        directory.toFile().mkdirs();
        DatabaseSettings settings =
                new DatabaseSettings(type, directory, "localhost", 3306, "test", "user", "", 2, 5000);
        Instant start = Instant.parse("2026-09-11T08:00:00Z");

        try (JdbcStaffRepository repository = new JdbcStaffRepository(settings)) {
            repository.initialize();
            repository.saveStaff(new StaffMember(UUID_VALUE, "Tester", "staff", true, start, null, start));
            WorkSession session = repository.createSession(new WorkSession(
                    0,
                    UUID_VALUE,
                    "test",
                    start,
                    null,
                    WorkStatus.WORKING,
                    List.of(new StatusInterval(WorkStatus.WORKING, start, null))));
            repository.saveSession(su.twomc.staffwork.service.WorkTimeCalculator.stop(session, start.plusSeconds(120)));

            assertEquals(
                    "Tester", repository.findStaff(UUID_VALUE).orElseThrow().lastKnownName());
            assertTrue(repository.findActiveSession(UUID_VALUE).isEmpty());
            assertEquals(
                    1,
                    repository
                            .findSessions(UUID_VALUE, start.minusSeconds(1), start.plusSeconds(300))
                            .size());
        }

        try (JdbcStaffRepository reopened = new JdbcStaffRepository(settings)) {
            reopened.initialize();
            assertTrue(reopened.findStaff(UUID_VALUE).isPresent());
        }
    }

    @Test
    void yamlRepositoryWritesAtomicallyAndCanReopen() {
        Instant start = Instant.parse("2026-09-11T08:00:00Z");
        try (YamlStaffRepository repository = new YamlStaffRepository(temporaryDirectory)) {
            repository.initialize();
            repository.saveStaff(new StaffMember(UUID_VALUE, "YamlUser", "staff", true, start, null, start));
            WorkSession session = repository.createSession(new WorkSession(
                    0,
                    UUID_VALUE,
                    "test",
                    start,
                    null,
                    WorkStatus.WORKING,
                    List.of(new StatusInterval(WorkStatus.WORKING, start, null))));
            repository.saveSession(su.twomc.staffwork.service.WorkTimeCalculator.stop(session, start.plusSeconds(60)));
        }

        assertTrue(java.nio.file.Files.exists(temporaryDirectory.resolve("staff-data.yml")));
        assertFalse(java.nio.file.Files.exists(temporaryDirectory.resolve("staff-data.yml.tmp")));
        try (YamlStaffRepository reopened = new YamlStaffRepository(temporaryDirectory)) {
            reopened.initialize();
            assertEquals(
                    "YamlUser", reopened.findStaff(UUID_VALUE).orElseThrow().lastKnownName());
            assertEquals(
                    1,
                    reopened.findSessions(UUID_VALUE, Instant.EPOCH, Instant.MAX)
                            .size());
        }
    }
}

package su.twomc.staffwork.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import su.twomc.staffwork.model.StaffMember;
import su.twomc.staffwork.model.StatisticsPeriod;
import su.twomc.staffwork.model.WorkStatus;
import su.twomc.staffwork.support.InMemoryStaffRepository;
import su.twomc.staffwork.support.MutableClock;

class WorkSessionServiceTest {
    private final UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final Instant start = Instant.parse("2026-09-11T08:00:00Z");
    private InMemoryStaffRepository repository;
    private MutableClock clock;
    private ExecutorService executor;
    private WorkSessionService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryStaffRepository();
        repository.saveStaff(new StaffMember(uuid, "Tester", "staff", true, start, null, start));
        clock = new MutableClock(start);
        executor = Executors.newFixedThreadPool(8);
        service = new WorkSessionService(
                repository,
                executor,
                clock,
                "test",
                Set.of(WorkStatus.WORKING, WorkStatus.MEETING, WorkStatus.TRAINING),
                ZoneId.of("UTC"));
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void preventsDoubleSession() {
        assertTrue(service.start(uuid).join().success());
        assertFalse(service.start(uuid).join().success());
        assertEquals("session.already-active", service.start(uuid).join().messageKey());
    }

    @Test
    void concurrentStartsCreateExactlyOneSession() {
        var futures = new ArrayList<CompletableFuture<OperationResult<su.twomc.staffwork.model.WorkSession>>>();
        for (int index = 0; index < 30; index++) {
            futures.add(service.start(uuid));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        assertEquals(
                1, futures.stream().filter(future -> future.join().success()).count());
    }

    @Test
    void calculatesStatisticsAcrossStatusChanges() {
        service.start(uuid).join();
        clock.set(start.plusSeconds(3600));
        service.changeStatus(uuid, WorkStatus.BREAK).join();
        clock.set(start.plusSeconds(5400));
        service.changeStatus(uuid, WorkStatus.TRAINING).join();
        clock.set(start.plusSeconds(7200));
        service.stop(uuid).join();

        assertEquals(
                java.time.Duration.ofMinutes(90),
                service.statistics(uuid, StatisticsPeriod.TODAY).join().countedTime());
    }

    @Test
    void recoversOpenSessionAfterRestart() {
        service.start(uuid).join();
        clock.set(start.plusSeconds(600));

        assertEquals(1, service.recoverOpenSessions().join());
        assertTrue(repository.findActiveSession(uuid).isEmpty());
        assertEquals(
                java.time.Duration.ofMinutes(10),
                service.statistics(uuid, StatisticsPeriod.ALL).join().countedTime());
    }
}

package su.twomc.staffwork.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import su.twomc.staffwork.model.StatisticsPeriod;
import su.twomc.staffwork.model.StatusInterval;
import su.twomc.staffwork.model.WorkSession;
import su.twomc.staffwork.model.WorkStatus;

class WorkTimeCalculatorTest {
    private static final UUID STAFF = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void countsOnlyConfiguredStatusesAndOpenInterval() {
        Instant start = Instant.parse("2026-09-11T08:00:00Z");
        Instant now = Instant.parse("2026-09-11T12:00:00Z");
        WorkSession session = session(
                start,
                null,
                List.of(
                        new StatusInterval(WorkStatus.WORKING, start, start.plusSeconds(3600)),
                        new StatusInterval(WorkStatus.BREAK, start.plusSeconds(3600), start.plusSeconds(5400)),
                        new StatusInterval(WorkStatus.MEETING, start.plusSeconds(5400), null)));

        Duration result = WorkTimeCalculator.countedTime(
                List.of(session), start, now, now, Set.of(WorkStatus.WORKING, WorkStatus.MEETING));

        assertEquals(Duration.ofMinutes(210), result);
    }

    @Test
    void clipsIntervalCrossingMidnight() {
        Instant from = Instant.parse("2026-09-11T00:00:00Z");
        Instant to = Instant.parse("2026-09-12T00:00:00Z");
        WorkSession session = session(
                from.minusSeconds(1800),
                to.plusSeconds(1800),
                List.of(new StatusInterval(WorkStatus.WORKING, from.minusSeconds(1800), to.plusSeconds(1800))));

        assertEquals(
                Duration.ofHours(24),
                WorkTimeCalculator.countedTime(List.of(session), from, to, to, Set.of(WorkStatus.WORKING)));
    }

    @Test
    void periodUsesServerTimeZoneAndMondayAsWeekStart() {
        Instant now = Instant.parse("2026-09-13T22:30:00Z");
        ZoneId moscow = ZoneId.of("Europe/Moscow");

        assertEquals(
                Instant.parse("2026-09-13T21:00:00Z"),
                StatisticsPeriod.TODAY.range(now, moscow).from());
        assertEquals(
                Instant.parse("2026-09-13T21:00:00Z"),
                StatisticsPeriod.WEEK.range(now, moscow).from());
        assertEquals(
                Instant.parse("2026-08-31T21:00:00Z"),
                StatisticsPeriod.MONTH.range(now, moscow).from());
    }

    @Test
    void statusTransitionClosesPreviousInterval() {
        Instant start = Instant.parse("2026-09-11T08:00:00Z");
        WorkSession changed = WorkTimeCalculator.changeStatus(
                session(start, null, List.of(new StatusInterval(WorkStatus.WORKING, start, null))),
                WorkStatus.AFK,
                start.plusSeconds(60));

        assertEquals(start.plusSeconds(60), changed.intervals().get(0).endedAt());
        assertEquals(WorkStatus.AFK, changed.currentStatus());
        assertFalse(changed.intervals().get(0).isOpen());
    }

    private static WorkSession session(Instant start, Instant end, List<StatusInterval> intervals) {
        return new WorkSession(
                1,
                STAFF,
                "test",
                start,
                end,
                intervals.get(intervals.size() - 1).status(),
                intervals);
    }
}

package su.twomc.staffwork.placeholder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Test;
import su.twomc.staffwork.model.StaffMember;
import su.twomc.staffwork.model.WorkStatus;
import su.twomc.staffwork.service.WorkSessionService;
import su.twomc.staffwork.support.InMemoryStaffRepository;
import su.twomc.staffwork.support.MutableClock;

class PlaceholderExpansionTest {
    @Test
    void returnsCachedValuesWithoutDatabaseAccessOnRequestThread() {
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000004");
        Instant start = Instant.parse("2026-09-11T08:00:00Z");
        MutableClock clock = new MutableClock(start);
        InMemoryStaffRepository repository = new InMemoryStaffRepository();
        repository.saveStaff(new StaffMember(uuid, "Holder", "staff", true, start, null, start));
        Executor direct = Runnable::run;
        WorkSessionService service =
                new WorkSessionService(repository, direct, clock, "test", Set.of(WorkStatus.WORKING), ZoneId.of("UTC"));
        service.start(uuid).join();
        clock.set(start.plusSeconds(3600));
        PlaceholderCache cache = new PlaceholderCache(service, clock);
        cache.refresh(uuid).join();
        TmcStaffExpansion expansion = new TmcStaffExpansion("0.1", cache, Map.of(WorkStatus.WORKING, "&a"));
        OfflinePlayer player = mock(OfflinePlayer.class);
        when(player.getUniqueId()).thenReturn(uuid);

        assertEquals("В работе", expansion.onRequest(player, "staff_status"));
        assertEquals("&a", expansion.onRequest(player, "staff_status_color"));
        assertEquals("1 ч. 00 мин.", expansion.onRequest(player, "staff_work_time_today"));
        assertEquals("true", expansion.onRequest(player, "staff_is_working"));
    }
}

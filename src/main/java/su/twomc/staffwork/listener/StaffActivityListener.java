package su.twomc.staffwork.listener;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import su.twomc.staffwork.TMCStaffWork;
import su.twomc.staffwork.config.PluginSettings;
import su.twomc.staffwork.model.WorkStatus;

public final class StaffActivityListener implements Listener {
    private final TMCStaffWork plugin;
    private final Clock clock;
    private final Map<UUID, Instant> lastActivity = new ConcurrentHashMap<>();

    public StaffActivityListener(TMCStaffWork plugin, Clock clock) {
        this.plugin = plugin;
        this.clock = clock;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        lastActivity.put(player.getUniqueId(), clock.instant());
        plugin.staffService()
                .find(player.getUniqueId())
                .thenAccept(member -> member.ifPresent(ignored -> {
                    plugin.staffService().recordSeen(player.getUniqueId(), player.getName());
                    plugin.telegram().notifyEvent("staff-join", player.getName());
                    PluginSettings.Automation automation = plugin.settings().automation();
                    if (automation.autoStartOnJoin()) {
                        plugin.workService()
                                .start(player.getUniqueId())
                                .thenCompose(result -> {
                                    if (result.success() && automation.joinStatus() != WorkStatus.WORKING) {
                                        return plugin.workService()
                                                .changeStatus(player.getUniqueId(), automation.joinStatus());
                                    }
                                    return java.util.concurrent.CompletableFuture.completedFuture(result);
                                })
                                .thenRun(() -> plugin.placeholderCache().refresh(player.getUniqueId()));
                    } else {
                        plugin.placeholderCache().refresh(player.getUniqueId());
                    }
                    scheduleAfkCheck(player);
                }));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        lastActivity.remove(player.getUniqueId());
        plugin.telegram().notifyEvent("staff-quit", player.getName());
        switch (plugin.settings().automation().quitBehavior()) {
            case STOP -> plugin.workService().stop(player.getUniqueId());
            case PAUSE -> plugin.workService().changeStatus(player.getUniqueId(), WorkStatus.BREAK);
            case KEEP -> {
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() != null
                && (event.getFrom().getBlockX() != event.getTo().getBlockX()
                        || event.getFrom().getBlockY() != event.getTo().getBlockY()
                        || event.getFrom().getBlockZ() != event.getTo().getBlockZ())) {
            markActive(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        markActive(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            markActive(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        markActive(event.getPlayer());
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        lastActivity.put(event.getPlayer().getUniqueId(), clock.instant());
    }

    private void markActive(Player player) {
        lastActivity.put(player.getUniqueId(), clock.instant());
        if (plugin.settings().automation().returnFromAfk()
                && plugin.placeholderCache().get(player.getUniqueId()).status() == WorkStatus.AFK) {
            plugin.workService()
                    .changeStatus(player.getUniqueId(), WorkStatus.WORKING)
                    .thenRun(() -> plugin.placeholderCache().refresh(player.getUniqueId()));
        }
    }

    private void scheduleAfkCheck(Player player) {
        long delayTicks = plugin.settings().automation().afkThresholdSeconds() * 20L;
        plugin.scheduler()
                .runEntityDelayed(
                        player,
                        () -> {
                            if (!player.isOnline()) {
                                return;
                            }
                            Instant last = lastActivity.getOrDefault(player.getUniqueId(), clock.instant());
                            if (!last.plusSeconds(plugin.settings().automation().afkThresholdSeconds())
                                    .isAfter(clock.instant())) {
                                plugin.workService()
                                        .activeSession(player.getUniqueId())
                                        .thenAccept(active -> active.ifPresent(session -> {
                                            if (session.currentStatus() != WorkStatus.AFK
                                                    && session.currentStatus() != WorkStatus.OFF_DUTY) {
                                                plugin.workService()
                                                        .changeStatus(player.getUniqueId(), WorkStatus.AFK)
                                                        .thenRun(() -> plugin.placeholderCache()
                                                                .refresh(player.getUniqueId()));
                                            }
                                        }));
                            }
                            scheduleAfkCheck(player);
                        },
                        Math.max(20L, delayTicks));
    }
}

package su.twomc.staffwork.config;

import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import su.twomc.staffwork.model.WorkStatus;
import su.twomc.staffwork.storage.DatabaseSettings;

public record PluginSettings(
        String serverId,
        ZoneId zoneId,
        DatabaseSettings database,
        Set<String> ranks,
        Set<WorkStatus> countedStatuses,
        Automation automation,
        Telegram telegram,
        Map<WorkStatus, String> statusColors) {

    public record Automation(
            boolean autoStartOnJoin,
            WorkStatus joinStatus,
            QuitBehavior quitBehavior,
            int afkThresholdSeconds,
            boolean returnFromAfk) {}

    public enum QuitBehavior {
        STOP,
        PAUSE,
        KEEP
    }

    public record Telegram(
            boolean enabled,
            String token,
            long pollingSeconds,
            int linkCodeTtlSeconds,
            int requestCooldownSeconds,
            Set<String> events,
            Map<String, String> templates) {}
}

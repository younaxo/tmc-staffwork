package su.twomc.staffwork.config;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import su.twomc.staffwork.model.WorkStatus;
import su.twomc.staffwork.storage.DatabaseSettings;
import su.twomc.staffwork.storage.StorageType;

public final class ConfigManager {
    private static final int CONFIG_VERSION = 1;

    private final JavaPlugin plugin;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public PluginSettings load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        int version = config.getInt("config-version", 0);
        if (version > CONFIG_VERSION) {
            throw new IllegalStateException("Версия config.yml новее поддерживаемой: " + version);
        }
        if (version < CONFIG_VERSION) {
            plugin.getLogger().warning("Обнаружена старая конфигурация; добавлены безопасные значения по умолчанию");
            config.options().copyDefaults(true);
            config.set("config-version", CONFIG_VERSION);
            plugin.saveConfig();
        }

        ZoneId zone = parseZone(config.getString("general.time-zone", "Europe/Moscow"), plugin.getLogger());
        StorageType type = StorageType.parse(config.getString("storage.type", "SQLITE"));
        Path dataDirectory = plugin.getDataFolder().toPath();
        DatabaseSettings database = new DatabaseSettings(
                type,
                dataDirectory,
                config.getString("storage.mysql.host", "127.0.0.1"),
                config.getInt("storage.mysql.port", 3306),
                config.getString("storage.mysql.database", "tmc_staffwork"),
                config.getString("storage.mysql.username", "staffwork"),
                config.getString("storage.mysql.password", ""),
                bounded(config.getInt("storage.pool.maximum-size", 5), 1, 30),
                bounded(config.getLong("storage.pool.connection-timeout-ms", 10_000), 1_000, 60_000));

        Set<String> ranks = new HashSet<>();
        var rankSection = config.getConfigurationSection("ranks");
        if (rankSection != null) {
            rankSection.getKeys(false).stream()
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .filter(value -> value.matches("[a-z0-9_-]{1,32}"))
                    .forEach(ranks::add);
        }
        if (ranks.isEmpty()) {
            ranks.add("staff");
        }

        Set<WorkStatus> counted = parseStatuses(config.getStringList("work-time.counted-statuses"));
        if (counted.isEmpty()) {
            counted = Set.of(WorkStatus.WORKING, WorkStatus.MEETING, WorkStatus.TRAINING);
        }

        PluginSettings.Automation automation = new PluginSettings.Automation(
                config.getBoolean("automation.join.auto-start-session", false),
                WorkStatus.parse(config.getString("automation.join.status", "OFF_DUTY"))
                        .orElse(WorkStatus.OFF_DUTY),
                parseQuitBehavior(config.getString("automation.quit.behavior", "STOP")),
                bounded(config.getInt("automation.afk.threshold-seconds", 300), 30, 86_400),
                config.getBoolean("automation.afk.return-on-activity", true));

        Set<String> events = Set.copyOf(config.getStringList("telegram.events"));
        Map<String, String> templates = Map.of(
                "session-start", config.getString("telegram.templates.session-start", "{player} начал работу"),
                "session-stop", config.getString("telegram.templates.session-stop", "{player} завершил работу"),
                "staff-join", config.getString("telegram.templates.staff-join", "{player} вошёл на сервер"),
                "staff-quit", config.getString("telegram.templates.staff-quit", "{player} вышел с сервера"));
        PluginSettings.Telegram telegram = new PluginSettings.Telegram(
                config.getBoolean("telegram.enabled", false),
                config.getString("telegram.bot-token", ""),
                bounded(config.getLong("telegram.polling-seconds", 3), 1, 60),
                bounded(config.getInt("telegram.link-code-ttl-seconds", 300), 60, 1800),
                bounded(config.getInt("telegram.request-cooldown-seconds", 30), 5, 300),
                events,
                templates);

        Map<WorkStatus, String> colors = new EnumMap<>(WorkStatus.class);
        for (WorkStatus status : WorkStatus.values()) {
            colors.put(
                    status, config.getString("placeholder-api.status-colors." + status.name(), defaultColor(status)));
        }
        return new PluginSettings(
                validateServerId(config.getString("general.server-id", "default")),
                zone,
                database,
                Set.copyOf(ranks),
                Set.copyOf(counted),
                automation,
                telegram,
                Map.copyOf(colors));
    }

    private static Set<WorkStatus> parseStatuses(java.util.List<String> values) {
        Set<WorkStatus> statuses = java.util.EnumSet.noneOf(WorkStatus.class);
        values.forEach(value -> WorkStatus.parse(value).ifPresent(statuses::add));
        return statuses;
    }

    private static PluginSettings.QuitBehavior parseQuitBehavior(String value) {
        try {
            return PluginSettings.QuitBehavior.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return PluginSettings.QuitBehavior.STOP;
        }
    }

    private static ZoneId parseZone(String value, Logger logger) {
        try {
            return ZoneId.of(value);
        } catch (ZoneRulesException exception) {
            logger.warning("Неизвестный часовой пояс '" + value + "'; используется UTC");
            return ZoneId.of("UTC");
        }
    }

    private static String validateServerId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("general.server-id должен соответствовать [A-Za-z0-9_-]{1,64}");
        }
        return value;
    }

    private static int bounded(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long bounded(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String defaultColor(WorkStatus status) {
        return switch (status) {
            case WORKING -> "&a";
            case AFK -> "&e";
            case OFF_DUTY -> "&c";
            case BREAK, MEETING, TRAINING -> "&7";
        };
    }
}

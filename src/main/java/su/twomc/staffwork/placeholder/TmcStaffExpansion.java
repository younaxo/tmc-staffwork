package su.twomc.staffwork.placeholder;

import java.util.Map;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.twomc.staffwork.model.WorkStatus;
import su.twomc.staffwork.service.DurationFormatter;

public final class TmcStaffExpansion extends PlaceholderExpansion {
    private final String version;
    private final PlaceholderCache cache;
    private volatile Map<WorkStatus, String> colors;

    public TmcStaffExpansion(String version, PlaceholderCache cache, Map<WorkStatus, String> colors) {
        this.version = version;
        this.cache = cache;
        this.colors = Map.copyOf(colors);
    }

    public void updateColors(Map<WorkStatus, String> newColors) {
        colors = Map.copyOf(newColors);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "tmcstaff";
    }

    @Override
    public @NotNull String getAuthor() {
        return "younaxo";
    }

    @Override
    public @NotNull String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String parameters) {
        if (player == null) {
            return "";
        }
        PlaceholderCache.Snapshot snapshot = cache.get(player.getUniqueId());
        return switch (parameters.toLowerCase(java.util.Locale.ROOT)) {
            case "staff_status" -> snapshot.status().displayName();
            case "staff_status_color" -> colors.getOrDefault(snapshot.status(), "&7");
            case "staff_is_working" -> Boolean.toString(snapshot.status() == WorkStatus.WORKING);
            case "staff_work_time_today" -> DurationFormatter.format(snapshot.today());
            case "staff_work_time_week" -> DurationFormatter.format(snapshot.week());
            case "staff_work_time_month" -> DurationFormatter.format(snapshot.month());
            case "staff_total_work_time" -> DurationFormatter.format(snapshot.all());
            case "staff_current_session_time" -> DurationFormatter.format(cache.currentSessionTime(snapshot));
            default -> null;
        };
    }
}

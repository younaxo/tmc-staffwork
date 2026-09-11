package su.twomc.staffwork.placeholder;

import org.bukkit.plugin.java.JavaPlugin;
import su.twomc.staffwork.config.PluginSettings;

public final class PlaceholderHook {
    private PlaceholderHook() {}

    public static PlaceholderController register(JavaPlugin plugin, PlaceholderCache cache, PluginSettings settings) {
        TmcStaffExpansion expansion =
                new TmcStaffExpansion(plugin.getDescription().getVersion(), cache, settings.statusColors());
        if (!expansion.register()) {
            plugin.getLogger().warning("PlaceholderAPI найден, но расширение tmcstaff не зарегистрировано");
            return PlaceholderController.NONE;
        }
        plugin.getLogger().info("Расширение PlaceholderAPI tmcstaff зарегистрировано");
        return expansion::updateColors;
    }
}

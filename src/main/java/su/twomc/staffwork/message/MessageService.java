package su.twomc.staffwork.message;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class MessageService implements AutoCloseable {
    private final JavaPlugin plugin;
    private final BukkitAudiences audiences;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private volatile Map<String, String> messages = Map.of();

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.audiences = BukkitAudiences.create(plugin);
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages_ru.yml");
        if (!file.exists()) {
            plugin.saveResource("messages_ru.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Map<String, String> loaded = new HashMap<>();
        flatten(yaml, "", loaded);
        messages = Map.copyOf(loaded);
    }

    public void send(CommandSender sender, String key, String... replacements) {
        audiences.sender(sender).sendMessage(component(key, replacements));
    }

    public Component component(String key, String... replacements) {
        String template = messages.getOrDefault(key, "<red>Не найдено сообщение: " + key + "</red>");
        TagResolver.Builder tags = TagResolver.builder();
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            tags.resolver(Placeholder.unparsed(replacements[index], replacements[index + 1]));
        }
        try {
            return miniMessage.deserialize(template, tags.build());
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Некорректный MiniMessage в сообщении " + key, exception);
            return Component.text("Ошибка локализации: " + key);
        }
    }

    private static void flatten(
            org.bukkit.configuration.ConfigurationSection section, String prefix, Map<String, String> target) {
        for (String key : section.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (section.isConfigurationSection(key)) {
                ConfigurationSection child = section.getConfigurationSection(key);
                if (child != null) {
                    flatten(child, path, target);
                }
            } else if (section.isString(key)) {
                target.put(path, section.getString(key, ""));
            }
        }
    }

    @Override
    public void close() {
        audiences.close();
    }
}

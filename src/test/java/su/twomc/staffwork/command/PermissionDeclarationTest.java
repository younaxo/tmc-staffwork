package su.twomc.staffwork.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PermissionDeclarationTest {
    @Test
    void everyPermissionUsesRequiredNamespaceAndAdministrativeDefaultsAreSafe() throws IOException {
        String pluginYml;
        try (var stream = getClass().getClassLoader().getResourceAsStream("plugin.yml")) {
            if (stream == null) {
                throw new IOException("plugin.yml не найден");
            }
            pluginYml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        pluginYml
                .lines()
                .map(String::trim)
                .filter(line -> line.startsWith("tmc.staffwork.") && line.endsWith(":"))
                .forEach(line -> assertTrue(line.matches("tmc\\.staffwork\\.[a-z0-9.*_-]+(?:\\.[a-z0-9.*_-]+)*:")));
        assertTrue(pluginYml.contains("tmc.staffwork.admin:"));
        assertFalse(pluginYml.contains(
                "tmc.staffwork.user:\n    description: Базовые команды сотрудника\n    default: true"));
    }
}

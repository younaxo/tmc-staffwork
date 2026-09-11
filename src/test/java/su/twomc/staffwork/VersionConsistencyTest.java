package su.twomc.staffwork;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class VersionConsistencyTest {
    @Test
    void releaseVersionIsConsistentInProjectFiles() throws IOException {
        String expected = "0.1";
        assertTrue(Files.readString(Path.of("build.gradle.kts")).contains("version = \"" + expected + "\""));
        assertTrue(Files.readString(Path.of("CHANGELOG.md")).contains("## " + expected + " "));
        try (var stream = getClass().getClassLoader().getResourceAsStream("plugin.yml")) {
            if (stream == null) {
                throw new IOException("plugin.yml не найден");
            }
            assertTrue(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8).contains("version: '" + expected + "'"));
        }
    }
}

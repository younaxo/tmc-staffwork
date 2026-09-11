package su.twomc.staffwork.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import su.twomc.staffwork.config.PluginSettings;

class TelegramServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsSixDigitSingleUseWindowAndRateLimitsRequests() {
        var executor = Executors.newSingleThreadScheduledExecutor();
        PluginSettings.Telegram settings =
                new PluginSettings.Telegram(true, "test-token", 3, 300, 30, Set.of(), Map.of());
        TelegramService service = new TelegramService(
                settings, temporaryDirectory, executor, Clock.systemUTC(), Logger.getAnonymousLogger());
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000005");

        TelegramService.LinkCode code = service.createLinkCode(uuid).orElseThrow();
        assertTrue(code.value().matches("\\d{6}"));
        assertEquals(300, code.ttlSeconds());
        assertTrue(service.createLinkCode(uuid).isEmpty());

        service.close();
        executor.shutdownNow();
    }
}

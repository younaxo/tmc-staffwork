package su.twomc.staffwork.telegram;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import su.twomc.staffwork.config.PluginSettings;
import su.twomc.staffwork.service.RateLimiter;

public final class TelegramService implements AutoCloseable {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PluginSettings.Telegram settings;
    private final Path linksFile;
    private final ScheduledExecutorService executor;
    private final Clock clock;
    private final Logger logger;
    private final HttpClient client;
    private final byte[] pepper = new byte[32];
    private final Map<String, PendingCode> pendingCodes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> links = new ConcurrentHashMap<>();
    private final RateLimiter<UUID> codeLimiter;
    private final Map<Long, FailedAttempts> failedAttempts = new ConcurrentHashMap<>();
    private final AtomicLong updateOffset = new AtomicLong();
    private final AtomicBoolean running = new AtomicBoolean();

    public TelegramService(
            PluginSettings.Telegram settings,
            Path dataDirectory,
            ScheduledExecutorService executor,
            Clock clock,
            Logger logger) {
        this.settings = settings;
        this.linksFile =
                dataDirectory.resolve("telegram-links.yml").toAbsolutePath().normalize();
        this.executor = executor;
        this.clock = clock;
        this.logger = logger;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .executor(executor)
                .build();
        SECURE_RANDOM.nextBytes(pepper);
        this.codeLimiter = new RateLimiter<>(clock, Duration.ofSeconds(settings.requestCooldownSeconds()));
    }

    public boolean isEnabled() {
        return settings.enabled() && !settings.token().isBlank();
    }

    public void start() {
        loadLinks();
        if (!isEnabled()) {
            return;
        }
        running.set(true);
        poll();
    }

    public Optional<LinkCode> createLinkCode(UUID uuid) {
        if (!isEnabled() || !codeLimiter.tryAcquire(uuid)) {
            return Optional.empty();
        }
        String code = "%06d".formatted(SECURE_RANDOM.nextInt(1_000_000));
        Instant expiresAt = clock.instant().plusSeconds(settings.linkCodeTtlSeconds());
        pendingCodes.put(hash(code), new PendingCode(uuid, expiresAt));
        pendingCodes.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(clock.instant()));
        return Optional.of(new LinkCode(code, settings.linkCodeTtlSeconds()));
    }

    public void unlink(UUID uuid) {
        links.remove(uuid);
        persistLinks();
    }

    public void notifyEvent(String event, String playerName) {
        if (!isEnabled() || !settings.events().contains(event)) {
            return;
        }
        String template = settings.templates().get(event);
        if (template == null || template.isBlank()) {
            return;
        }
        String message = template.replace("{player}", playerName);
        links.values().forEach(chatId -> sendMessage(chatId, message, 0));
    }

    private void poll() {
        if (!running.get()) {
            return;
        }
        String query = "offset=" + updateOffset.get() + "&timeout=" + Math.min(25, settings.pollingSeconds());
        request("getUpdates", query)
                .whenCompleteAsync(
                        (response, error) -> {
                            if (!running.get()) {
                                return;
                            }
                            if (error != null || response.statusCode() / 100 != 2) {
                                logger.warning("Telegram polling временно недоступен; повтор через несколько секунд");
                                executor.schedule(this::poll, 5, TimeUnit.SECONDS);
                                return;
                            }
                            try {
                                processUpdates(response.body());
                            } catch (RuntimeException exception) {
                                logger.warning("Telegram вернул ответ неизвестного формата");
                            }
                            executor.schedule(this::poll, settings.pollingSeconds(), TimeUnit.SECONDS);
                        },
                        executor);
    }

    private void processUpdates(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        if (!root.has("ok") || !root.get("ok").getAsBoolean()) {
            return;
        }
        JsonArray updates = root.getAsJsonArray("result");
        for (JsonElement element : updates) {
            JsonObject update = element.getAsJsonObject();
            updateOffset.accumulateAndGet(update.get("update_id").getAsLong() + 1, Math::max);
            if (!update.has("message")) {
                continue;
            }
            JsonObject message = update.getAsJsonObject("message");
            if (!message.has("text") || !message.has("chat")) {
                continue;
            }
            String text = message.get("text").getAsString().trim();
            long chatId = message.getAsJsonObject("chat").get("id").getAsLong();
            if (text.startsWith("/link ")) {
                acceptLink(chatId, text.substring(6).trim());
            }
        }
    }

    private void acceptLink(long chatId, String code) {
        FailedAttempts attempts = failedAttempts.computeIfAbsent(chatId, ignored -> new FailedAttempts());
        if (!attempts.allow(clock.instant())) {
            sendMessage(chatId, "Слишком много попыток. Повторите позже.", 0);
            return;
        }
        String codeHash = hash(code);
        PendingCode pending = pendingCodes.remove(codeHash);
        if (pending == null || pending.expiresAt().isBefore(clock.instant())) {
            attempts.fail(clock.instant());
            sendMessage(chatId, "Код недействителен или истёк.", 0);
            return;
        }
        failedAttempts.remove(chatId);
        links.put(pending.uuid(), chatId);
        persistLinks();
        sendMessage(chatId, "Аккаунт успешно привязан к TMC StaffWork.", 0);
    }

    private void sendMessage(long chatId, String text, int attempt) {
        String query = "chat_id=" + chatId + "&text=" + encode(text);
        request("sendMessage", query)
                .whenCompleteAsync(
                        (response, error) -> {
                            boolean failed = error != null || response.statusCode() / 100 != 2;
                            if (failed && running.get() && attempt < 3) {
                                executor.schedule(
                                        () -> sendMessage(chatId, text, attempt + 1), 1L << attempt, TimeUnit.SECONDS);
                            }
                        },
                        executor);
    }

    private CompletableFuture<HttpResponse<String>> request(String method, String query) {
        URI uri = URI.create("https://api.telegram.org/bot" + settings.token() + "/" + method);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(35))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(query))
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String hash(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(pepper);
            return Base64.getEncoder().encodeToString(digest.digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 недоступен", exception);
        }
    }

    private void loadLinks() {
        if (!Files.exists(linksFile)) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(linksFile.toFile());
        var section = yaml.getConfigurationSection("links");
        if (section != null) {
            section.getKeys(false).forEach(key -> links.put(UUID.fromString(key), section.getLong(key)));
        }
    }

    private synchronized void persistLinks() {
        YamlConfiguration yaml = new YamlConfiguration();
        links.forEach((uuid, chatId) -> yaml.set("links." + uuid, chatId));
        Path temporary = linksFile.resolveSibling(linksFile.getFileName() + ".tmp");
        try {
            Files.createDirectories(linksFile.getParent());
            Files.writeString(temporary, yaml.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, linksFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, linksFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            logger.warning("Не удалось сохранить привязки Telegram");
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        running.set(false);
        Arrays.fill(pepper, (byte) 0);
        pendingCodes.clear();
    }

    public record LinkCode(String value, int ttlSeconds) {}

    private record PendingCode(UUID uuid, Instant expiresAt) {}

    private static final class FailedAttempts {
        private int count;
        private Instant blockedUntil = Instant.EPOCH;

        synchronized boolean allow(Instant now) {
            return !blockedUntil.isAfter(now);
        }

        synchronized void fail(Instant now) {
            count++;
            if (count >= 5) {
                blockedUntil = now.plusSeconds(300);
                count = 0;
            }
        }
    }
}

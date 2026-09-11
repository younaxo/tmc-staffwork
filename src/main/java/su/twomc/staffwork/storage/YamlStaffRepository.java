package su.twomc.staffwork.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import su.twomc.staffwork.model.StaffMember;
import su.twomc.staffwork.model.StatusInterval;
import su.twomc.staffwork.model.WorkSession;
import su.twomc.staffwork.model.WorkStatus;

public final class YamlStaffRepository implements StaffRepository {
    private static final int DATA_VERSION = 1;

    private final Path dataFile;
    private final Map<UUID, StaffMember> staff = new HashMap<>();
    private final Map<Long, WorkSession> sessions = new HashMap<>();
    private long nextSessionId = 1;

    public YamlStaffRepository(Path dataDirectory) {
        this.dataFile = dataDirectory.toAbsolutePath().normalize().resolve("staff-data.yml");
    }

    @Override
    public synchronized void initialize() {
        try {
            Files.createDirectories(dataFile.getParent());
            if (!Files.exists(dataFile)) {
                persist();
                return;
            }
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile.toFile());
            int version = yaml.getInt("data-version", 0);
            if (version > DATA_VERSION) {
                throw new StorageException("Версия YAML-данных новее поддерживаемой: " + version);
            }
            if (version < DATA_VERSION) {
                Files.copy(
                        dataFile,
                        dataFile.resolveSibling("staff-data.backup-" + System.currentTimeMillis() + ".yml"),
                        StandardCopyOption.COPY_ATTRIBUTES);
            }
            loadStaff(yaml.getConfigurationSection("staff"));
            loadSessions(yaml.getConfigurationSection("sessions"));
            if (version < DATA_VERSION) {
                persist();
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw new StorageException("Не удалось загрузить YAML-хранилище", exception);
        }
    }

    private void loadStaff(ConfigurationSection root) {
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            UUID uuid = UUID.fromString(key);
            staff.put(
                    uuid,
                    new StaffMember(
                            uuid,
                            section.getString("last-name", "Unknown"),
                            section.getString("rank", "staff"),
                            section.getBoolean("enabled", true),
                            instant(section.getString("added-at")),
                            nullableUuid(section.getString("added-by")),
                            nullableInstant(section.getString("last-seen-at"))));
        }
    }

    private void loadSessions(ConfigurationSection root) {
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            long id = Long.parseLong(key);
            List<StatusInterval> intervals = new ArrayList<>();
            List<Map<?, ?>> serializedIntervals = section.getMapList("intervals");
            for (Map<?, ?> values : serializedIntervals) {
                intervals.add(new StatusInterval(
                        WorkStatus.valueOf(String.valueOf(values.get("status"))),
                        instant(String.valueOf(values.get("started-at"))),
                        nullableInstant((String) values.get("ended-at"))));
            }
            sessions.put(
                    id,
                    new WorkSession(
                            id,
                            UUID.fromString(section.getString("staff-uuid", "")),
                            section.getString("server-id", "default"),
                            instant(section.getString("started-at")),
                            nullableInstant(section.getString("ended-at")),
                            WorkStatus.valueOf(section.getString("current-status", "OFF_DUTY")),
                            intervals));
            nextSessionId = Math.max(nextSessionId, id + 1);
        }
    }

    @Override
    public synchronized Optional<StaffMember> findStaff(UUID uuid) {
        return Optional.ofNullable(staff.get(uuid));
    }

    @Override
    public synchronized Optional<StaffMember> findStaffByName(String name) {
        return staff.values().stream()
                .filter(member -> member.lastKnownName().equalsIgnoreCase(name))
                .findFirst();
    }

    @Override
    public synchronized List<StaffMember> findAllStaff() {
        return staff.values().stream()
                .sorted(Comparator.comparing(StaffMember::lastKnownName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    public synchronized void saveStaff(StaffMember member) {
        staff.put(member.uuid(), member);
        persist();
    }

    @Override
    public synchronized boolean deleteStaff(UUID uuid) {
        boolean removed = staff.remove(uuid) != null;
        if (removed) {
            persist();
        }
        return removed;
    }

    @Override
    public synchronized Optional<WorkSession> findActiveSession(UUID uuid) {
        return sessions.values().stream()
                .filter(session -> session.staffUuid().equals(uuid) && session.isOpen())
                .max(Comparator.comparing(WorkSession::startedAt));
    }

    @Override
    public synchronized List<WorkSession> findSessions(UUID uuid, Instant from, Instant to) {
        return sessions.values().stream()
                .filter(session -> session.staffUuid().equals(uuid))
                .filter(session -> session.startedAt().isBefore(to))
                .filter(session ->
                        session.endedAt() == null || session.endedAt().isAfter(from))
                .sorted(Comparator.comparing(WorkSession::startedAt))
                .toList();
    }

    @Override
    public synchronized WorkSession createSession(WorkSession session) {
        WorkSession created = new WorkSession(
                nextSessionId++,
                session.staffUuid(),
                session.serverId(),
                session.startedAt(),
                session.endedAt(),
                session.currentStatus(),
                session.intervals());
        sessions.put(created.id(), created);
        persist();
        return created;
    }

    @Override
    public synchronized void saveSession(WorkSession session) {
        if (!sessions.containsKey(session.id())) {
            throw new StorageException("Рабочая сессия не найдена");
        }
        sessions.put(session.id(), session);
        persist();
    }

    @Override
    public synchronized List<WorkSession> findAllActiveSessions() {
        return sessions.values().stream().filter(WorkSession::isOpen).toList();
    }

    private void persist() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("data-version", DATA_VERSION);
        for (StaffMember member : staff.values()) {
            String path = "staff." + member.uuid();
            yaml.set(path + ".last-name", member.lastKnownName());
            yaml.set(path + ".rank", member.rank());
            yaml.set(path + ".enabled", member.enabled());
            yaml.set(path + ".added-at", member.addedAt().toString());
            yaml.set(
                    path + ".added-by",
                    member.addedBy() == null ? null : member.addedBy().toString());
            yaml.set(
                    path + ".last-seen-at",
                    member.lastSeenAt() == null ? null : member.lastSeenAt().toString());
        }
        for (WorkSession session : sessions.values()) {
            String path = "sessions." + session.id();
            yaml.set(path + ".staff-uuid", session.staffUuid().toString());
            yaml.set(path + ".server-id", session.serverId());
            yaml.set(path + ".started-at", session.startedAt().toString());
            yaml.set(
                    path + ".ended-at",
                    session.endedAt() == null ? null : session.endedAt().toString());
            yaml.set(path + ".current-status", session.currentStatus().name());
            List<Map<String, String>> intervals = session.intervals().stream()
                    .map(interval -> {
                        Map<String, String> values = new java.util.LinkedHashMap<>();
                        values.put("status", interval.status().name());
                        values.put("started-at", interval.startedAt().toString());
                        values.put(
                                "ended-at",
                                interval.endedAt() == null
                                        ? null
                                        : interval.endedAt().toString());
                        return values;
                    })
                    .toList();
            yaml.set(path + ".intervals", intervals);
        }
        Path temporary = dataFile.resolveSibling(dataFile.getFileName() + ".tmp");
        try {
            Files.writeString(temporary, yaml.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, dataFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, dataFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new StorageException("Не удалось атомарно сохранить YAML-данные", exception);
        }
    }

    private static Instant instant(String value) {
        Instant result = nullableInstant(value);
        if (result == null) {
            throw new IllegalArgumentException("Отсутствует обязательная временная метка");
        }
        return result;
    }

    private static Instant nullableInstant(String value) {
        try {
            return value == null || value.isBlank() ? null : Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Некорректная временная метка", exception);
        }
    }

    private static UUID nullableUuid(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    @Override
    public void close() {
        // Данные записываются атомарно при каждом изменении.
    }
}

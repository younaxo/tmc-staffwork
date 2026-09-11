package su.twomc.staffwork.storage;

import java.nio.file.Path;

public record DatabaseSettings(
        StorageType type,
        Path dataDirectory,
        String host,
        int port,
        String database,
        String username,
        String password,
        int maximumPoolSize,
        long connectionTimeoutMs) {}

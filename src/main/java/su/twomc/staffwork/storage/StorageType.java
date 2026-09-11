package su.twomc.staffwork.storage;

import java.util.Locale;

public enum StorageType {
    YAML,
    SQLITE,
    H2,
    MYSQL;

    public static StorageType parse(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return SQLITE;
        }
    }
}

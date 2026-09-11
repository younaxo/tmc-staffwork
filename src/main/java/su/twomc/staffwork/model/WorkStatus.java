package su.twomc.staffwork.model;

import java.util.Locale;
import java.util.Optional;

public enum WorkStatus {
    WORKING("В работе"),
    AFK("AFK"),
    OFF_DUTY("Не в работе"),
    BREAK("Перерыв"),
    MEETING("Совещание"),
    TRAINING("Обучение");

    private final String displayName;

    WorkStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<WorkStatus> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_')));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}

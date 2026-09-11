package su.twomc.staffwork.command;

import java.util.Optional;
import java.util.UUID;

public final class CommandInput {
    private CommandInput() {}

    public static Optional<UUID> uuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return Optional.empty();
        }
    }

    public static boolean validPlayerName(String value) {
        return value != null && value.matches("[A-Za-z0-9_]{3,16}");
    }
}

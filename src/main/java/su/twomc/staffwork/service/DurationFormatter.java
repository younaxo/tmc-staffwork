package su.twomc.staffwork.service;

import java.time.Duration;

public final class DurationFormatter {
    private DurationFormatter() {}

    public static String format(Duration duration) {
        long seconds = Math.max(0, duration.getSeconds());
        long days = seconds / 86_400;
        long hours = (seconds % 86_400) / 3_600;
        long minutes = (seconds % 3_600) / 60;
        if (days > 0) {
            return "%d д. %02d ч. %02d мин.".formatted(days, hours, minutes);
        }
        return "%d ч. %02d мин.".formatted(hours, minutes);
    }
}

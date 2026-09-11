package su.twomc.staffwork.model;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Optional;

public enum StatisticsPeriod {
    TODAY,
    WEEK,
    MONTH,
    ALL;

    public TimeRange range(Instant now, ZoneId zone) {
        ZonedDateTime localNow = now.atZone(zone);
        LocalDate date = localNow.toLocalDate();
        Instant from =
                switch (this) {
                    case TODAY -> date.atStartOfDay(zone).toInstant();
                    case WEEK ->
                        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                                .atStartOfDay(zone)
                                .toInstant();
                    case MONTH -> date.withDayOfMonth(1).atStartOfDay(zone).toInstant();
                    case ALL -> Instant.EPOCH;
                };
        return new TimeRange(from, now);
    }

    public static Optional<StatisticsPeriod> parse(String value) {
        try {
            return Optional.of(valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return Optional.empty();
        }
    }

    public record TimeRange(Instant from, Instant to) {}
}

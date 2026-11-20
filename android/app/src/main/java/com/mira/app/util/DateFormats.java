package com.mira.app.util;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class DateFormats {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter
                    .ofPattern("MMM d, HH:mm", Locale.getDefault())
                    .withZone(ZoneId.systemDefault());

    private DateFormats() {
    }

    public static String shortDateTime(String isoInstant) {
        if (isoInstant == null || isoInstant.isBlank()) {
            return "";
        }

        try {
            return FORMATTER.format(Instant.parse(isoInstant));
        } catch (RuntimeException exception) {
            return "";
        }
    }

    /**
     * Whole minutes since the timestamp, clamped at zero for clock skew, or -1
     * when the value cannot be parsed. Callers decide how recent is "recent".
     */
    public static long minutesSince(String isoInstant) {
        if (isoInstant == null || isoInstant.isBlank()) {
            return -1;
        }

        try {
            long minutes = Duration.between(
                    Instant.parse(isoInstant),
                    Instant.now()
            ).toMinutes();

            return Math.max(0, minutes);
        } catch (RuntimeException exception) {
            return -1;
        }
    }
}
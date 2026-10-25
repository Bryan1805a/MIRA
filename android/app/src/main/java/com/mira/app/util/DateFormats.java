package com.mira.app.util;

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
}
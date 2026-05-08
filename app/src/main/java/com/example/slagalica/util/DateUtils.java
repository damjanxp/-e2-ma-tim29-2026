package com.example.slagalica.util;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Simple date formatting helpers.
 */
public final class DateUtils {

    private static final String DEFAULT_PATTERN = "dd.MM.yyyy HH:mm";

    private DateUtils() {
    }

    /**
     * Formats a timestamp into a readable date/time string.
     */
    @NonNull
    public static String formatTimestamp(long timestampMillis) {
        SimpleDateFormat formatter = new SimpleDateFormat(DEFAULT_PATTERN, Locale.getDefault());
        return formatter.format(new Date(timestampMillis));
    }
}


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
    private static final String DAY_KEY_PATTERN = "yyyy-MM-dd";

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

    /**
     * Vraća stabilan ključ kalendarskog dana (npr. "2026-07-03") u vremenskoj
     * zoni uređaja — koristi se za dnevni reset (npr. dnevni izazovi).
     * {@code Locale.US} garantuje ASCII cifre bez obzira na jezik uređaja.
     */
    @NonNull
    public static String todayKey() {
        return new SimpleDateFormat(DAY_KEY_PATTERN, Locale.US).format(new Date());
    }
}


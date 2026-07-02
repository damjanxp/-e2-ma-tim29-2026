package com.example.slagalica.util;

import androidx.annotation.NonNull;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Pretvara naziv regiona u bezbedan ključ za Realtime Database.
 *
 * <p>RTDB ključevi ne smeju sadržati karaktere {@code . # $ [ ] /}, pa se
 * naziv regiona svodi na mala slova bez dijakritika, a svi ostali
 * nedozvoljeni karakteri se uklanjaju. Primer: {@code "Beograd" -> "beograd"}.</p>
 */
public final class RegionKey {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern INVALID_CHARS = Pattern.compile("[^a-z0-9]");

    private RegionKey() {
        // ne instancira se
    }

    /**
     * Generiše RTDB-bezbedan ključ na osnovu naziva regiona.
     *
     * @param regionName naziv regiona (npr. "Beograd")
     * @return ključ pogodan za korišćenje kao segment RTDB putanje
     */
    @NonNull
    public static String toKey(@NonNull String regionName) {
        String normalized = Normalizer.normalize(regionName, Normalizer.Form.NFD);
        String withoutDiacritics = DIACRITICS.matcher(normalized).replaceAll("");
        String lower = withoutDiacritics.toLowerCase();
        return INVALID_CHARS.matcher(lower).replaceAll("");
    }
}

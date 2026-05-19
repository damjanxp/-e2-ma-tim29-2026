package com.example.slagalica.logic.games;

import androidx.annotation.NonNull;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Statička utility klasa sa poslovnom logikom za igru "Korak po korak".
 *
 * <p>Ne sme da instancira stanje — sve metode su {@code static}.
 * Ne importuje ništa iz {@code ui/} paketa.</p>
 */
public final class KorakPoKorakLogic {

    /** Regex koji uklanja Unicode kombinacijske oznake (dijakritike) nakon NFD normalizacije. */
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    /** Sprečava instanciranje utility klase. */
    private KorakPoKorakLogic() {
    }

    // -------------------------------------------------------------------------
    // Bodovanje
    // -------------------------------------------------------------------------

    /**
     * Vraća broj bodova koje igrač osvaja ako pogodi odgovor na datom koraku.
     *
     * <p>Formula: {@code 20 - 2 * korakIndex}, gde je {@code korakIndex} 0-baziran
     * (0 = prvi hint, najteži; 6 = sedmi hint, najlakši).</p>
     *
     * <table border="1">
     *   <tr><th>korakIndex</th><th>Bodovi</th></tr>
     *   <tr><td>0</td><td>20</td></tr>
     *   <tr><td>1</td><td>18</td></tr>
     *   <tr><td>2</td><td>16</td></tr>
     *   <tr><td>3</td><td>14</td></tr>
     *   <tr><td>4</td><td>12</td></tr>
     *   <tr><td>5</td><td>10</td></tr>
     *   <tr><td>6</td><td>8</td></tr>
     * </table>
     *
     * @param korakIndex 0-bazirani indeks koraka (hint-a) na kom je pogođen odgovor
     * @return broj bodova (8–20), ili 0 ako je indeks izvan opsega [0, 6]
     */
    public static int bodoviZaPogodakUKoraku(int korakIndex) {
        if (korakIndex < 0 || korakIndex > 6) {
            return 0;
        }
        return 20 - 2 * korakIndex;
    }

    /**
     * Vraća broj bodova koji se dodeljuju igraču kada protivnik pogodi
     * odgovor umesto njega (preuzimanje bodova).
     *
     * @return uvek 5 bodova
     */
    public static int bodoviZaPreuzimanje() {
        return 5;
    }

    // -------------------------------------------------------------------------
    // Provera odgovora
    // -------------------------------------------------------------------------

    /**
     * Proverava da li je korisnički odgovor tačan poređenjem sa rešenjem.
     *
     * <p>Poređenje je:
     * <ul>
     *   <li><b>case-insensitive</b> — "Jabuka" i "jabuka" su iste</li>
     *   <li><b>trim</b> — vodeći i završni razmaci se ignorišu</li>
     *   <li><b>diacritic-insensitive</b> — š→s, ž→z, đ→dj, č→c, ć→c;
     *       implementirano kroz Unicode NFD normalizaciju + uklanjanje
     *       combining diacritical marks</li>
     * </ul>
     * </p>
     *
     * <p>Primer: {@code tacanOdgovor("Šuma", "suma")} vraća {@code true}.</p>
     *
     * @param userAnswer odgovor koji je uneo korisnik (može biti null)
     * @param resenje    tačno rešenje iz Firestore dokumenta (može biti null)
     * @return {@code true} ako su odgovori ekvivalentni, {@code false} u suprotnom
     */
    public static boolean tacanOdgovor(String userAnswer, @NonNull String resenje) {
        if (userAnswer == null || userAnswer.trim().isEmpty()) {
            return false;
        }

        // Poseban slučaj: đ i Đ → "dj" jer NFD ne rastavlja đ na d + kombinacijski znak
        String normalizedUser    = normalizuj(userAnswer);
        String normalizedResenje = normalizuj(resenje);

        return normalizedUser.equalsIgnoreCase(normalizedResenje);
    }

    // -------------------------------------------------------------------------
    // Pomoćne metode
    // -------------------------------------------------------------------------

    /**
     * Normalizuje string za poređenje bez dijakritika:
     * <ol>
     *   <li>Trim-uje vodeće/završne razmake.</li>
     *   <li>Zamenjuje {@code đ}/{@code Đ} sa {@code dj}/{@code DJ} (NFD ih ne rastavlja).</li>
     *   <li>NFD normalizacija (rastavljanje znakova s dijakriticima na base + combining).</li>
     *   <li>Uklanjanje svih combining diacritical marks regex-om.</li>
     * </ol>
     *
     * @param s string za normalizaciju
     * @return normalizovani string, uvek malim slovima
     */
    private static String normalizuj(@NonNull String s) {
        String result = s.trim();

        // đ/Đ → dj (NFD ne rastavlja đ jer nije kompozit d+diakritik)
        result = result.replace('đ', 'd').replace('Đ', 'D');

        // NFD normalizacija: š → s + kombinacijski hačekov znak, itd.
        result = Normalizer.normalize(result, Normalizer.Form.NFD);

        // Ukloni sve combining diacritical marks (U+0300–U+036F)
        result = COMBINING_MARKS.matcher(result).replaceAll("");

        return result.toLowerCase();
    }
}


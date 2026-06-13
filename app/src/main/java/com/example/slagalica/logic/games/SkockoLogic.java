package com.example.slagalica.logic.games;

import java.util.Random;

/**
 * Bodovanje i hint-logika igre "Skočko".
 *
 * Hint-i se prikazuju sortirano (crveni → žuti → prazni) — ne otkrivaju
 * koja pozicija odgovara kojoj boji, samo ukupan broj.
 */
public final class SkockoLogic {

    public static final int NUM_SYMBOLS  = 6;
    public static final int COMBO_LEN    = 4;
    public static final int MAX_ATTEMPTS = 6;

    public static final int HINT_EMPTY  = 0;
    public static final int HINT_YELLOW = 1;
    public static final int HINT_RED    = 2;

    public static final int OPPONENT_SCORE = 10;

    private SkockoLogic() {}

    /**
     * Vraća niz od 4 hint-a sortiranih kao [crveni…, žuti…, prazni…].
     * Algoritam je identičan Wordle-u (višestruka pojavljivanja simbola
     * se ispravno tretiraju).
     */
    public static int[] computeHints(int[] guess, int[] secret) {
        boolean[] secretUsed = new boolean[COMBO_LEN];
        boolean[] guessUsed  = new boolean[COMBO_LEN];
        int reds = 0, yellows = 0;

        for (int i = 0; i < COMBO_LEN; i++) {
            if (guess[i] == secret[i]) {
                reds++;
                secretUsed[i] = true;
                guessUsed[i]  = true;
            }
        }
        for (int i = 0; i < COMBO_LEN; i++) {
            if (guessUsed[i]) continue;
            for (int j = 0; j < COMBO_LEN; j++) {
                if (!secretUsed[j] && guess[i] == secret[j]) {
                    yellows++;
                    secretUsed[j] = true;
                    break;
                }
            }
        }

        int[] hints = new int[COMBO_LEN];
        int k = 0;
        for (int i = 0; i < reds;    i++) hints[k++] = HINT_RED;
        for (int i = 0; i < yellows; i++) hints[k++] = HINT_YELLOW;
        while (k < COMBO_LEN)             hints[k++] = HINT_EMPTY;
        return hints;
    }

    public static boolean isCorrect(int[] hints) {
        for (int h : hints) if (h != HINT_RED) return false;
        return true;
    }

    /** Bodovi za tačan pogodak u datom pokušaju (0-based index). */
    public static int mainScore(int attemptIndex) {
        if (attemptIndex < 2) return 20;
        if (attemptIndex < 4) return 15;
        return 10;
    }

    /** Nasumična tajna kombinacija dužine {@link #COMBO_LEN}. */
    public static int[] randomSecret(Random random) {
        int[] s = new int[COMBO_LEN];
        for (int i = 0; i < COMBO_LEN; i++) s[i] = random.nextInt(NUM_SYMBOLS);
        return s;
    }
}

package com.example.slagalica.logic.games;

/**
 * Bodovanje igre "Asocijacije":
 *
 *  - Rešenje kolone = 2 + broj neotkrivenih polja u toj koloni
 *  - Finalno rešenje = 7 + za svaku nerešenu kolonu: (2 + neotkrivena polja)
 *
 * Primer: otkriveno 1 polje u koloni A, odmah pogođeno finalno rešenje:
 *   finalno: 7
 *   kolona A (1 otkriveno): 2 + 3 = 5
 *   kolone B, C, D (0 otkriveno): 3 × (2+4) = 18
 *   ukupno: 30
 */
public final class AsocijacijeLogic {

    public static final int NUM_COLS = 4;
    public static final int NUM_ROWS = 4;

    private AsocijacijeLogic() {}

    /** Bodovi za tačno pogođeno rešenje kolone. */
    public static int columnScore(int revealedInCol) {
        return 2 + (NUM_ROWS - revealedInCol);
    }

    /**
     * Bodovi za tačno pogođeno finalno rešenje.
     * Uključuje automatske bodove za sve nerešene kolone.
     *
     * @param revealedPerCol broj otkrivenih polja po koloni
     * @param colSolved       koje kolone su već rešene
     */
    public static int finalScore(int[] revealedPerCol, boolean[] colSolved) {
        int total = 7;
        for (int c = 0; c < NUM_COLS; c++) {
            if (!colSolved[c]) {
                total += 2 + (NUM_ROWS - revealedPerCol[c]);
            }
        }
        return total;
    }
}

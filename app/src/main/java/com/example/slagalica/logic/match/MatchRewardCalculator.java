package com.example.slagalica.logic.match;

/**
 * Statička utility klasa sa poslovnom logikom za nagrade nakon završene partije
 * ("3. Igranje partija").
 *
 * <p>Ne sme da instancira stanje — sve metode su {@code static}.
 * Ne importuje ništa iz {@code ui/} ili {@code data/} paketa (čista logika).</p>
 *
 * <p>Pravila po specifikaciji:</p>
 * <ul>
 *   <li>Pobednik dobija 10 zvezdi + po 1 zvezdu za svakih 40 osvojenih bodova.</li>
 *   <li>Gubitnik gubi 10 zvezdi, ali i dobija po 1 zvezdu za svakih 40 osvojenih bodova.</li>
 *   <li>Broj zvezda nikad ne pada ispod nule (ko nema zvezde ne može ih izgubiti).</li>
 *   <li>Svakih 50 osvojenih zvezda donosi igraču 1 token.</li>
 * </ul>
 */
public final class MatchRewardCalculator {

    /** Osnovni broj zvezda koji pobednik dobija, odnosno gubitnik gubi. */
    private static final int BASE_STARS = 10;

    /** Broj osvojenih bodova potreban za dodatnu zvezdu. */
    private static final int POINTS_PER_STAR = 40;

    /** Broj zvezda potreban za jedan token. */
    private static final int STARS_PER_TOKEN = 50;

    /** Sprečava instanciranje utility klase. */
    private MatchRewardCalculator() {
    }

    // -------------------------------------------------------------------------
    // Zvezde
    // -------------------------------------------------------------------------

    /**
     * Vraća broj zvezda koje pobednik osvaja.
     *
     * <p>Formula: {@code 10 + (myPoints / 40)} (celobrojno deljenje).</p>
     *
     * <p>Primer: pobeda sa 150 bodova → {@code 10 + 150/40 = 10 + 3 = 13} zvezdi.</p>
     *
     * @param myPoints ukupan broj bodova koje je igrač osvojio u partiji
     * @return broj zvezda koje se dodaju pobedniku (uvek ≥ 10)
     */
    public static int starsForWinner(int myPoints) {
        return BASE_STARS + (myPoints / POINTS_PER_STAR);
    }

    /**
     * Vraća promenu (delta) broja zvezda za gubitnika — negativna osnova
     * uz bonus za osvojene bodove.
     *
     * <p>Formula: {@code -10 + (myPoints / 40)} (celobrojno deljenje).
     * Rezultat može biti negativan ili pozitivan.</p>
     *
     * <p>Primer: poraz sa 100 bodova → {@code -10 + 100/40 = -10 + 2 = -8} zvezdi.</p>
     *
     * @param myPoints ukupan broj bodova koje je igrač osvojio u partiji
     * @return promena broja zvezda (može biti negativna ili pozitivna)
     */
    public static int starsDeltaForLoser(int myPoints) {
        return -BASE_STARS + (myPoints / POINTS_PER_STAR);
    }

    /**
     * Primenjuje promenu zvezda na trenutno stanje, uz donju granicu na nuli.
     *
     * <p>Igrač koji nema zvezde ne može ih izgubiti, pa rezultat nikad
     * nije negativan.</p>
     *
     * @param currentStars trenutan broj zvezda igrača
     * @param delta        promena zvezda (npr. iz {@link #starsDeltaForLoser(int)})
     * @return novi broj zvezda, uvek ≥ 0
     */
    public static int applyStarFloor(int currentStars, int delta) {
        return Math.max(0, currentStars + delta);
    }

    // -------------------------------------------------------------------------
    // Tokeni
    // -------------------------------------------------------------------------

    /**
     * Vraća broj NOVIH tokena koje igrač zaslužuje jer je ukupan broj zvezda
     * prešao dodatne pragove od po 50 zvezda.
     *
     * <p>Računa se po broju pređenih pragova:
     * {@code (starsAfter / 50) - (starsBefore / 50)}, ograničeno na ≥ 0
     * (pad broja zvezda ne oduzima već dodeljene tokene).</p>
     *
     * <p>Primeri: {@code tokensFromStars(45, 55) = 1};
     * {@code tokensFromStars(10, 40) = 0}.</p>
     *
     * @param starsBefore broj zvezda pre dodele nagrada
     * @param starsAfter  broj zvezda posle dodele nagrada
     * @return broj novih tokena (uvek ≥ 0)
     */
    public static int tokensFromStars(int starsBefore, int starsAfter) {
        int earned = (starsAfter / STARS_PER_TOKEN) - (starsBefore / STARS_PER_TOKEN);
        return Math.max(0, earned);
    }
}

package com.example.slagalica.logic.league;

/**
 * Statička utility klasa sa poslovnom logikom za napredovanje kroz lige
 * ("6. Napredovanje kroz lige").
 *
 * <p>Ne sme da instancira stanje — sve metode su {@code static}.
 * Ne importuje ništa iz {@code ui/} ili {@code data/} paketa (čista logika).</p>
 *
 * <p>Pravila po specifikaciji:</p>
 * <ul>
 *   <li>Postoji nulta liga + 5 liga (ukupno 6, vidi {@code Constants#LEAGUE_COUNT}).</li>
 *   <li>Za prvu ligu potrebno je 100 zvezda; svaka naredna liga zahteva duplo
 *       više ukupnih zvezda od prethodne (200, 400, 800, 1600).</li>
 *   <li>Igrač automatski ulazi ili ispada iz lige čim njegov ukupan broj zvezda
 *       pređe ili padne ispod praga (nema histereze).</li>
 *   <li>Svaka liga donosi po dodatni 1 dnevni token (liga 3 → +3 dodatna tokena).</li>
 *   <li>Ako se igrač ne plasira na mesečnoj rang listi (nije odigrao nijednu
 *       partiju u ciklusu), gubi 30% zvezda na kraju ciklusa.</li>
 * </ul>
 */
public final class LeagueLogic {

    /** Prag ukupnih zvezda potreban za ulazak u ligu na istom indeksu. Indeks 0 = nulta liga. */
    private static final int[] LEAGUE_THRESHOLDS = {0, 100, 200, 400, 800, 1600};

    /** Osnovan broj dnevnih žetona (liga 0), pre dodatka po ligi. */
    private static final int BASE_DAILY_TOKENS = 5;

    /** Deo zvezda koji igrač zadržava ako se ne plasira na mesečnoj rang listi (gubi 30%). */
    private static final double MONTHLY_RELEGATION_RETAIN = 0.7;

    /** Sprečava instanciranje utility klase. */
    private LeagueLogic() {
    }

    /**
     * Vraća indeks lige (0–5) kojoj igrač pripada na osnovu ukupnog broja zvezda.
     *
     * @param totalStars ukupan broj zvezda igrača
     * @return indeks lige, 0 (nulta liga) do {@code LEAGUE_THRESHOLDS.length - 1}
     */
    public static int leagueForStars(int totalStars) {
        int league = 0;
        for (int i = 0; i < LEAGUE_THRESHOLDS.length; i++) {
            if (totalStars >= LEAGUE_THRESHOLDS[i]) {
                league = i;
            }
        }
        return league;
    }

    /**
     * Vraća broj zvezda potreban za ulazak u zadatu ligu.
     *
     * @param league indeks lige
     * @return prag zvezda; za poslednju ligu nema sledeće pa se vraća njen sopstveni prag
     */
    public static int thresholdForLeague(int league) {
        int idx = Math.max(0, Math.min(league, LEAGUE_THRESHOLDS.length - 1));
        return LEAGUE_THRESHOLDS[idx];
    }

    /**
     * Vraća broj zvezda potreban za sledeću ligu, ili -1 ako je igrač već u
     * najvišoj ligi.
     *
     * @param currentLeague trenutna liga igrača
     * @return prag sledeće lige, ili -1 ako nema sledeće
     */
    public static int thresholdForNextLeague(int currentLeague) {
        int next = currentLeague + 1;
        if (next >= LEAGUE_THRESHOLDS.length) {
            return -1;
        }
        return LEAGUE_THRESHOLDS[next];
    }

    /**
     * Vraća broj dnevnih žetona za igrača u zadatoj ligi.
     *
     * <p>Formula: {@code 5 + league} (npr. treća liga → 5 + 3 = 8 tokena/dan).</p>
     *
     * @param league trenutna liga igrača
     * @return broj žetona koji se dodeljuju svakog dana
     */
    public static int dailyTokensForLeague(int league) {
        return BASE_DAILY_TOKENS + Math.max(0, league);
    }

    /**
     * Primenjuje kaznu od 30% zvezda za neplasiranje na mesečnoj rang listi.
     *
     * @param currentStars trenutan broj zvezda igrača
     * @return novi broj zvezda (zaokruženo na manje), uvek ≥ 0
     */
    public static int applyMonthlyRelegationPenalty(int currentStars) {
        return (int) Math.floor(currentStars * MONTHLY_RELEGATION_RETAIN);
    }
}

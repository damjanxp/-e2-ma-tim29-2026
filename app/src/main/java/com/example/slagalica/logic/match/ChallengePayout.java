package com.example.slagalica.logic.match;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Statička utility klasa sa poslovnom logikom za raspodelu pota nakon
 * završenog izazova ("9. Izazov").
 *
 * <p>Ne sme da instancira stanje — sve metode su {@code static}.
 * Ne importuje ništa iz {@code ui/} ili {@code data/} paketa (čista logika).</p>
 *
 * <p>Pravila po specifikaciji:</p>
 * <ul>
 *   <li>Pobednik (najviši rezultat) dobija 75% od ukupnog pota u zvezdama i žetonima.</li>
 *   <li>Drugoplasirani dobija nazad sopstveni ulog (bez dobitka ni gubitka).</li>
 *   <li>Svi ostali ne dobijaju ništa.</li>
 *   <li>Nerešen rezultat se razrešava po vremenu završetka — ko je ranije završio,
 *       rangira se više.</li>
 * </ul>
 */
public final class ChallengePayout {

    /** Deo pota koji dobija pobednik izazova. */
    private static final double WINNER_SHARE = 0.75;

    /** Sprečava instanciranje utility klase. */
    private ChallengePayout() {
    }

    /** Ishod raspodele za jednog učesnika — broj zvezda i žetona koje dobija. */
    public static final class Payout {
        private final int stars;
        private final int tokens;

        public Payout(int stars, int tokens) {
            this.stars = stars;
            this.tokens = tokens;
        }

        public int getStars() { return stars; }
        public int getTokens() { return tokens; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Payout)) return false;
            Payout other = (Payout) o;
            return stars == other.stars && tokens == other.tokens;
        }

        @Override
        public int hashCode() {
            return 31 * stars + tokens;
        }

        @Override
        public String toString() {
            return "Payout{stars=" + stars + ", tokens=" + tokens + '}';
        }
    }

    /**
     * Izračunava raspodelu pota po učesniku izazova.
     *
     * <p>Rangiranje: prvo po rezultatu (opadajuće), a nerešeno po vremenu
     * završetka (rastuće — ko je ranije završio je bolje rangiran).</p>
     *
     * @param scores        rezultat svakog učesnika (ključ: uid)
     * @param finishedAt    vreme završetka svakog učesnika u ms (ključ: uid),
     *                      koristi se samo za razrešenje nerešenog rezultata
     * @param totalPotStars ukupan broj zvezda u potu (zbir uloga svih učesnika)
     * @param totalPotTokens ukupan broj žetona u potu (zbir uloga svih učesnika)
     * @param stakeStars    ulog jednog učesnika u zvezdama (za refundaciju drugoplasiranom)
     * @param stakeTokens   ulog jednog učesnika u žetonima (za refundaciju drugoplasiranom)
     * @return mapa uid -> {@link Payout}; učesnici bez nagrade imaju {@code Payout(0, 0)}
     */
    public static Map<String, Payout> compute(Map<String, Integer> scores,
                                              Map<String, Long> finishedAt,
                                              int totalPotStars, int totalPotTokens,
                                              int stakeStars, int stakeTokens) {
        List<String> ranked = new ArrayList<>(scores.keySet());
        ranked.sort((uidA, uidB) -> {
            int scoreCompare = Integer.compare(scores.get(uidB), scores.get(uidA));
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            long finishedA = finishedAt.getOrDefault(uidA, Long.MAX_VALUE);
            long finishedB = finishedAt.getOrDefault(uidB, Long.MAX_VALUE);
            return Long.compare(finishedA, finishedB);
        });

        Map<String, Payout> result = new HashMap<>();
        for (String uid : ranked) {
            result.put(uid, new Payout(0, 0));
        }

        if (ranked.isEmpty()) {
            return result;
        }

        String winner = ranked.get(0);
        int winnerStars = (int) Math.floor(totalPotStars * WINNER_SHARE);
        int winnerTokens = (int) Math.floor(totalPotTokens * WINNER_SHARE);
        result.put(winner, new Payout(winnerStars, winnerTokens));

        if (ranked.size() > 1) {
            String runnerUp = ranked.get(1);
            result.put(runnerUp, new Payout(stakeStars, stakeTokens));
        }

        return result;
    }
}

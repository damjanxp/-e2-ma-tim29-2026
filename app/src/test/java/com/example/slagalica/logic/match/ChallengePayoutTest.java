package com.example.slagalica.logic.match;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Lokalni unit test za {@link ChallengePayout} — proverava pravila raspodele
 * pota iz specifikacije ("9. Izazov").
 */
public class ChallengePayoutTest {

    // ---- 2 učesnika, bez nerešenog rezultata --------------------------------

    @Test
    public void twoParticipants_higherScoreWins() {
        Map<String, Integer> scores = new HashMap<>();
        scores.put("A", 100);
        scores.put("B", 50);
        Map<String, Long> finishedAt = new HashMap<>();
        finishedAt.put("A", 1000L);
        finishedAt.put("B", 900L);

        // ulog 10 zvezda / 2 žetona po učesniku -> pot = 20 zvezda / 4 žetona
        Map<String, ChallengePayout.Payout> result =
                ChallengePayout.compute(scores, finishedAt, 20, 4, 10, 2);

        assertEquals(new ChallengePayout.Payout(15, 3), result.get("A")); // 20*0.75=15, 4*0.75=3
        assertEquals(new ChallengePayout.Payout(10, 2), result.get("B")); // refundacija uloga
    }

    // ---- 3 učesnika, nerešen rezultat za prvo mesto -------------------------

    @Test
    public void threeParticipants_tieForFirst_resolvedByEarlierFinish() {
        Map<String, Integer> scores = new HashMap<>();
        scores.put("A", 100);
        scores.put("B", 100);
        scores.put("C", 30);
        Map<String, Long> finishedAt = new HashMap<>();
        finishedAt.put("A", 1000L);
        finishedAt.put("B", 500L); // isti rezultat kao A, ali završio ranije -> pobednik
        finishedAt.put("C", 2000L);

        // ulog 10 zvezda / 2 žetona po učesniku -> pot = 30 zvezda / 6 žetona
        Map<String, ChallengePayout.Payout> result =
                ChallengePayout.compute(scores, finishedAt, 30, 6, 10, 2);

        assertEquals(new ChallengePayout.Payout(22, 4), result.get("B")); // 30*0.75=22.5->22, 6*0.75=4.5->4
        assertEquals(new ChallengePayout.Payout(10, 2), result.get("A")); // drugoplasiran (isti skor, kasnije)
        assertEquals(new ChallengePayout.Payout(0, 0), result.get("C"));
    }

    // ---- 4 učesnika, nerešen rezultat za drugo mesto ------------------------

    @Test
    public void fourParticipants_tieForSecond_resolvedByEarlierFinish() {
        Map<String, Integer> scores = new HashMap<>();
        scores.put("A", 200);
        scores.put("B", 100);
        scores.put("C", 100);
        scores.put("D", 50);
        Map<String, Long> finishedAt = new HashMap<>();
        finishedAt.put("A", 100L);
        finishedAt.put("B", 300L);
        finishedAt.put("C", 200L); // isti rezultat kao B, ali završio ranije -> drugoplasiran
        finishedAt.put("D", 400L);

        // ulog 5 zvezda / 1 žeton po učesniku -> pot = 20 zvezda / 4 žetona
        Map<String, ChallengePayout.Payout> result =
                ChallengePayout.compute(scores, finishedAt, 20, 4, 5, 1);

        assertEquals(new ChallengePayout.Payout(15, 3), result.get("A")); // pobednik: 20*0.75=15, 4*0.75=3
        assertEquals(new ChallengePayout.Payout(5, 1), result.get("C"));  // drugoplasiran (isti skor, ranije)
        assertEquals(new ChallengePayout.Payout(0, 0), result.get("B"));
        assertEquals(new ChallengePayout.Payout(0, 0), result.get("D"));
    }
}

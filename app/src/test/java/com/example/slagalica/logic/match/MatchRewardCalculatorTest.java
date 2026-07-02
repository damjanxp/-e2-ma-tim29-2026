package com.example.slagalica.logic.match;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Lokalni unit test za {@link MatchRewardCalculator} — proverava pravila
 * nagrada iz specifikacije ("3. Igranje partija").
 */
public class MatchRewardCalculatorTest {

    // ---- Pobednik ----------------------------------------------------------

    @Test
    public void winner_150points_gives13stars() {
        // 10 + 150/40 = 10 + 3 = 13
        assertEquals(13, MatchRewardCalculator.starsForWinner(150));
    }

    @Test
    public void winner_zeroPoints_gives10stars() {
        assertEquals(10, MatchRewardCalculator.starsForWinner(0));
    }

    // ---- Gubitnik ----------------------------------------------------------

    @Test
    public void loser_100points_gives8starDeficit() {
        // -10 + 100/40 = -10 + 2 = -8
        assertEquals(-8, MatchRewardCalculator.starsDeltaForLoser(100));
    }

    @Test
    public void loser_manyPoints_canBePositive() {
        // -10 + 480/40 = -10 + 12 = 2
        assertEquals(2, MatchRewardCalculator.starsDeltaForLoser(480));
    }

    // ---- Donja granica (floor) --------------------------------------------

    @Test
    public void floor_neverBelowZero() {
        // 5 + (-8) = -3 -> 0
        assertEquals(0, MatchRewardCalculator.applyStarFloor(5, -8));
    }

    @Test
    public void floor_normalDeltaApplies() {
        assertEquals(15, MatchRewardCalculator.applyStarFloor(2, 13));
    }

    // ---- Tokeni od zvezda --------------------------------------------------

    @Test
    public void tokens_crossingThreshold_grantsOne() {
        assertEquals(1, MatchRewardCalculator.tokensFromStars(45, 55));
    }

    @Test
    public void tokens_noThresholdCrossed_grantsZero() {
        assertEquals(0, MatchRewardCalculator.tokensFromStars(10, 40));
    }

    @Test
    public void tokens_dropInStars_grantsZero() {
        assertEquals(0, MatchRewardCalculator.tokensFromStars(60, 30));
    }

    @Test
    public void tokens_multipleThresholds() {
        // 30/50=0 -> 130/50=2  => 2 nova tokena
        assertEquals(2, MatchRewardCalculator.tokensFromStars(30, 130));
    }
}

package com.example.slagalica.logic.league;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Lokalni unit test za {@link LeagueLogic} — proverava pravila napredovanja
 * kroz lige iz specifikacije ("6. Napredovanje kroz lige").
 */
public class LeagueLogicTest {

    // ---- Određivanje lige na osnovu zvezda ---------------------------------

    @Test
    public void zeroStars_isNultaLiga() {
        assertEquals(0, LeagueLogic.leagueForStars(0));
    }

    @Test
    public void belowFirstThreshold_staysNultaLiga() {
        assertEquals(0, LeagueLogic.leagueForStars(99));
    }

    @Test
    public void exactThreshold_entersLeague() {
        assertEquals(1, LeagueLogic.leagueForStars(100));
        assertEquals(2, LeagueLogic.leagueForStars(200));
        assertEquals(3, LeagueLogic.leagueForStars(400));
        assertEquals(4, LeagueLogic.leagueForStars(800));
        assertEquals(5, LeagueLogic.leagueForStars(1600));
    }

    @Test
    public void specExample_104starsIsFirstLeague() {
        // Primer iz specifikacije: 104 zvezde -> prva liga
        assertEquals(1, LeagueLogic.leagueForStars(104));
    }

    @Test
    public void specExample_dropTo95starsBackToNultaLiga() {
        // Primer iz specifikacije: pad na 95 zvezda -> nulta liga
        assertEquals(0, LeagueLogic.leagueForStars(95));
    }

    @Test
    public void aboveHighestThreshold_staysInHighestLeague() {
        assertEquals(5, LeagueLogic.leagueForStars(10_000));
    }

    // ---- Dnevni žetoni po ligi ----------------------------------------------

    @Test
    public void dailyTokens_nultaLiga_isFive() {
        assertEquals(5, LeagueLogic.dailyTokensForLeague(0));
    }

    @Test
    public void dailyTokens_specExample_thirdLeagueIsEight() {
        // Primer iz specifikacije: treća liga -> 5 + 3 = 8 tokena
        assertEquals(8, LeagueLogic.dailyTokensForLeague(3));
    }

    // ---- Kazna za neplasiranje na mesečnoj rang listi ------------------------

    @Test
    public void monthlyRelegation_specExample_430to301() {
        // Primer iz specifikacije: 430 zvezda -> 301 (30% gubitak, zaokruženo na manje)
        assertEquals(301, LeagueLogic.applyMonthlyRelegationPenalty(430));
    }

    @Test
    public void monthlyRelegation_zeroStaysZero() {
        assertEquals(0, LeagueLogic.applyMonthlyRelegationPenalty(0));
    }
}

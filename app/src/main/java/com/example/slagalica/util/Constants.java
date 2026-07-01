package com.example.slagalica.util;

/**
 * Globalne konstante aplikacije — nazivi Firestore kolekcija,
 * putanje u Realtime Database i parametri igara.
 */
public final class Constants {

    private Constants() {
        // ne instancira se
    }

    // ==== Firestore kolekcije ====
    public static final String COLLECTION_USERS     = "users";
    public static final String COLLECTION_KO_ZNA_ZNA = "koZnaZna";
    public static final String COLLECTION_SPOJNICE  = "spojnice";
    public static final String COLLECTION_LEADERBOARD_CYCLES = "leaderboardCycles";

    // ==== Realtime Database ====
    // Baza je u regionu europe-west1; google-services.json ne sadrži adresu baze,
    // pa je zadajemo eksplicitno da se SDK ne poveže na pogrešan podrazumevani region.
    public static final String RTDB_URL =
            "https://slagalica-ma2025-default-rtdb.europe-west1.firebasedatabase.app";
    public static final String RTDB_MATCHMAKING = "matchmaking";
    public static final String RTDB_MATCHES     = "matches";

    // ==== Intent ekstre ====
    public static final String EXTRA_MATCH_ID      = "extra_match_id";
    public static final String EXTRA_OPPONENT_UID  = "extra_opponent_uid";
    public static final String EXTRA_OPPONENT_NAME = "extra_opponent_name";
    public static final String EXTRA_IS_PLAYER_ONE = "extra_is_player_one";
    public static final String EXTRA_MY_SCORE       = "extra_my_score";
    public static final String EXTRA_OPPONENT_SCORE = "extra_opponent_score";

    // Kumulativni bodovi koji se prenose kroz lanac igara
    public static final String EXTRA_MY_KZZ       = "extra_my_kzz";
    public static final String EXTRA_OPP_KZZ      = "extra_opp_kzz";
    public static final String EXTRA_MY_SPOJNICE  = "extra_my_spojnice";
    public static final String EXTRA_OPP_SPOJNICE = "extra_opp_spojnice";
    public static final String EXTRA_MY_MOJ_BROJ  = "extra_my_moj_broj";
    public static final String EXTRA_OPP_MOJ_BROJ = "extra_opp_moj_broj";
    public static final String EXTRA_MY_KORAK     = "extra_my_korak";
    public static final String EXTRA_OPP_KORAK    = "extra_opp_korak";

    // Kumulativni bodovi koji se prenose kroz lanac igara
    public static final String EXTRA_MY_ASOCIJACIJE  = "extra_my_asocijacije";
    public static final String EXTRA_OPP_ASOCIJACIJE = "extra_opp_asocijacije";
    public static final String EXTRA_MY_SKOCKO       = "extra_my_skocko";
    public static final String EXTRA_OPP_SKOCKO      = "extra_opp_skocko";

    // ==== Identifikatori igara (za setGameResult) ====
    public static final String GAME_KZZ        = "kzz";
    public static final String GAME_SPOJNICE   = "spojnice";
    public static final String GAME_MOJ_BROJ   = "mojBroj";
    public static final String GAME_KORAK      = "korakPoKorak";
    public static final String GAME_ASOCIJACIJE = "asocijacije";
    public static final String GAME_SKOCKO     = "skocko";

    // ==== Skočko — faze runde ====
    public static final String SKOCKO_PHASE_MAIN   = "MAIN";
    public static final String SKOCKO_PHASE_CHANCE = "OPPONENT_CHANCE";
    public static final String SKOCKO_PHASE_DONE   = "DONE";

    // ==== Korak po korak — faze runde ====
    public static final String KORAK_PHASE_ACTIVE  = "ACTIVE";
    public static final String KORAK_PHASE_CHANCE  = "OPPONENT_CHANCE";
    public static final String KORAK_PHASE_DONE    = "DONE";

    // ==== Ko zna zna ====
    public static final int KZZ_QUESTION_COUNT   = 5;
    public static final int KZZ_QUESTION_TIME_MS = 5_000;
    public static final int KZZ_POINTS_CORRECT   = 10;
    public static final int KZZ_POINTS_WRONG     = -5;

    // ==== Spojnice ====
    public static final int SPOJNICE_ROUNDS        = 2;
    public static final int SPOJNICE_PAIRS         = 5;
    public static final int SPOJNICE_ROUND_TIME_MS = 30_000;
    public static final int SPOJNICE_POINTS_PER_PAIR = 2;

    // ==== Profil ====
    public static final int AVATAR_COUNT = 6;
    public static final int LEAGUE_COUNT = 6; // nulta + 5 liga

    // ==== Rang lista ====
    public static final String LEADERBOARD_TYPE_WEEKLY  = "weekly";
    public static final String LEADERBOARD_TYPE_MONTHLY = "monthly";
    public static final int    LEADERBOARD_TOP_N = 10;
    /** Rang lista se automatski osvežava dok je ekran otvoren. */
    public static final long   LEADERBOARD_REFRESH_INTERVAL_MS = 120_000L; // 2 minuta
    /** Nagrade u žetonima po plasmanu — indeks 0 = 1. mesto, ..., indeks 9 = 10. mesto. */
    public static final int[] LEADERBOARD_WEEKLY_REWARDS  = {5, 3, 2, 1, 1, 1, 1, 1, 1, 1};
    public static final int[] LEADERBOARD_MONTHLY_REWARDS = {10, 6, 4, 2, 2, 2, 2, 2, 2, 2};
}

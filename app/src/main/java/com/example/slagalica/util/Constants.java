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
}

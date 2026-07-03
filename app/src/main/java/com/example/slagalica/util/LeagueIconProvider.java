package com.example.slagalica.util;

import com.example.slagalica.R;

/**
 * Mapira indeks lige (0-5, vidi {@link com.example.slagalica.logic.league.LeagueLogic})
 * na lokalni drawable resurs ikonice ("6. Napredovanje kroz lige" — specifikacija 6.a
 * traži proizvoljnu ikonicu za svaku ligu).
 */
public final class LeagueIconProvider {

    private static final int[] ICONS = {
            R.drawable.ic_league_0, // Nulta liga
            R.drawable.ic_league_1, // Bronzana liga
            R.drawable.ic_league_2, // Srebrna liga
            R.drawable.ic_league_3, // Zlatna liga
            R.drawable.ic_league_4, // Platinasta liga
            R.drawable.ic_league_5  // Dijamantska liga
    };

    private LeagueIconProvider() {
        // ne instancira se
    }

    /** Vraća drawable resurs ikonice za zadatu ligu (van opsega → nulta liga). */
    public static int getDrawableRes(int league) {
        if (league < 0 || league >= ICONS.length) {
            return ICONS[0];
        }
        return ICONS[league];
    }
}

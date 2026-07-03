package com.example.slagalica.ui.dailychallenge;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.example.slagalica.R;
import com.example.slagalica.util.Constants;

/**
 * Prikazni podaci (naslov, opis, ikona) za svaki dnevni izazov iz
 * {@link Constants#DAILY_CHALLENGE_IDS} — jedino mesto koje UI mora dopuniti
 * kada se doda nov izazov (vidi uputstvo u klasnom komentaru
 * {@link DailyChallengesActivity}).
 */
final class DailyChallengeCatalog {

    /** Prikazni podaci jednog izazova. */
    static final class Entry {
        @StringRes final int titleRes;
        @StringRes final int descRes;
        @NonNull final String icon;

        Entry(@StringRes int titleRes, @StringRes int descRes, @NonNull String icon) {
            this.titleRes = titleRes;
            this.descRes = descRes;
            this.icon = icon;
        }
    }

    private DailyChallengeCatalog() {
        // ne instancira se
    }

    /** Vraća prikazne podatke za dati ID, ili {@code null} ako nije registrovan ovde. */
    @Nullable
    static Entry entryFor(@NonNull String challengeId) {
        switch (challengeId) {
            case Constants.DAILY_CHALLENGE_WIN_MATCH:
                return new Entry(R.string.daily_challenge_title_win_match,
                        R.string.daily_challenge_desc_win_match, "⚔️");
            case Constants.DAILY_CHALLENGE_WIN_TOURNAMENT_MATCH:
                return new Entry(R.string.daily_challenge_title_win_tournament,
                        R.string.daily_challenge_desc_win_tournament, "🏆");
            default:
                return null;
        }
    }
}

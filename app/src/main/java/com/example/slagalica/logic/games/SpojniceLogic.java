package com.example.slagalica.logic.games;

import androidx.annotation.NonNull;

import com.example.slagalica.data.model.SpojniceAttempt;
import com.example.slagalica.util.Constants;

import java.util.Map;

/**
 * Pravila igre "Spojnice" (specifikacija, igra 2):
 * <ul>
 *   <li>2 runde po 30 sekundi, svaku rundu započinje po jedan igrač;</li>
 *   <li>svaki povezani pojam nosi 2 boda (maksimalno 10 po rundi);</li>
 *   <li>nakon što igrač koji počinje prođe kroz svih pet pojmova, preostale
 *       nepovezane pojmove dobija drugi igrač (30 sekundi).</li>
 * </ul>
 */
public final class SpojniceLogic {

    /** Faza runde: na potezu je igrač koji započinje rundu. */
    public static final int PHASE_STARTER = 1;
    /** Faza runde: drugi igrač povezuje preostale pojmove. */
    public static final int PHASE_SECOND = 2;
    /** Faza runde: runda je završena. */
    public static final int PHASE_DONE = 3;

    private SpojniceLogic() {
        // ne instancira se
    }

    /**
     * UID igrača koji započinje rundu — prvu rundu player1, drugu player2.
     */
    @NonNull
    public static String starterUid(int round, @NonNull String player1Uid,
                                    @NonNull String player2Uid) {
        return round == 0 ? player1Uid : player2Uid;
    }

    /** Ukupni bodovi igrača {@code uid} iz pokušaja jedne runde (+2 po tačnoj vezi). */
    public static int pointsFor(@NonNull String uid,
                                @NonNull Map<Integer, SpojniceAttempt> attempts) {
        int points = 0;
        for (SpojniceAttempt attempt : attempts.values()) {
            if (attempt.isOk() && uid.equals(attempt.getUid())) {
                points += Constants.SPOJNICE_POINTS_PER_PAIR;
            }
        }
        return points;
    }

    /** Broj levih pojmova koje je aktivni igrač već obradio u zadatoj fazi. */
    public static int attemptedCount(@NonNull Map<Integer, SpojniceAttempt> attempts) {
        return attempts.size();
    }

    /** Da li su svi pojmovi povezani (tačno) — runda se tada odmah završava. */
    public static boolean allConnected(@NonNull Map<Integer, SpojniceAttempt> attempts) {
        if (attempts.size() < Constants.SPOJNICE_PAIRS) {
            return false;
        }
        for (SpojniceAttempt attempt : attempts.values()) {
            if (!attempt.isOk()) {
                return false;
            }
        }
        return true;
    }

    /** Da li je levi pojam {@code leftIdx} već uspešno povezan. */
    public static boolean isConnected(int leftIdx,
                                      @NonNull Map<Integer, SpojniceAttempt> attempts) {
        SpojniceAttempt attempt = attempts.get(leftIdx);
        return attempt != null && attempt.isOk();
    }
}

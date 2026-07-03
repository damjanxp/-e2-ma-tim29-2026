package com.example.slagalica.data.model;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Dnevno stanje izazova jednog korisnika — koji su izazovi završeni
 * {@code date}-a i da li je bonus za "sve izazove" već isplaćen. Nije Firebase
 * POJO (mapa sa dinamičkim ključevima po izazovu); parsira se ručno u
 * {@code DailyChallengeRepository}.
 */
public class DailyChallengeState {

    @NonNull public final String date;
    @NonNull public final Map<String, Boolean> completed = new HashMap<>();
    public boolean bonusClaimed;

    public DailyChallengeState(@NonNull String date) {
        this.date = date;
    }

    public boolean isCompleted(@NonNull String challengeId) {
        return Boolean.TRUE.equals(completed.get(challengeId));
    }

    /** True kada su svi zadati izazovi (po id-u) završeni; false ako je niz prazan. */
    public boolean allCompleted(@NonNull String[] challengeIds) {
        if (challengeIds.length == 0) {
            return false;
        }
        for (String id : challengeIds) {
            if (!isCompleted(id)) {
                return false;
            }
        }
        return true;
    }
}

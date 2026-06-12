package com.example.slagalica.logic.games;

import androidx.annotation.NonNull;

import com.example.slagalica.data.model.KzzAnswer;
import com.example.slagalica.util.Constants;

import java.util.Map;

/**
 * Pravila bodovanja igre "Ko zna zna" (specifikacija, igra 1):
 * <ul>
 *   <li>tačan odgovor +10, netačan −5, bez odgovora 0;</li>
 *   <li>ako oba igrača odgovore tačno, bodove dobija samo brži igrač.</li>
 * </ul>
 * Logika je deterministička: oba klijenta nad istim podacima iz Realtime
 * Database izračunavaju identičan rezultat, pa server nije potreban.
 */
public final class KoZnaZnaLogic {

    private KoZnaZnaLogic() {
        // ne instancira se
    }

    /**
     * Izračunava bodove koje igrač {@code uid} dobija za jedno pitanje.
     *
     * @param uid     igrač za kog se računaju bodovi
     * @param answers odgovori oba igrača (ključ je UID)
     * @return +10, −5 ili 0
     */
    public static int pointsFor(@NonNull String uid, @NonNull Map<String, KzzAnswer> answers) {
        KzzAnswer mine = answers.get(uid);
        if (mine == null || mine.getAnswerIndex() < 0) {
            return 0; // nije odgovorio — bodovi nepromenjeni
        }
        if (!mine.isCorrect()) {
            return Constants.KZZ_POINTS_WRONG;
        }
        // Tačan odgovor — bodove dobija samo ako je brži od protivnika koji je takođe tačan
        for (Map.Entry<String, KzzAnswer> entry : answers.entrySet()) {
            if (entry.getKey().equals(uid)) {
                continue;
            }
            KzzAnswer other = entry.getValue();
            if (other.getAnswerIndex() >= 0 && other.isCorrect()) {
                if (other.getElapsedMs() < mine.getElapsedMs()) {
                    return 0;
                }
                // Identično vreme — determinističko razrešenje poređenjem UID-ova
                if (other.getElapsedMs() == mine.getElapsedMs()
                        && entry.getKey().compareTo(uid) < 0) {
                    return 0;
                }
            }
        }
        return Constants.KZZ_POINTS_CORRECT;
    }

    /** Da li su oba igrača dala odgovor na pitanje (ili im je isteklo vreme). */
    public static boolean bothAnswered(@NonNull Map<String, KzzAnswer> answers) {
        return answers.size() >= 2;
    }
}

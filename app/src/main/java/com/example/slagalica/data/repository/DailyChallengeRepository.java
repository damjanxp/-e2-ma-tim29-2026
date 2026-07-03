package com.example.slagalica.data.repository;

import androidx.annotation.NonNull;

import com.example.slagalica.data.model.DailyChallengeState;
import com.example.slagalica.util.Constants;
import com.example.slagalica.util.DateUtils;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.Map;

/**
 * Dnevni izazovi — Firestore kolekcija {@code dailyChallenges}, jedan dokument
 * po korisniku ({@code dailyChallenges/{uid}}).
 *
 * <p>Skup izazova koji postoje je jedino definisan u
 * {@link Constants#DAILY_CHALLENGE_IDS} — ova klasa ne zna ništa o pojedinačnim
 * izazovima osim njihovih ID-jeva, pa dodavanje novog izazova ne zahteva izmene
 * ovde.</p>
 *
 * <p>"Dan" se određuje kalendarskim ključem ({@link DateUtils#todayKey()}) u
 * vremenskoj zoni uređaja. Kad god se pri čitanju ili upisu otkrije da je
 * sačuvani {@code date} različit od današnjeg, stanje se tretira kao prazno i
 * dokument se prepisuje s današnjim danom — nema potrebe za cron poslom koji bi
 * resetovao izazove u ponoć.</p>
 *
 * <p>Dokument:</p>
 * <pre>
 * dailyChallenges/{uid}
 *   date          — "yyyy-MM-dd"
 *   completed     — { challengeId: true, ... }
 *   bonusClaimed  — da li je bonus za sve izazove već isplaćen ovog dana
 * </pre>
 */
public class DailyChallengeRepository {

    private static final String COLLECTION_DAILY_CHALLENGES = "dailyChallenges";

    /** Callback bez povratne vrednosti. */
    public interface SimpleCallback {
        void onSuccess();
        void onError(@NonNull String message);
    }

    /** Slušalac stanja dnevnih izazova (živi listener). */
    public interface StateListener {
        void onState(@NonNull DailyChallengeState state);
    }

    /** Rezultat pokušaja da se označi jedan izazov završenim. */
    public interface CompleteListener {
        /**
         * @param newlyCompleted true ako je OVIM pozivom izazov prvi put završen
         *                       danas (false ako je već bio završen — bez duple nagrade)
         * @param bonusAwarded   true ako je ovim pozivom dodeljen i bonus za sve izazove
         */
        void onResult(boolean newlyCompleted, boolean bonusAwarded);
        void onError(@NonNull String message);
    }

    private static DailyChallengeRepository instance;

    private final FirebaseFirestore mDb;
    private final UserRepository userRepository;

    private DailyChallengeRepository() {
        mDb = FirebaseFirestore.getInstance();
        userRepository = UserRepository.getInstance();
    }

    public static synchronized DailyChallengeRepository getInstance() {
        if (instance == null) {
            instance = new DailyChallengeRepository();
        }
        return instance;
    }

    // =========================================================================
    // Čitanje
    // =========================================================================

    /** Sluša stanje dnevnih izazova korisnika u realnom vremenu. */
    public Runnable listenState(@NonNull String uid, @NonNull StateListener listener) {
        ListenerRegistration registration = docRef(uid).addSnapshotListener((snap, error) -> {
            if (error != null || snap == null) {
                return; // tiho — prikaz ostaje kakav je bio
            }
            listener.onState(parseFresh(snap));
        });
        return registration::remove;
    }

    /** Parsira dokument u stanje "za danas" — zastareo/nepostojeći dan postaje prazno stanje. */
    @NonNull
    private DailyChallengeState parseFresh(@NonNull DocumentSnapshot snap) {
        String today = DateUtils.todayKey();
        DailyChallengeState state = new DailyChallengeState(today);
        if (!snap.exists() || !today.equals(snap.getString("date"))) {
            return state; // nov dan (ili nema dokumenta) — sve nezavršeno
        }
        Object completedRaw = snap.get("completed");
        if (completedRaw instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) completedRaw).entrySet()) {
                if (entry.getKey() instanceof String && entry.getValue() instanceof Boolean) {
                    state.completed.put((String) entry.getKey(), (Boolean) entry.getValue());
                }
            }
        }
        state.bonusClaimed = Boolean.TRUE.equals(snap.getBoolean("bonusClaimed"));
        return state;
    }

    // =========================================================================
    // Završavanje izazova
    // =========================================================================

    /**
     * Označava jedan izazov završenim za danas (idempotentno — ako je izazov
     * već bio završen danas, ne dodeljuje nagradu ponovo). Kada ovim pozivom
     * poslednji nezavršeni izazov iz {@link Constants#DAILY_CHALLENGE_IDS}
     * postane završen, dodeljuje i bonus.
     */
    public void completeChallenge(@NonNull String uid, @NonNull String challengeId,
                                  @NonNull CompleteListener listener) {
        DocumentReference ref = docRef(uid);
        String today = DateUtils.todayKey();

        mDb.runTransaction(transaction -> {
            DocumentSnapshot snap = transaction.get(ref);
            boolean freshDay = !snap.exists() || !today.equals(snap.getString("date"));
            Map<String, Object> completed = freshDay
                    ? new HashMap<>() : rawCompletedMap(snap);
            boolean alreadyBonusClaimed = !freshDay
                    && Boolean.TRUE.equals(snap.getBoolean("bonusClaimed"));

            if (Boolean.TRUE.equals(completed.get(challengeId))) {
                // Već završeno danas — samo osveži datum ako je bio zastareo.
                transaction.set(ref, docMap(today, completed, alreadyBonusClaimed));
                return new int[]{0, 0};
            }

            completed.put(challengeId, true);
            boolean bonusNow = !alreadyBonusClaimed && allDone(completed);
            transaction.set(ref, docMap(today, completed, alreadyBonusClaimed || bonusNow));
            return new int[]{1, bonusNow ? 1 : 0};
        }).addOnSuccessListener(flags -> {
            boolean newlyCompleted = flags[0] == 1;
            boolean bonusAwarded = flags[1] == 1;
            if (newlyCompleted) {
                creditRewards(uid, 1, bonusAwarded);
            }
            listener.onResult(newlyCompleted, bonusAwarded);
        }).addOnFailureListener(e ->
                listener.onError("Ažuriranje dnevnog izazova nije uspelo."));
    }

    /**
     * DEBUG: završava sve definisane izazove odjednom i dodeljuje nagrade za
     * svaki novozavršen izazov (i bonus, ako još nije dodeljen danas).
     */
    public void completeAllDebug(@NonNull String uid, @NonNull SimpleCallback cb) {
        DocumentReference ref = docRef(uid);
        String today = DateUtils.todayKey();

        mDb.runTransaction(transaction -> {
            DocumentSnapshot snap = transaction.get(ref);
            boolean freshDay = !snap.exists() || !today.equals(snap.getString("date"));
            Map<String, Object> completed = freshDay
                    ? new HashMap<>() : rawCompletedMap(snap);
            boolean alreadyBonusClaimed = !freshDay
                    && Boolean.TRUE.equals(snap.getBoolean("bonusClaimed"));

            int newlyCompletedCount = 0;
            for (String id : Constants.DAILY_CHALLENGE_IDS) {
                if (!Boolean.TRUE.equals(completed.get(id))) {
                    completed.put(id, true);
                    newlyCompletedCount++;
                }
            }
            boolean bonusNow = !alreadyBonusClaimed && allDone(completed);
            transaction.set(ref, docMap(today, completed, alreadyBonusClaimed || bonusNow));
            return new int[]{newlyCompletedCount, bonusNow ? 1 : 0};
        }).addOnSuccessListener(flags -> {
            int newlyCompletedCount = flags[0];
            boolean bonusAwarded = flags[1] == 1;
            if (newlyCompletedCount > 0 || bonusAwarded) {
                creditRewards(uid, newlyCompletedCount, bonusAwarded);
            }
            cb.onSuccess();
        }).addOnFailureListener(e ->
                cb.onError("Završavanje svih izazova nije uspelo."));
    }

    /**
     * DEBUG: resetuje sve izazove za danas na "nezavršeno". Ne oduzima zvezde/
     * žetone već dodeljene pre reseta — resetuje se samo napredak, ne i nagrade.
     */
    public void resetAllDebug(@NonNull String uid, @NonNull SimpleCallback cb) {
        docRef(uid).set(docMap(DateUtils.todayKey(), new HashMap<>(), false))
                .addOnSuccessListener(v -> cb.onSuccess())
                .addOnFailureListener(e -> cb.onError("Resetovanje dnevnih izazova nije uspelo."));
    }

    /** Dodeljuje zvezde za {@code count} novozavršenih izazova, plus bonus ako je dodeljen. */
    private void creditRewards(@NonNull String uid, int count, boolean bonusAwarded) {
        int stars = count * Constants.DAILY_CHALLENGE_REWARD_STARS
                + (bonusAwarded ? Constants.DAILY_CHALLENGE_ALL_BONUS_STARS : 0);
        int tokens = bonusAwarded ? Constants.DAILY_CHALLENGE_ALL_BONUS_TOKENS : 0;
        if (stars > 0 || tokens > 0) {
            userRepository.creditChallengeReward(uid, stars, tokens, null);
            // Dnevni izazovi su čist dobitak (nema uloga), pa ceo iznos ide
            // i na rang listu (nedeljni/mesečni brojač zvezda).
            userRepository.addLeaderboardStars(uid, stars, null);
        }
    }

    // =========================================================================
    // Pomoćne metode
    // =========================================================================

    /** Kopira {@code completed} mapu iz snapshot-a (pretpostavlja da je dan svež). */
    @NonNull
    private Map<String, Object> rawCompletedMap(@NonNull DocumentSnapshot snap) {
        Map<String, Object> result = new HashMap<>();
        Object raw = snap.get("completed");
        if (raw instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
                if (entry.getKey() instanceof String) {
                    result.put((String) entry.getKey(), entry.getValue());
                }
            }
        }
        return result;
    }

    private boolean allDone(@NonNull Map<String, Object> completed) {
        for (String id : Constants.DAILY_CHALLENGE_IDS) {
            if (!Boolean.TRUE.equals(completed.get(id))) {
                return false;
            }
        }
        return true;
    }

    @NonNull
    private Map<String, Object> docMap(@NonNull String date, @NonNull Map<String, Object> completed,
                                       boolean bonusClaimed) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("date", date);
        doc.put("completed", completed);
        doc.put("bonusClaimed", bonusClaimed);
        return doc;
    }

    private DocumentReference docRef(@NonNull String uid) {
        return mDb.collection(COLLECTION_DAILY_CHALLENGES).document(uid);
    }
}

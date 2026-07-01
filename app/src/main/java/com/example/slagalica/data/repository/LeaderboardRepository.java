package com.example.slagalica.data.repository;

import androidx.annotation.NonNull;

import com.example.slagalica.data.model.LeaderboardCycle;
import com.example.slagalica.data.model.User;
import com.example.slagalica.util.Constants;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Singleton repozitorijum za rang listu (nedeljni i mesečni ciklus).
 *
 * <p>Rangiranje se računa nad poljima {@code weeklyStars}/{@code monthlyStars} korisničkog
 * profila. Ciklusi se ne zaključuju automatski (nema Cloud Functions/planera na besplatnom
 * planu) — zaključuje ih ručno test-dugme na ekranu rang liste, koje dodeljuje žetone prema
 * trenutnom plasmanu i resetuje brojače zvezda za novi ciklus.</p>
 */
public class LeaderboardRepository {

    private static LeaderboardRepository instance;

    private final FirebaseFirestore mDb;

    private LeaderboardRepository() {
        mDb = FirebaseFirestore.getInstance();
    }

    /**
     * Vraća jedinu instancu {@link LeaderboardRepository}.
     *
     * @return singleton instanca
     */
    @NonNull
    public static synchronized LeaderboardRepository getInstance() {
        if (instance == null) {
            instance = new LeaderboardRepository();
        }
        return instance;
    }

    // -------------------------------------------------------------------------
    // Callback interfejsi
    // -------------------------------------------------------------------------

    public interface CycleCallback {
        void onSuccess(@NonNull LeaderboardCycle cycle);
        void onError(@NonNull String message);
    }

    public interface RankingCallback {
        void onSuccess(@NonNull List<User> rankedUsers);
        void onError(@NonNull String message);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(@NonNull String message);
    }

    // -------------------------------------------------------------------------
    // Pomoćne metode
    // -------------------------------------------------------------------------

    private String starsField(@NonNull String type) {
        return Constants.LEADERBOARD_TYPE_MONTHLY.equals(type) ? "monthlyStars" : "weeklyStars";
    }

    private int[] rewardsFor(@NonNull String type) {
        return Constants.LEADERBOARD_TYPE_MONTHLY.equals(type)
                ? Constants.LEADERBOARD_MONTHLY_REWARDS
                : Constants.LEADERBOARD_WEEKLY_REWARDS;
    }

    /** Računa kraj ciklusa: +7 dana za nedeljni, +1 kalendarski mesec za mesečni. */
    private long computeCycleEnd(@NonNull String type, long start) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(start);
        if (Constants.LEADERBOARD_TYPE_MONTHLY.equals(type)) {
            cal.add(Calendar.MONTH, 1);
        } else {
            cal.add(Calendar.DAY_OF_YEAR, 7);
        }
        return cal.getTimeInMillis();
    }

    // -------------------------------------------------------------------------
    // Ciklus (metapodaci: kada je počeo/kada se završava)
    // -------------------------------------------------------------------------

    /** Učitava tekući ciklus; ako ne postoji (prvo pokretanje), kreira ga počevši od sada. */
    public void getOrInitCycle(@NonNull String type, @NonNull CycleCallback cb) {
        DocumentReference ref = mDb.collection(Constants.COLLECTION_LEADERBOARD_CYCLES).document(type);
        ref.get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        LeaderboardCycle cycle = snapshot.toObject(LeaderboardCycle.class);
                        if (cycle != null) {
                            cb.onSuccess(cycle);
                            return;
                        }
                    }
                    long now = System.currentTimeMillis();
                    LeaderboardCycle cycle = new LeaderboardCycle(now, computeCycleEnd(type, now));
                    ref.set(cycle)
                            .addOnSuccessListener(unused -> cb.onSuccess(cycle))
                            .addOnFailureListener(e -> cb.onError("Neuspešno kreiranje ciklusa rang liste."));
                })
                .addOnFailureListener(e -> cb.onError("Učitavanje ciklusa nije uspelo. Proveri internet vezu."));
    }

    // -------------------------------------------------------------------------
    // Rangiranje
    // -------------------------------------------------------------------------

    /** Vraća do {@link Constants#LEADERBOARD_TOP_N} igrača sortiranih po zvezdama tekućeg ciklusa. */
    public void getTopPlayers(@NonNull String type, @NonNull RankingCallback cb) {
        mDb.collection(Constants.COLLECTION_USERS)
                .orderBy(starsField(type), Query.Direction.DESCENDING)
                .limit(Constants.LEADERBOARD_TOP_N)
                .get()
                .addOnSuccessListener(snapshot -> cb.onSuccess(toUserList(snapshot)))
                .addOnFailureListener(e -> cb.onError("Učitavanje rang liste nije uspelo. Proveri internet vezu."));
    }

    private List<User> toUserList(@NonNull QuerySnapshot snapshot) {
        List<User> users = new ArrayList<>();
        for (QueryDocumentSnapshot doc : snapshot) {
            User user = doc.toObject(User.class);
            users.add(user);
        }
        return users;
    }

    // -------------------------------------------------------------------------
    // Debug: +1 zvezda
    // -------------------------------------------------------------------------

    /** Uvećava brojače zvezda za tekući nedeljni i mesečni ciklus za 1 (dugme za testiranje). */
    public void addDebugStar(@NonNull String uid, @NonNull SimpleCallback cb) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("weeklyStars", FieldValue.increment(1));
        updates.put("monthlyStars", FieldValue.increment(1));
        mDb.collection(Constants.COLLECTION_USERS).document(uid)
                .update(updates)
                .addOnSuccessListener(unused -> cb.onSuccess())
                .addOnFailureListener(e -> cb.onError("Dodavanje zvezde nije uspelo."));
    }

    // -------------------------------------------------------------------------
    // Debug: zaključivanje ciklusa
    // -------------------------------------------------------------------------

    /**
     * Zaključuje tekući ciklus (nedeljni ili mesečni): dodeljuje žetone prema
     * trenutnom plasmanu (mesta 1-10), resetuje brojač zvezda svih igrača na 0
     * i pokreće novi ciklus od sada.
     */
    public void concludeCycle(@NonNull String type, @NonNull SimpleCallback cb) {
        String field = starsField(type);
        int[] rewards = rewardsFor(type);

        mDb.collection(Constants.COLLECTION_USERS)
                .orderBy(field, Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    WriteBatch batch = mDb.batch();
                    int rank = 0;
                    for (QueryDocumentSnapshot doc : snapshot) {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put(field, 0);
                        if (rank < rewards.length) {
                            updates.put("tokens", FieldValue.increment(rewards[rank]));
                        }
                        batch.update(doc.getReference(), updates);
                        rank++;
                    }

                    long now = System.currentTimeMillis();
                    LeaderboardCycle newCycle = new LeaderboardCycle(now, computeCycleEnd(type, now));
                    DocumentReference cycleRef =
                            mDb.collection(Constants.COLLECTION_LEADERBOARD_CYCLES).document(type);
                    batch.set(cycleRef, newCycle);

                    batch.commit()
                            .addOnSuccessListener(unused -> cb.onSuccess())
                            .addOnFailureListener(e -> cb.onError("Zaključivanje ciklusa nije uspelo."));
                })
                .addOnFailureListener(e -> cb.onError("Učitavanje igrača za zaključivanje ciklusa nije uspelo."));
    }
}

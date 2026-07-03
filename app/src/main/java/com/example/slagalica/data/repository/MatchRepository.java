package com.example.slagalica.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.data.model.KzzAnswer;
import com.example.slagalica.data.model.KzzQuestion;
import com.example.slagalica.data.model.SpojniceAttempt;
import com.example.slagalica.data.model.SpojnicePuzzle;
import com.example.slagalica.util.Constants;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.GenericTypeIndicator;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Matchmaking i sinhronizacija aktivnog meča kroz Firebase Realtime Database.
 *
 * <p>Struktura podataka:</p>
 * <pre>
 * matchmaking/{uid}            — red za čekanje: {username, joinedAt, matchId?, opponentUid?, opponentName?}
 * matches/{matchId}
 *   player1 / player2          — {uid, username}; player1 je kreator meča
 *   presence/{uid}             — true dok je igrač u meču (onDisconnect → false)
 *   kzz/questions              — 5 pitanja (upisuje kreator)
 *   kzz/answers/{q}/{uid}      — odgovor igrača na pitanje q
 *   spojnice/rounds            — 2 slagalice (upisuje kreator)
 *   spojnice/state/{r}/phase   — 1: prvi igrač igra, 2: drugi igrač povezuje ostatak, 3: kraj runde
 *   spojnice/state/{r}/attempts/{leftIdx} — pokušaji povezivanja
 *   results/{game}/{uid}       — konačan broj bodova igrača u igri
 * </pre>
 *
 * <p>Sve metode koje kače slušaoce vraćaju {@link Runnable} koji ih otkačinje —
 * aktivnost ih obavezno poziva u {@code onDestroy()}.</p>
 */
public class MatchRepository {

    /** Callback za uparivanje igrača. */
    public interface MatchmakingListener {
        void onMatched(@NonNull String matchId, boolean isPlayerOne,
                       @NonNull String opponentUid, @NonNull String opponentName);
        void onError(@NonNull String message);
    }

    /** Callback bez povratne vrednosti. */
    public interface SimpleCallback {
        void onSuccess();
        void onError(@NonNull String message);
    }

    /** Slušalac pitanja za "Ko zna zna" (okida se kad kreator upiše sadržaj). */
    public interface KzzQuestionsListener {
        void onQuestions(@NonNull List<KzzQuestion> questions);
        void onError(@NonNull String message);
    }

    /** Slušalac slagalica za "Spojnice". */
    public interface SpojniceRoundsListener {
        void onRounds(@NonNull List<SpojnicePuzzle> rounds);
        void onError(@NonNull String message);
    }

    /** Slušalac odgovora oba igrača na jedno pitanje. */
    public interface KzzAnswersListener {
        void onAnswers(@NonNull Map<String, KzzAnswer> answersByUid);
    }

    /** Slušalac stanja jedne runde spojnica. */
    public interface SpojniceStateListener {
        void onState(int phase, @NonNull Map<Integer, SpojniceAttempt> attemptsByLeftIdx);
    }

    /** Slušalac prisustva protivnika u meču. */
    public interface PresenceListener {
        void onPresenceChanged(boolean online);
    }

    /** Slušalac rezultata igara u meču. */
    public interface ResultsListener {
        void onResults(@NonNull Map<String, Long> scoreByUid);
    }

    public static final String GAME_KZZ      = "kzz";
    public static final String GAME_SPOJNICE = "spojnice";

    private static MatchRepository instance;

    private final FirebaseDatabase database;

    @Nullable private DatabaseReference myQueueRef;
    @Nullable private ValueEventListener queueListener;

    private MatchRepository() {
        // Eksplicitna adresa baze (europe-west1) — bez ovoga se SDK povezuje na
        // pogrešan podrazumevani region jer google-services.json nema firebase_url.
        database = FirebaseDatabase.getInstance(Constants.RTDB_URL);
    }

    public static synchronized MatchRepository getInstance() {
        if (instance == null) {
            instance = new MatchRepository();
        }
        return instance;
    }

    // =========================================================================
    // Matchmaking
    // =========================================================================

    /**
     * Ulazi u red za uparivanje. Ako u redu već čeka drugi igrač, preuzima ga
     * transakcijom (zaštita od duplog uparivanja) i kreira meč; u suprotnom
     * upisuje sopstveni ulaz i čeka da ga neko upari.
     */
    public void joinQueue(@NonNull String uid, @NonNull String username,
                          @NonNull MatchmakingListener listener) {
        DatabaseReference queueRef = database.getReference(Constants.RTDB_MATCHMAKING);
        queueRef.get().addOnSuccessListener(snapshot -> {
            String candidateUid = null;
            String candidateName = null;
            for (DataSnapshot child : snapshot.getChildren()) {
                boolean alreadyMatched = child.hasChild("matchId");
                if (child.getKey() != null && !child.getKey().equals(uid) && !alreadyMatched) {
                    candidateUid = child.getKey();
                    candidateName = child.child("username").getValue(String.class);
                    break;
                }
            }
            if (candidateUid != null) {
                claimCandidate(uid, username, candidateUid,
                        candidateName != null ? candidateName : "Protivnik", listener);
            } else {
                enqueueAndWait(uid, username, listener);
            }
        }).addOnFailureListener(e ->
                listener.onError("Traženje protivnika nije uspelo. Proveri internet konekciju."));
    }

    /** Napušta red za čekanje (otkazivanje pretrage). */
    public void cancelQueue(@NonNull String uid) {
        if (myQueueRef != null && queueListener != null) {
            myQueueRef.removeEventListener(queueListener);
        }
        database.getReference(Constants.RTDB_MATCHMAKING).child(uid).removeValue();
        myQueueRef = null;
        queueListener = null;
    }

    private void claimCandidate(String myUid, String myUsername,
                                String candidateUid, String candidateName,
                                MatchmakingListener listener) {
        String matchId = database.getReference(Constants.RTDB_MATCHES).push().getKey();
        if (matchId == null) {
            listener.onError("Kreiranje meča nije uspelo.");
            return;
        }
        DatabaseReference candidateRef =
                database.getReference(Constants.RTDB_MATCHMAKING).child(candidateUid);

        candidateRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                if (currentData.getValue() == null) {
                    return Transaction.abort(); // kandidat je u međuvremenu izašao
                }
                if (currentData.child("matchId").getValue() != null) {
                    return Transaction.abort(); // kandidata je preuzeo neko drugi
                }
                currentData.child("matchId").setValue(matchId);
                currentData.child("opponentUid").setValue(myUid);
                currentData.child("opponentName").setValue(myUsername);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed,
                                   @Nullable DataSnapshot currentData) {
                if (committed) {
                    createMatchNode(matchId, myUid, myUsername,
                            candidateUid, candidateName, listener);
                } else {
                    // Kandidat više nije slobodan — pokušaj ponovo iz početka
                    joinQueue(myUid, myUsername, listener);
                }
            }
        });
    }

    private void createMatchNode(String matchId, String myUid, String myUsername,
                                 String opponentUid, String opponentName,
                                 MatchmakingListener listener) {
        Map<String, Object> player1 = new HashMap<>();
        player1.put("uid", myUid);
        player1.put("username", myUsername);

        Map<String, Object> player2 = new HashMap<>();
        player2.put("uid", opponentUid);
        player2.put("username", opponentName);

        Map<String, Object> match = new HashMap<>();
        match.put("player1", player1);
        match.put("player2", player2);
        match.put("status", "active");
        match.put("createdAt", ServerValue.TIMESTAMP);

        matchRef(matchId).setValue(match)
                .addOnSuccessListener(v ->
                        listener.onMatched(matchId, true, opponentUid, opponentName))
                .addOnFailureListener(e ->
                        listener.onError("Kreiranje meča nije uspelo."));
    }

    /**
     * Kreira zapis meča direktno, bez matchmaking reda — koristi ga poziv
     * prijatelja na partiju ("7. Prijatelji"), kada je protivnik već poznat
     * (prihvatio je poziv) pa nema potrebe za nasumičnim uparivanjem.
     *
     * @param matchId     jedinstven id meča (npr. ključ pod {@code friendInvites})
     * @param player1Uid  uid igrača koji priprema sadržaj igara (kreator poziva)
     * @param player1Name korisničko ime kreatora poziva
     * @param player2Uid  uid pozvanog igrača
     * @param player2Name korisničko ime pozvanog igrača
     * @param cb          callback sa rezultatom operacije
     */
    public void createDirectMatch(@NonNull String matchId,
                                  @NonNull String player1Uid, @NonNull String player1Name,
                                  @NonNull String player2Uid, @NonNull String player2Name,
                                  @NonNull SimpleCallback cb) {
        Map<String, Object> player1 = new HashMap<>();
        player1.put("uid", player1Uid);
        player1.put("username", player1Name);

        Map<String, Object> player2 = new HashMap<>();
        player2.put("uid", player2Uid);
        player2.put("username", player2Name);

        Map<String, Object> match = new HashMap<>();
        match.put("player1", player1);
        match.put("player2", player2);
        match.put("status", "active");
        match.put("createdAt", ServerValue.TIMESTAMP);

        matchRef(matchId).setValue(match)
                .addOnSuccessListener(v -> cb.onSuccess())
                .addOnFailureListener(e -> cb.onError("Kreiranje meča nije uspelo."));
    }

    private void enqueueAndWait(String uid, String username, MatchmakingListener listener) {
        DatabaseReference entryRef =
                database.getReference(Constants.RTDB_MATCHMAKING).child(uid);
        Map<String, Object> entry = new HashMap<>();
        entry.put("username", username);
        entry.put("joinedAt", ServerValue.TIMESTAMP);
        entryRef.setValue(entry);
        entryRef.onDisconnect().removeValue();

        myQueueRef = entryRef;
        queueListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String matchId = snapshot.child("matchId").getValue(String.class);
                if (matchId == null) {
                    return; // još uvek čekamo
                }
                String opponentUid = snapshot.child("opponentUid").getValue(String.class);
                String opponentName = snapshot.child("opponentName").getValue(String.class);
                entryRef.removeEventListener(this);
                entryRef.removeValue();
                myQueueRef = null;
                queueListener = null;
                listener.onMatched(matchId, false,
                        opponentUid != null ? opponentUid : "",
                        opponentName != null ? opponentName : "Protivnik");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                listener.onError("Veza sa serverom je prekinuta.");
            }
        };
        entryRef.addValueEventListener(queueListener);
    }

    // =========================================================================
    // Sadržaj meča (upisuje kreator — player1)
    // =========================================================================

    /** Upisuje pitanja i slagalice u meč, tako da oba igrača vide isti sadržaj. */
    public void writeMatchContent(@NonNull String matchId,
                                  @NonNull List<KzzQuestion> questions,
                                  @NonNull List<SpojnicePuzzle> puzzles,
                                  @NonNull SimpleCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("kzz/questions", questions);
        updates.put("spojnice/rounds", puzzles);
        matchRef(matchId).updateChildren(updates)
                .addOnSuccessListener(v -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError("Upis sadržaja meča nije uspeo."));
    }

    /** Čeka da pitanja budu dostupna u meču, pa ih vraća (jednokratno). */
    public Runnable listenKzzQuestions(@NonNull String matchId,
                                       @NonNull KzzQuestionsListener listener) {
        DatabaseReference ref = matchRef(matchId).child("kzz").child("questions");
        ValueEventListener vel = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    return; // kreator još nije upisao sadržaj
                }
                GenericTypeIndicator<List<KzzQuestion>> type =
                        new GenericTypeIndicator<List<KzzQuestion>>() {};
                List<KzzQuestion> questions = snapshot.getValue(type);
                if (questions != null && !questions.isEmpty()) {
                    ref.removeEventListener(this);
                    listener.onQuestions(questions);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                listener.onError("Učitavanje pitanja iz meča nije uspelo.");
            }
        };
        ref.addValueEventListener(vel);
        return () -> ref.removeEventListener(vel);
    }

    /** Čeka da slagalice budu dostupne u meču, pa ih vraća (jednokratno). */
    public Runnable listenSpojniceRounds(@NonNull String matchId,
                                         @NonNull SpojniceRoundsListener listener) {
        DatabaseReference ref = matchRef(matchId).child("spojnice").child("rounds");
        ValueEventListener vel = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    return;
                }
                GenericTypeIndicator<List<SpojnicePuzzle>> type =
                        new GenericTypeIndicator<List<SpojnicePuzzle>>() {};
                List<SpojnicePuzzle> rounds = snapshot.getValue(type);
                if (rounds != null && !rounds.isEmpty()) {
                    ref.removeEventListener(this);
                    listener.onRounds(rounds);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                listener.onError("Učitavanje spojnica iz meča nije uspelo.");
            }
        };
        ref.addValueEventListener(vel);
        return () -> ref.removeEventListener(vel);
    }

    // =========================================================================
    // Ko zna zna — sinhronizacija odgovora
    // =========================================================================

    /** Upisuje odgovor igrača na pitanje {@code questionIdx}. */
    public void submitKzzAnswer(@NonNull String matchId, int questionIdx,
                                @NonNull String uid, @NonNull KzzAnswer answer) {
        matchRef(matchId).child("kzz").child("answers")
                .child(String.valueOf(questionIdx)).child(uid)
                .setValue(answer);
    }

    /** Sluša odgovore oba igrača na pitanje {@code questionIdx}. */
    public Runnable listenKzzAnswers(@NonNull String matchId, int questionIdx,
                                     @NonNull KzzAnswersListener listener) {
        DatabaseReference ref = matchRef(matchId).child("kzz").child("answers")
                .child(String.valueOf(questionIdx));
        ValueEventListener vel = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Map<String, KzzAnswer> answers = new HashMap<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    KzzAnswer answer = child.getValue(KzzAnswer.class);
                    if (child.getKey() != null && answer != null) {
                        answers.put(child.getKey(), answer);
                    }
                }
                listener.onAnswers(answers);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // prekid veze se obrađuje kroz presence mehanizam
            }
        };
        ref.addValueEventListener(vel);
        return () -> ref.removeEventListener(vel);
    }

    // =========================================================================
    // Spojnice — sinhronizacija pokušaja i faza
    // =========================================================================

    /** Upisuje pokušaj povezivanja levog pojma {@code leftIdx} u rundi {@code round}. */
    public void submitSpojniceAttempt(@NonNull String matchId, int round, int leftIdx,
                                      @NonNull SpojniceAttempt attempt) {
        spojniceStateRef(matchId, round).child("attempts")
                .child(String.valueOf(leftIdx)).setValue(attempt);
    }

    /** Postavlja fazu runde (1 — prvi igrač, 2 — drugi igrač, 3 — kraj runde). */
    public void setSpojnicePhase(@NonNull String matchId, int round, int phase) {
        spojniceStateRef(matchId, round).child("phase").setValue(phase);
    }

    /** Sluša fazu i pokušaje jedne runde spojnica. */
    public Runnable listenSpojniceState(@NonNull String matchId, int round,
                                        @NonNull SpojniceStateListener listener) {
        DatabaseReference ref = spojniceStateRef(matchId, round);
        ValueEventListener vel = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Integer phase = snapshot.child("phase").getValue(Integer.class);
                Map<Integer, SpojniceAttempt> attempts = new HashMap<>();
                for (DataSnapshot child : snapshot.child("attempts").getChildren()) {
                    SpojniceAttempt attempt = child.getValue(SpojniceAttempt.class);
                    if (child.getKey() != null && attempt != null) {
                        attempts.put(Integer.parseInt(child.getKey()), attempt);
                    }
                }
                listener.onState(phase != null ? phase : 1, attempts);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // prekid veze se obrađuje kroz presence mehanizam
            }
        };
        ref.addValueEventListener(vel);
        return () -> ref.removeEventListener(vel);
    }

    // =========================================================================
    // Prisustvo i rezultati
    // =========================================================================

    /** Označava igrača kao prisutnog u meču; pri prekidu veze automatski false. */
    public void setPresence(@NonNull String matchId, @NonNull String uid) {
        DatabaseReference ref = matchRef(matchId).child("presence").child(uid);
        ref.setValue(true);
        ref.onDisconnect().setValue(false);
    }

    /** Označava da je igrač napustio meč. */
    public void leaveMatch(@NonNull String matchId, @NonNull String uid) {
        matchRef(matchId).child("presence").child(uid).setValue(false);
    }

    /** Sluša prisustvo protivnika — ako napusti meč, čekanja se preskaču. */
    public Runnable listenOpponentPresence(@NonNull String matchId, @NonNull String opponentUid,
                                           @NonNull PresenceListener listener) {
        DatabaseReference ref = matchRef(matchId).child("presence").child(opponentUid);
        ValueEventListener vel = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean online = snapshot.getValue(Boolean.class);
                // dok se protivnik ne javi prvi put, smatramo da dolazi
                listener.onPresenceChanged(online == null || online);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // ignorisano
            }
        };
        ref.addValueEventListener(vel);
        return () -> ref.removeEventListener(vel);
    }

    /** Upisuje konačan rezultat igrača za jednu igru ({@link #GAME_KZZ} ili {@link #GAME_SPOJNICE}). */
    public void setGameResult(@NonNull String matchId, @NonNull String game,
                              @NonNull String uid, int score) {
        matchRef(matchId).child("results").child(game).child(uid).setValue(score);
    }

    /** Sluša rezultate jedne igre za oba igrača. */
    public Runnable listenGameResults(@NonNull String matchId, @NonNull String game,
                                      @NonNull ResultsListener listener) {
        DatabaseReference ref = matchRef(matchId).child("results").child(game);
        ValueEventListener vel = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Map<String, Long> scores = new HashMap<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Long score = child.getValue(Long.class);
                    if (child.getKey() != null && score != null) {
                        scores.put(child.getKey(), score);
                    }
                }
                listener.onResults(scores);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // ignorisano
            }
        };
        ref.addValueEventListener(vel);
        return () -> ref.removeEventListener(vel);
    }

    // =========================================================================
    // Moj broj — model klase
    // =========================================================================

    /** POJO koji se čuva u RTDB za izraz jednog igrača u igri Moj broj. */
    public static class MojBrojExpression {
        public String value;
        public int result;
        public boolean isValid;
        public boolean submitted;

        public MojBrojExpression() {}

        public MojBrojExpression(String value, int result, boolean isValid, boolean submitted) {
            this.value = value;
            this.result = result;
            this.isValid = isValid;
            this.submitted = submitted;
        }
    }

    // =========================================================================
    // Moj broj — callback interfejsi
    // =========================================================================

    /** Okida se kad starter upiše traženi broj i 6 dostupnih brojeva. */
    public interface MojBrojRoundListener {
        void onRound(int target, int[] numbers);
        void onError(@NonNull String message);
    }

    /** Okida se na svaku promenu izraza bilo kog igrača u rundi. */
    public interface MojBrojExpressionsListener {
        void onExpressions(@NonNull Map<String, MojBrojExpression> byUid);
    }

    // =========================================================================
    // Moj broj — metode
    // =========================================================================

    /**
     * Starter upisuje traženi broj i 6 dostupnih brojeva za rundu.
     * Ovo je signal non-starteru da počne igru.
     */
    public void writeMojBrojRound(@NonNull String matchId, int round,
                                  int target, @NonNull int[] numbers,
                                  @Nullable SimpleCallback cb) {
        Map<String, Object> data = new HashMap<>();
        data.put("target", target);
        Map<String, Integer> numbersMap = new HashMap<>();
        for (int i = 0; i < numbers.length; i++) {
            numbersMap.put(String.valueOf(i), numbers[i]);
        }
        data.put("numbers", numbersMap);
        mojBrojRoundRef(matchId, round).updateChildren(data)
                .addOnSuccessListener(v -> { if (cb != null) cb.onSuccess(); })
                .addOnFailureListener(e -> { if (cb != null) cb.onError("Upis runde nije uspeo."); });
    }

    /**
     * Čeka da starter upiše sadržaj runde; jednokratni listener koji
     * se uklanja čim podaci stignu.
     */
    public Runnable listenMojBrojRound(@NonNull String matchId, int round,
                                       @NonNull MojBrojRoundListener listener) {
        DatabaseReference ref = mojBrojRoundRef(matchId, round);
        ValueEventListener vel = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Long target = snapshot.child("target").getValue(Long.class);
                DataSnapshot numbersSnap = snapshot.child("numbers");
                if (target == null || !numbersSnap.exists()) {
                    return; // još nije upisano
                }
                int[] nums = new int[6];
                for (int i = 0; i < 6; i++) {
                    Long v = numbersSnap.child(String.valueOf(i)).getValue(Long.class);
                    nums[i] = v != null ? v.intValue() : 0;
                }
                ref.removeEventListener(this);
                listener.onRound(target.intValue(), nums);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                listener.onError("Greška pri učitavanju runde.");
            }
        };
        ref.addValueEventListener(vel);
        return () -> ref.removeEventListener(vel);
    }

    /** Igrač upisuje rezultat svog izraza (poziva se na POTVRDI ili isteku tajmera). */
    public void submitMojBrojExpression(@NonNull String matchId, int round,
                                        @NonNull String uid, @NonNull String expression,
                                        int result, boolean isValid) {
        mojBrojRoundRef(matchId, round).child("expressions").child(uid)
                .setValue(new MojBrojExpression(expression, result, isValid, true));
    }

    /**
     * Sluša izraze oba igrača za rundu. Okida se na svaku promenu
     * dok ga pozivalac ne otkači.
     */
    public Runnable listenMojBrojExpressions(@NonNull String matchId, int round,
                                              @NonNull MojBrojExpressionsListener listener) {
        DatabaseReference ref = mojBrojRoundRef(matchId, round).child("expressions");
        ValueEventListener vel = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Map<String, MojBrojExpression> result = new HashMap<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    MojBrojExpression expr = child.getValue(MojBrojExpression.class);
                    if (child.getKey() != null && expr != null) {
                        result.put(child.getKey(), expr);
                    }
                }
                listener.onExpressions(result);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // ignorisano
            }
        };
        ref.addValueEventListener(vel);
        return () -> ref.removeEventListener(vel);
    }

    // =========================================================================
    // Korak po korak — model klase
    // =========================================================================

    /** Podaci o pogotku jednog igrača (za bodovanje). */
    public static class KorakGuessData {
        public String uid;
        public boolean correct;
        public int korakIndex;

        public KorakGuessData() {}

        public KorakGuessData(String uid, boolean correct, int korakIndex) {
            this.uid = uid;
            this.correct = correct;
            this.korakIndex = korakIndex;
        }
    }

    // =========================================================================
    // Korak po korak — callback interfejsi
    // =========================================================================

    public interface KorakZadatakListener {
        void onZadatak(@NonNull String resenje, @NonNull List<String> koraci);
        void onError(@NonNull String message);
    }

    public interface KorakStateListener {
        void onState(@NonNull String phase, int openedHints,
                     @Nullable KorakGuessData activeGuess,
                     @Nullable KorakGuessData opponentGuess);
    }

    // =========================================================================
    // Korak po korak — metode
    // =========================================================================

    /** Player1 upisuje zadatak za datu rundu (resenje + 7 hintova). */
    public void writeKorakZadatak(@NonNull String matchId, int round,
                                  @NonNull String resenje, @NonNull List<String> koraci,
                                  @Nullable SimpleCallback cb) {
        Map<String, Object> data = new HashMap<>();
        data.put("resenje", resenje);
        data.put("koraci", koraci);
        korakRoundRef(matchId, round).updateChildren(data)
                .addOnSuccessListener(v -> { if (cb != null) cb.onSuccess(); })
                .addOnFailureListener(e -> { if (cb != null) cb.onError("Upis zadatka nije uspeo."); });
    }

    /** Jednokratni listener koji čeka zadatak za rundu. */
    public Runnable listenKorakZadatak(@NonNull String matchId, int round,
                                       @NonNull KorakZadatakListener listener) {
        DatabaseReference ref = korakRoundRef(matchId, round);
        ValueEventListener vel = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String resenje = snapshot.child("resenje").getValue(String.class);
                DataSnapshot koraciSnap = snapshot.child("koraci");
                if (resenje == null || !koraciSnap.exists()) {
                    return; // još nije upisano
                }
                List<String> koraci = new ArrayList<>();
                for (DataSnapshot k : koraciSnap.getChildren()) {
                    String hint = k.getValue(String.class);
                    if (hint != null) koraci.add(hint);
                }
                if (koraci.size() < 7) return; // nepotpuno
                ref.removeEventListener(this);
                listener.onZadatak(resenje, koraci);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                listener.onError("Greška pri učitavanju zadatka.");
            }
        };
        ref.addValueEventListener(vel);
        return () -> ref.removeEventListener(vel);
    }

    /** Aktivni igrač ažurira broj otvorenih hintova. */
    public void setKorakOpenedHints(@NonNull String matchId, int round, int count) {
        korakRoundRef(matchId, round).child("openedHints").setValue(count);
    }

    /** Aktivni igrač postavlja fazu runde. */
    public void setKorakPhase(@NonNull String matchId, int round, @NonNull String phase) {
        korakRoundRef(matchId, round).child("phase").setValue(phase);
    }

    /** Aktivni igrač upisuje rezultat svog pogotka (tačan ili ne). */
    public void submitKorakActiveGuess(@NonNull String matchId, int round,
                                       @NonNull String uid, boolean correct, int korakIndex) {
        korakRoundRef(matchId, round).child("activeGuess")
                .setValue(new KorakGuessData(uid, correct, korakIndex));
    }

    /** Pasivni igrač upisuje rezultat svoje šanse. */
    public void submitKorakOpponentGuess(@NonNull String matchId, int round,
                                          @NonNull String uid, boolean correct) {
        korakRoundRef(matchId, round).child("opponentGuess")
                .setValue(new KorakGuessData(uid, correct, -1));
    }

    /** Oba igrača slušaju celo stanje runde u realnom vremenu. */
    public Runnable listenKorakState(@NonNull String matchId, int round,
                                     @NonNull KorakStateListener listener) {
        DatabaseReference ref = korakRoundRef(matchId, round);
        ValueEventListener vel = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String phase = snapshot.child("phase").getValue(String.class);
                Long openedLong = snapshot.child("openedHints").getValue(Long.class);
                int opened = openedLong != null ? openedLong.intValue() : 0;

                KorakGuessData activeGuess = null;
                KorakGuessData opponentGuess = null;
                if (snapshot.child("activeGuess").exists()) {
                    activeGuess = snapshot.child("activeGuess").getValue(KorakGuessData.class);
                }
                if (snapshot.child("opponentGuess").exists()) {
                    opponentGuess = snapshot.child("opponentGuess").getValue(KorakGuessData.class);
                }
                listener.onState(phase != null ? phase : "", opened, activeGuess, opponentGuess);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // ignorisano
            }
        };
        ref.addValueEventListener(vel);
        return () -> ref.removeEventListener(vel);
    }

    // =========================================================================
    // Asocijacije — callback interfejsi
    // =========================================================================

    public interface AsocijacijePuzzleListener {
        void onPuzzle(@NonNull com.example.slagalica.data.model.AsocijacijePuzzle puzzle);
        void onError(@NonNull String message);
    }

    public interface AsocijacijeScoreListener {
        void onScore(int score);
    }

    // =========================================================================
    // Asocijacije — metode
    // =========================================================================

    /** Player1 upisuje obe slagalice (za rundu 0 i 1) u meč. */
    public void writeAsocijacijePuzzles(@NonNull String matchId,
                                         @NonNull com.example.slagalica.data.model.AsocijacijePuzzle p0,
                                         @NonNull com.example.slagalica.data.model.AsocijacijePuzzle p1,
                                         @Nullable SimpleCallback cb) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("asocijacije/puzzles/0", puzzleToMap(p0));
        updates.put("asocijacije/puzzles/1", puzzleToMap(p1));
        matchRef(matchId).updateChildren(updates)
                .addOnSuccessListener(v -> { if (cb != null) cb.onSuccess(); })
                .addOnFailureListener(e -> { if (cb != null) cb.onError("Upis slagalica nije uspeo."); });
    }

    /** Jednokratni listener koji čeka slagalicu za zadatu rundu. */
    public Runnable listenAsocijacijePuzzle(@NonNull String matchId, int round,
                                              @NonNull AsocijacijePuzzleListener listener) {
        DatabaseReference ref = matchRef(matchId).child("asocijacije")
                .child("puzzles").child(String.valueOf(round));
        ValueEventListener vel = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                if (!snap.exists()) return;
                com.example.slagalica.data.model.AsocijacijePuzzle p = puzzleFromSnapshot(snap);
                if (p == null) return;
                ref.removeEventListener(this);
                listener.onPuzzle(p);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError e) {
                listener.onError("Greška pri učitavanju slagalice.");
            }
        };
        ref.addValueEventListener(vel);
        return () -> ref.removeEventListener(vel);
    }

    /** Aktivni igrač upisuje rezultat kada završi svoju rundu. */
    public void submitAsocijacijeScore(@NonNull String matchId, int round,
                                        @NonNull String uid, int score) {
        matchRef(matchId).child("asocijacije").child("score")
                .child(String.valueOf(round)).child(uid).setValue(score);
    }

    /**
     * Oba igrača slušaju rezultat runde. Okida se čim aktivni igrač upiše rezultat
     * (jednokratno — posle toga pasivni igrač zna da može da počne svoju rundu).
     */
    public Runnable listenAsocijacijeScore(@NonNull String matchId, int round,
                                            @NonNull AsocijacijeScoreListener listener) {
        DatabaseReference ref = matchRef(matchId).child("asocijacije")
                .child("score").child(String.valueOf(round));
        ValueEventListener vel = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                if (!snap.hasChildren()) return;
                for (DataSnapshot child : snap.getChildren()) {
                    Long score = child.getValue(Long.class);
                    if (score != null) {
                        ref.removeEventListener(this);
                        listener.onScore(score.intValue());
                        return;
                    }
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError e) {}
        };
        ref.addValueEventListener(vel);
        return () -> ref.removeEventListener(vel);
    }

    // =========================================================================
    // Asocijacije — live round state (turn-based multiplayer)
    // =========================================================================

    public interface AsocijacijeRoundListener {
        void onRoundState(@NonNull AsocijacijeRoundState state);
    }

    public static class AsocijacijeRoundState {
        public String turn;
        public boolean mustReveal;
        public String phase = "PLAYING";
        public boolean[][] revealed = new boolean[4][4];
        public String[] colSolvedBy = new String[4];
        public int[] colScores = new int[4];
        public boolean finalSolved;
        public String finalSolvedBy;
        public int finalScore;
        public Map<String, Integer> scores = new HashMap<>();
    }

    public void initAsocijacijeRound(@NonNull String matchId, int round, @NonNull String firstTurnUid) {
        Map<String, Object> init = new HashMap<>();
        init.put("turn", firstTurnUid);
        init.put("mustReveal", false);
        init.put("phase", "PLAYING");
        asocRoundRef(matchId, round).updateChildren(init);
    }

    public void revealAsocijacijeCell(@NonNull String matchId, int round, int col, int row, boolean clearMustReveal) {
        Map<String, Object> update = new HashMap<>();
        update.put("revealed/" + col + "/" + row, true);
        if (clearMustReveal) update.put("mustReveal", false);
        asocRoundRef(matchId, round).updateChildren(update);
    }

    public void solveAsocijacijeColumn(@NonNull String matchId, int round, int col,
                                        @NonNull String solverUid, int colScore, int newTotalScore) {
        Map<String, Object> update = new HashMap<>();
        update.put("colSolved/" + col, solverUid);
        update.put("colScores/" + col, colScore);
        update.put("scores/" + solverUid, newTotalScore);
        asocRoundRef(matchId, round).updateChildren(update);
    }

    public void solveAsocijacijeFinal(@NonNull String matchId, int round,
                                       @NonNull String solverUid, int finalScore, int newTotalScore) {
        Map<String, Object> update = new HashMap<>();
        update.put("finalSolved", true);
        update.put("finalSolvedBy", solverUid);
        update.put("finalScore", finalScore);
        update.put("scores/" + solverUid, newTotalScore);
        update.put("phase", "DONE");
        asocRoundRef(matchId, round).updateChildren(update);
    }

    public void passAsocijacijeTurn(@NonNull String matchId, int round, @NonNull String nextTurnUid) {
        Map<String, Object> update = new HashMap<>();
        update.put("turn", nextTurnUid);
        update.put("mustReveal", true);
        asocRoundRef(matchId, round).updateChildren(update);
    }

    public void endAsocijacijeRound(@NonNull String matchId, int round) {
        asocRoundRef(matchId, round).child("phase").setValue("DONE");
    }

    public Runnable listenAsocijacijeRoundState(@NonNull String matchId, int round,
                                                 @NonNull AsocijacijeRoundListener listener) {
        DatabaseReference ref = asocRoundRef(matchId, round);
        ValueEventListener vel = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                AsocijacijeRoundState state = new AsocijacijeRoundState();
                state.turn = snap.child("turn").getValue(String.class);
                Boolean mr = snap.child("mustReveal").getValue(Boolean.class);
                state.mustReveal = Boolean.TRUE.equals(mr);
                String ph = snap.child("phase").getValue(String.class);
                state.phase = ph != null ? ph : "PLAYING";

                for (DataSnapshot colSnap : snap.child("revealed").getChildren()) {
                    int c = safeParseInt(colSnap.getKey(), -1);
                    if (c < 0 || c >= 4) continue;
                    for (DataSnapshot rowSnap : colSnap.getChildren()) {
                        int r = safeParseInt(rowSnap.getKey(), -1);
                        if (r < 0 || r >= 4) continue;
                        if (Boolean.TRUE.equals(rowSnap.getValue(Boolean.class))) state.revealed[c][r] = true;
                    }
                }
                for (DataSnapshot cs : snap.child("colSolved").getChildren()) {
                    int c = safeParseInt(cs.getKey(), -1);
                    if (c < 0 || c >= 4) continue;
                    state.colSolvedBy[c] = cs.getValue(String.class);
                }
                for (DataSnapshot cs : snap.child("colScores").getChildren()) {
                    int c = safeParseInt(cs.getKey(), -1);
                    if (c < 0 || c >= 4) continue;
                    Long v = cs.getValue(Long.class);
                    if (v != null) state.colScores[c] = v.intValue();
                }
                Boolean fs = snap.child("finalSolved").getValue(Boolean.class);
                state.finalSolved = Boolean.TRUE.equals(fs);
                state.finalSolvedBy = snap.child("finalSolvedBy").getValue(String.class);
                Long fsc = snap.child("finalScore").getValue(Long.class);
                state.finalScore = fsc != null ? fsc.intValue() : 0;
                for (DataSnapshot sc : snap.child("scores").getChildren()) {
                    if (sc.getKey() == null) continue;
                    Long v = sc.getValue(Long.class);
                    if (v != null) state.scores.put(sc.getKey(), v.intValue());
                }
                listener.onRoundState(state);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError e) {}
        };
        ref.addValueEventListener(vel);
        return () -> ref.removeEventListener(vel);
    }

    private int safeParseInt(@Nullable String s, int fallback) {
        if (s == null) return fallback;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return fallback; }
    }

    private DatabaseReference asocRoundRef(@NonNull String matchId, int round) {
        return matchRef(matchId).child("asocijacije").child("rounds").child(String.valueOf(round));
    }

    private Map<String, Object> puzzleToMap(com.example.slagalica.data.model.AsocijacijePuzzle p) {
        List<String> clueFlat = new ArrayList<>(16);
        for (int c = 0; c < 4; c++)
            for (int r = 0; r < 4; r++)
                clueFlat.add(p.getClue(c, r));
        List<String> colSols = new ArrayList<>(4);
        for (int c = 0; c < 4; c++) colSols.add(p.getColSolution(c));
        Map<String, Object> m = new HashMap<>();
        m.put("clues", clueFlat);
        m.put("colSolutions", colSols);
        m.put("finalSolution", p.getFinalSolution());
        return m;
    }

    @Nullable
    private com.example.slagalica.data.model.AsocijacijePuzzle puzzleFromSnapshot(
            @NonNull DataSnapshot snap) {
        String finalSol = snap.child("finalSolution").getValue(String.class);
        if (finalSol == null) return null;
        String[] clueFlat = new String[16];
        int i = 0;
        for (DataSnapshot c : snap.child("clues").getChildren()) {
            if (i >= 16) break;
            String v = c.getValue(String.class);
            clueFlat[i++] = v != null ? v : "?";
        }
        if (i < 16) return null;
        String[][] clues = new String[4][4];
        for (int c = 0; c < 4; c++)
            for (int r = 0; r < 4; r++)
                clues[c][r] = clueFlat[c * 4 + r];
        String[] colSols = new String[4];
        i = 0;
        for (DataSnapshot s : snap.child("colSolutions").getChildren()) {
            if (i >= 4) break;
            String v = s.getValue(String.class);
            colSols[i++] = v != null ? v : "?";
        }
        if (i < 4) return null;
        return new com.example.slagalica.data.model.AsocijacijePuzzle(clues, colSols, finalSol);
    }

    // =========================================================================
    // Skočko — model klase
    // =========================================================================

    /** Jedan pokušaj igrača — guess[4] i hints[4] kao mape sa string ključevima. */
    public static class SkockoAttemptData {
        public Map<String, Integer> guess;
        public Map<String, Integer> hints;

        public SkockoAttemptData() {}

        public SkockoAttemptData(int[] g, int[] h) {
            guess = new HashMap<>();
            hints = new HashMap<>();
            for (int i = 0; i < g.length; i++) {
                guess.put(String.valueOf(i), g[i]);
                hints.put(String.valueOf(i), h[i]);
            }
        }

        // Napomena: ove metode NAMERNO nisu imenovane getGuessArray()/getHintsArray() —
        // Firebase-ov CustomClassMapper tretira svaki public getXxx() kao bean-property
        // pri serializaciji (setValue()) i pokušava da serijalizuje int[], što baca
        // "Serializing Arrays is not supported" jer RTDB ne podržava nizove kao tip.
        public int[] guessArray() {
            int[] a = new int[4];
            for (int i = 0; i < 4; i++) {
                Integer v = guess != null ? guess.get(String.valueOf(i)) : null;
                a[i] = v != null ? v : 0;
            }
            return a;
        }

        public int[] hintsArray() {
            int[] a = new int[4];
            for (int i = 0; i < 4; i++) {
                Integer v = hints != null ? hints.get(String.valueOf(i)) : null;
                a[i] = v != null ? v : 0;
            }
            return a;
        }
    }

    // =========================================================================
    // Skočko — callback interfejsi
    // =========================================================================

    public interface SkockoStateListener {
        void onState(@Nullable int[] secret, @NonNull String phase,
                     @NonNull Map<Integer, SkockoAttemptData> mainAttempts,
                     @Nullable SkockoAttemptData opponentAttempt);
    }

    // =========================================================================
    // Skočko — metode
    // =========================================================================

    /** Aktivni igrač upisuje tajnu kombinaciju na početku runde. */
    public void writeSkockoSecret(@NonNull String matchId, int round, @NonNull int[] secret) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < secret.length; i++) m.put(String.valueOf(i), secret[i]);
        skockoRoundRef(matchId, round).child("secret").setValue(m);
    }

    /** Postavlja fazu Skočko runde. */
    public void setSkockoPhase(@NonNull String matchId, int round, @NonNull String phase) {
        skockoRoundRef(matchId, round).child("phase").setValue(phase);
    }

    /** Aktivni igrač upisuje pokušaj (guess + hints) na indeksu {@code attemptIdx}. */
    public void submitSkockoMainAttempt(@NonNull String matchId, int round, int attemptIdx,
                                          @NonNull int[] guess, @NonNull int[] hints) {
        skockoRoundRef(matchId, round).child("mainAttempts")
                .child(String.valueOf(attemptIdx))
                .setValue(new SkockoAttemptData(guess, hints));
    }

    /** Pasivni igrač upisuje svoj jedini pokušaj u fazi OPPONENT_CHANCE. */
    public void submitSkockoOpponentAttempt(@NonNull String matchId, int round,
                                              @NonNull int[] guess, @NonNull int[] hints) {
        skockoRoundRef(matchId, round).child("opponentAttempt")
                .setValue(new SkockoAttemptData(guess, hints));
    }

    /** Oba igrača slušaju celo stanje Skočko runde u realnom vremenu. */
    public Runnable listenSkockoState(@NonNull String matchId, int round,
                                       @NonNull SkockoStateListener listener) {
        DatabaseReference ref = skockoRoundRef(matchId, round);
        ValueEventListener vel = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                int[] secret = null;
                if (snap.hasChild("secret")) {
                    secret = new int[4];
                    for (int i = 0; i < 4; i++) {
                        Long v = snap.child("secret").child(String.valueOf(i)).getValue(Long.class);
                        secret[i] = v != null ? v.intValue() : 0;
                    }
                }
                String phase = snap.child("phase").getValue(String.class);
                if (phase == null) phase = "";

                Map<Integer, SkockoAttemptData> mainAttempts = new HashMap<>();
                for (DataSnapshot aSnap : snap.child("mainAttempts").getChildren()) {
                    if (aSnap.getKey() == null) continue;
                    int idx;
                    try { idx = Integer.parseInt(aSnap.getKey()); } catch (NumberFormatException e) { continue; }
                    mainAttempts.put(idx, attemptFromSnapshot(aSnap));
                }

                SkockoAttemptData oppAttempt = null;
                if (snap.hasChild("opponentAttempt")) {
                    oppAttempt = attemptFromSnapshot(snap.child("opponentAttempt"));
                }

                listener.onState(secret, phase, mainAttempts, oppAttempt);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError e) {}
        };
        ref.addValueEventListener(vel);
        return () -> ref.removeEventListener(vel);
    }

    private SkockoAttemptData attemptFromSnapshot(@NonNull DataSnapshot snap) {
        SkockoAttemptData d = new SkockoAttemptData();
        d.guess = new HashMap<>();
        d.hints = new HashMap<>();
        for (DataSnapshot g : snap.child("guess").getChildren()) {
            Long v = g.getValue(Long.class);
            if (g.getKey() != null && v != null) d.guess.put(g.getKey(), v.intValue());
        }
        for (DataSnapshot h : snap.child("hints").getChildren()) {
            Long v = h.getValue(Long.class);
            if (h.getKey() != null && v != null) d.hints.put(h.getKey(), v.intValue());
        }
        return d;
    }

    // =========================================================================
    // Pomoćne reference
    // =========================================================================

    private DatabaseReference matchRef(String matchId) {
        return database.getReference(Constants.RTDB_MATCHES).child(matchId);
    }

    private DatabaseReference spojniceStateRef(String matchId, int round) {
        return matchRef(matchId).child("spojnice").child("state").child(String.valueOf(round));
    }

    private DatabaseReference mojBrojRoundRef(String matchId, int round) {
        return matchRef(matchId).child("mojBroj").child("rounds").child(String.valueOf(round));
    }

    private DatabaseReference korakRoundRef(String matchId, int round) {
        return matchRef(matchId).child("korakPoKorak").child("rounds").child(String.valueOf(round));
    }

    private DatabaseReference skockoRoundRef(String matchId, int round) {
        return matchRef(matchId).child("skocko").child("rounds").child(String.valueOf(round));
    }
}

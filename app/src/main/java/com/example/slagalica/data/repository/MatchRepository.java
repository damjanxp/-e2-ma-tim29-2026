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
}

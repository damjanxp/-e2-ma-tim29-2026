package com.example.slagalica.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.data.model.Challenge;
import com.example.slagalica.data.model.ChallengeParticipant;
import com.example.slagalica.data.model.User;
import com.example.slagalica.logic.match.ChallengePayout;
import com.example.slagalica.util.Constants;
import com.example.slagalica.util.RegionKey;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Izazov (solo prolazak kroz svih šest igara sa ulogom) kroz Firebase
 * Realtime Database.
 *
 * <p>Struktura podataka: {@code challenges/{challengeId}}, sa učesnicima u
 * {@code participants/{uid}} (vidi {@link Challenge}, {@link ChallengeParticipant}).</p>
 *
 * <p>Slušaoci vraćeni iz {@link #listenOpenChallenges} i {@link #listenChallenge}
 * se obavezno uklanjaju u {@code onDestroy()} pozivom vraćenog {@link Runnable}-a.</p>
 */
public class ChallengeRepository {

    private static final int MAX_PARTICIPANTS = 4;
    private static final int MIN_STAKE_STARS = 1;
    private static final int MAX_STAKE_STARS = 10;
    private static final int MIN_STAKE_TOKENS = 0;
    private static final int MAX_STAKE_TOKENS = 2;

    private static final String STATUS_OPEN     = "open";
    private static final String STATUS_PLAYING  = "playing";
    private static final String STATUS_FINISHED = "finished";

    /** Callback bez povratne vrednosti. */
    public interface SimpleCallback {
        void onSuccess();
        void onError(@NonNull String message);
    }

    /** Slušalac liste otvorenih izazova u jednom regionu. */
    public interface OpenChallengesListener {
        void onChallenges(@NonNull List<Challenge> challenges);
    }

    /** Slušalac jednog izazova (lobi i rezultat). */
    public interface ChallengeListener {
        void onChallenge(@NonNull Challenge challenge);
    }

    private static ChallengeRepository instance;

    private final FirebaseDatabase database;
    private final UserRepository userRepository;

    private ChallengeRepository() {
        // Eksplicitna adresa baze (europe-west1) — bez ovoga se SDK povezuje na
        // pogrešan podrazumevani region jer google-services.json nema firebase_url.
        database = FirebaseDatabase.getInstance(Constants.RTDB_URL);
        userRepository = UserRepository.getInstance();
    }

    public static synchronized ChallengeRepository getInstance() {
        if (instance == null) {
            instance = new ChallengeRepository();
        }
        return instance;
    }

    // =========================================================================
    // Kreiranje i pridruživanje
    // =========================================================================

    /** Kreira novi izazov: skida ulog od domaćina i upisuje ga kao prvog učesnika. */
    public void createChallenge(@NonNull User host, int stakeStars, int stakeTokens,
                                @NonNull SimpleCallback cb) {
        if (stakeStars < MIN_STAKE_STARS || stakeStars > MAX_STAKE_STARS) {
            cb.onError("Ulog mora biti između 1 i 10 zvezda.");
            return;
        }
        if (stakeTokens < MIN_STAKE_TOKENS || stakeTokens > MAX_STAKE_TOKENS) {
            cb.onError("Ulog mora biti između 0 i 2 žetona.");
            return;
        }

        String challengeId = challengesRef().push().getKey();
        if (challengeId == null) {
            cb.onError("Kreiranje izazova nije uspelo.");
            return;
        }

        userRepository.deductChallengeStake(host.getUid(), stakeStars, stakeTokens,
                new UserRepository.SimpleCallback() {
                    @Override
                    public void onSuccess() {
                        Challenge challenge = new Challenge();
                        challenge.setId(challengeId);
                        challenge.setHostUid(host.getUid());
                        challenge.setHostName(host.getUsername());
                        challenge.setRegion(RegionKey.toKey(host.getRegion()));
                        challenge.setStakeStars(stakeStars);
                        challenge.setStakeTokens(stakeTokens);
                        challenge.setStatus(STATUS_OPEN);
                        challenge.setCreatedAt(System.currentTimeMillis());

                        Map<String, ChallengeParticipant> participants = new HashMap<>();
                        participants.put(host.getUid(), new ChallengeParticipant(
                                host.getUid(), host.getUsername(), true, false, 0, 0));
                        challenge.setParticipants(participants);

                        challengesRef().child(challengeId).setValue(challenge)
                                .addOnSuccessListener(v -> cb.onSuccess())
                                .addOnFailureListener(e -> cb.onError("Kreiranje izazova nije uspelo."));
                    }

                    @Override
                    public void onError(String message) {
                        cb.onError(message);
                    }
                });
    }

    /** Pridružuje igrača otvorenom izazovu, uz isti ulog kao ostali učesnici. */
    public void joinChallenge(@NonNull String challengeId, @NonNull User user,
                              @NonNull SimpleCallback cb) {
        challengeRef(challengeId).get().addOnSuccessListener(snapshot -> {
            Challenge challenge = snapshot.getValue(Challenge.class);
            if (challenge == null) {
                cb.onError("Izazov nije pronađen.");
                return;
            }
            Map<String, ChallengeParticipant> participants = challenge.getParticipants();
            int participantCount = participants != null ? participants.size() : 0;

            if (!STATUS_OPEN.equals(challenge.getStatus())) {
                cb.onError("Izazov više nije otvoren za pridruživanje.");
                return;
            }
            if (participantCount >= MAX_PARTICIPANTS) {
                cb.onError("Izazov je pun.");
                return;
            }
            if (participants != null && participants.containsKey(user.getUid())) {
                cb.onError("Već si pridružen/a ovom izazovu.");
                return;
            }

            userRepository.deductChallengeStake(user.getUid(),
                    challenge.getStakeStars(), challenge.getStakeTokens(),
                    new UserRepository.SimpleCallback() {
                        @Override
                        public void onSuccess() {
                            ChallengeParticipant participant = new ChallengeParticipant(
                                    user.getUid(), user.getUsername(), true, false, 0, 0);
                            challengeRef(challengeId).child("participants").child(user.getUid())
                                    .setValue(participant)
                                    .addOnSuccessListener(v -> cb.onSuccess())
                                    .addOnFailureListener(e -> cb.onError("Pridruživanje izazovu nije uspelo."));
                        }

                        @Override
                        public void onError(String message) {
                            cb.onError(message);
                        }
                    });
        }).addOnFailureListener(e ->
                cb.onError("Učitavanje izazova nije uspelo. Proveri internet konekciju."));
    }

    // =========================================================================
    // Slušaoci
    // =========================================================================

    /**
     * Sluša otvorene izazove u regionu {@code regionKey}.
     *
     * <p>Koristi RTDB upit po polju {@code region} — za veći broj izazova
     * preporučljivo je dodati {@code ".indexOn": ["region"]} pravilo za tu granu.</p>
     */
    public Runnable listenOpenChallenges(@NonNull String regionKey,
                                         @NonNull OpenChallengesListener listener) {
        Query query = challengesRef().orderByChild("region").equalTo(regionKey);
        ValueEventListener vel = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Challenge> challenges = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Challenge challenge = child.getValue(Challenge.class);
                    if (challenge != null && STATUS_OPEN.equals(challenge.getStatus())) {
                        challenges.add(challenge);
                    }
                }
                Collections.sort(challenges,
                        (a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
                listener.onChallenges(challenges);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // prekid veze se tiho ignoriše — lista ostaje kakva je bila
            }
        };
        query.addValueEventListener(vel);
        return () -> query.removeEventListener(vel);
    }

    /** Sluša jedan izazov u realnom vremenu (lobi i rezultat). */
    public Runnable listenChallenge(@NonNull String challengeId,
                                    @NonNull ChallengeListener listener) {
        DatabaseReference ref = challengeRef(challengeId);
        ValueEventListener vel = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Challenge challenge = snapshot.getValue(Challenge.class);
                if (challenge != null) {
                    listener.onChallenge(challenge);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // prekid veze se tiho ignoriše
            }
        };
        ref.addValueEventListener(vel);
        return () -> ref.removeEventListener(vel);
    }

    // =========================================================================
    // Pokretanje, rezultat i raspodela nagrada
    // =========================================================================

    /** Domaćin pokreće izazov — menja status u "playing" (svi učesnici kreću u solo rundu). */
    public void startChallenge(@NonNull String challengeId, @Nullable SimpleCallback cb) {
        challengeRef(challengeId).child("status").setValue(STATUS_PLAYING)
                .addOnSuccessListener(v -> { if (cb != null) cb.onSuccess(); })
                .addOnFailureListener(e -> { if (cb != null) cb.onError("Pokretanje izazova nije uspelo."); });
    }

    /**
     * Upisuje konačan rezultat solo runde jednog učesnika. Kada svi pridruženi
     * učesnici završe, automatski se poziva {@link #finalizeChallenge}.
     */
    public void submitScore(@NonNull String challengeId, @NonNull String uid, int score,
                            @NonNull SimpleCallback cb) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("score", score);
        updates.put("finished", true);
        updates.put("finishedAt", System.currentTimeMillis());

        challengeRef(challengeId).child("participants").child(uid).updateChildren(updates)
                .addOnSuccessListener(v -> {
                    cb.onSuccess();
                    checkAllFinishedThenFinalize(challengeId);
                })
                .addOnFailureListener(e -> cb.onError("Slanje rezultata nije uspelo."));
    }

    private void checkAllFinishedThenFinalize(String challengeId) {
        challengeRef(challengeId).get().addOnSuccessListener(snapshot -> {
            Challenge challenge = snapshot.getValue(Challenge.class);
            if (challenge == null
                    || (!STATUS_OPEN.equals(challenge.getStatus()) && !STATUS_PLAYING.equals(challenge.getStatus()))) {
                return; // već finalizovan ili ne postoji
            }
            Map<String, ChallengeParticipant> participants = challenge.getParticipants();
            if (participants == null || participants.isEmpty()) {
                return;
            }
            for (ChallengeParticipant p : participants.values()) {
                if (p.isJoined() && !p.isFinished()) {
                    return; // još neko igra
                }
            }
            finalizeChallenge(challengeId);
        });
    }

    /**
     * Raspodeljuje pot (75% pobedniku, ulog nazad drugoplasiranom) po pravilima
     * iz {@link ChallengePayout} i postavlja status na "finished". Idempotentno
     * — ne radi ništa ako je izazov već finaliziran ili ne postoji.
     */
    public void finalizeChallenge(@NonNull String challengeId) {
        challengeRef(challengeId).get().addOnSuccessListener(snapshot -> {
            Challenge challenge = snapshot.getValue(Challenge.class);
            if (challenge == null || STATUS_FINISHED.equals(challenge.getStatus())) {
                return; // idempotentno — već finalizovan ili ne postoji
            }
            Map<String, ChallengeParticipant> participants = challenge.getParticipants();
            if (participants == null || participants.isEmpty()) {
                return;
            }

            Map<String, Integer> scores = new HashMap<>();
            Map<String, Long> finishedAt = new HashMap<>();
            for (ChallengeParticipant p : participants.values()) {
                scores.put(p.getUid(), p.getScore());
                finishedAt.put(p.getUid(), p.getFinishedAt());
            }

            int participantCount = participants.size();
            int totalPotStars = challenge.getStakeStars() * participantCount;
            int totalPotTokens = challenge.getStakeTokens() * participantCount;

            Map<String, ChallengePayout.Payout> payouts = ChallengePayout.compute(
                    scores, finishedAt, totalPotStars, totalPotTokens,
                    challenge.getStakeStars(), challenge.getStakeTokens());

            int stakeStars = challenge.getStakeStars();
            for (Map.Entry<String, ChallengePayout.Payout> entry : payouts.entrySet()) {
                String participantUid = entry.getKey();
                ChallengePayout.Payout payout = entry.getValue();
                if (payout.getStars() > 0 || payout.getTokens() > 0) {
                    userRepository.creditChallengeReward(
                            participantUid, payout.getStars(), payout.getTokens(), null);
                }
                // Drugoplasirani dobija tačno svoj ulog nazad (neto 0 — nije
                // "osvojeno"). Samo pobednikov neto dobitak (pot minus sopstveni
                // ulog) ide na rang listu, da refundacija ne naduva plasman.
                int netEarnedStars = payout.getStars() - stakeStars;
                if (netEarnedStars > 0) {
                    userRepository.addLeaderboardStars(participantUid, netEarnedStars, null);
                }
            }

            challengeRef(challengeId).child("status").setValue(STATUS_FINISHED);
        });
    }

    // =========================================================================
    // Pomoćne reference
    // =========================================================================

    private DatabaseReference challengesRef() {
        return database.getReference(Constants.RTDB_CHALLENGES);
    }

    private DatabaseReference challengeRef(String challengeId) {
        return challengesRef().child(challengeId);
    }
}

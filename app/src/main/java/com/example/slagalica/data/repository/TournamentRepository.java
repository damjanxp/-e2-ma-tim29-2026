package com.example.slagalica.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.data.model.Tournament;
import com.example.slagalica.data.model.TournamentPlayer;
import com.example.slagalica.data.model.TournamentSlot;
import com.example.slagalica.util.Constants;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turnir za četiri igrača kroz Firebase Realtime Database — dva polufinala i
 * finale između pobednika. Nadovezuje se na postojeći 1v1 lanac igara: svaki
 * meč turnira je običan {@code matches/{matchId}} čvor, dodatno obeležen poljima
 * {@code tournamentId} i {@code stage} da bi {@code MatchResultActivity} znao
 * kome da prijavi pobednika.
 *
 * <p>Umesto reda za čekanje, koriste se fiksni lobiji (tri, za demo). Članstvo u
 * lobiju je TRAJNO — igrač ostaje član i kada napusti ekran ili aplikaciju, pa se
 * lobi puni tokom vremena (i sa jednim uređajem, naizmeničnom prijavom naloga).
 * Kada četvrti igrač popuni lobi, isti klijent formira turnir i upiše {@code tid};
 * članovi (koji lobi slušaju ili mu se vrate) tada prelaze u bracket. Lobi se sam
 * oslobađa kada se turnir završi (vidi {@link #finalizeChampion}); pre toga se u
 * njega ne može ući ({@code tid} postavljen).</p>
 *
 * <p>Struktura podataka:</p>
 * <pre>
 * tournamentLobbies/{lobbyId}
 *   players/{uid}         — {username, avatarIndex}   (trajno članstvo)
 *   tid?                  — postavljen kada se lobi popuni i turnir formira
 * tournaments/{tid}
 *   status                — SEMIS | FINAL | DONE
 *   lobbyId               — matični lobi (za reset po završetku)
 *   players/{uid}         — {uid, username, avatarIndex}
 *   semifinal1/semifinal2 — {matchId, p1Uid, p2Uid, winnerUid?, ready/{uid}}
 *   final                 — {matchId, p1Uid, p2Uid, winnerUid?, ready/{uid}}
 *   champion              — uid pobednika
 * </pre>
 */
public class TournamentRepository {

    /** Rezultat pokušaja ulaska u lobi. */
    public interface JoinLobbyListener {
        void onJoined(@NonNull String lobbyId);
        void onError(@NonNull String message);
    }

    /** Slušalac popunjenosti jednog lobija (za listu lobija). */
    public interface LobbyInfoListener {
        void onLobby(int count, boolean formed, @Nullable String tournamentId, boolean iAmMember);
    }

    /** Slušalac stanja jednog turnira. */
    public interface TournamentListener {
        void onTournament(@NonNull Tournament tournament);
    }

    private static TournamentRepository instance;

    private final FirebaseDatabase database;

    private TournamentRepository() {
        database = FirebaseDatabase.getInstance(Constants.RTDB_URL);
    }

    public static synchronized TournamentRepository getInstance() {
        if (instance == null) {
            instance = new TournamentRepository();
        }
        return instance;
    }

    // =========================================================================
    // Lobiji i formiranje turnira
    // =========================================================================

    /**
     * Sluša popunjenost jednog lobija (za listu lobija). Vraća broj igrača, da li
     * je turnir već formiran ({@code tid != null}) i sam {@code tid}.
     */
    public Runnable listenLobby(@NonNull String lobbyId, @NonNull String myUid,
                                @NonNull LobbyInfoListener listener) {
        DatabaseReference ref = lobbyRef(lobbyId);
        ValueEventListener vel = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int count = (int) snapshot.child("players").getChildrenCount();
                String tid = snapshot.child("tid").getValue(String.class);
                boolean iAmMember = snapshot.child("players").hasChild(myUid);
                listener.onLobby(count, tid != null, tid, iAmMember);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // ignorisano
            }
        };
        ref.addValueEventListener(vel);
        return () -> ref.removeEventListener(vel);
    }

    /**
     * Ulazi u lobi. Ako ovim ulaskom lobi dostigne {@link Constants#TOURNAMENT_SIZE}
     * igrača, isti klijent (koji je ušao kao četvrti) formira turnir — pravi
     * bracket i mečeve, pa upisuje {@code tid} u lobi. Ostali članovi lobija,
     * koji ga slušaju, tada saznaju da je turnir počeo (vidi
     * {@code TournamentLobbyListActivity}).
     *
     * <p>Ulazak je blokiran ako je lobi pun ili je turnir već u toku ({@code tid}
     * postavljen) — tako igrač koji stigne kasno ne upadne u tuđi turnir.</p>
     */
    public void joinLobby(@NonNull String lobbyId, @NonNull String uid,
                          @NonNull String username, int avatarIndex,
                          @NonNull JoinLobbyListener listener) {
        final String candidateTid = tournamentsRef().push().getKey();
        final boolean[] wasAdded = {false};
        lobbyRef(lobbyId).runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                wasAdded[0] = false;
                if (currentData.child("tid").getValue() != null) {
                    return Transaction.abort(); // turnir već u toku / lobi se prazni
                }
                MutableData players = currentData.child("players");
                boolean alreadyMember = players.hasChild(uid);
                long count = players.getChildrenCount();
                if (!alreadyMember) {
                    if (count >= Constants.TOURNAMENT_SIZE) {
                        return Transaction.abort(); // pun
                    }
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("username", username);
                    entry.put("avatarIndex", avatarIndex);
                    players.child(uid).setValue(entry);
                    wasAdded[0] = true;
                }
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed,
                                   @Nullable DataSnapshot currentData) {
                if (!committed || currentData == null) {
                    listener.onError("Lobi je pun ili je turnir već počeo.");
                    return;
                }
                listener.onJoined(lobbyId);
                long count = currentData.child("players").getChildrenCount();
                // Ako smo ušli kao četvrti — mi formiramo turnir i tek onda
                // upisujemo tid (tako ostali članovi ulaze u već spreman bracket).
                if (wasAdded[0] && count == Constants.TOURNAMENT_SIZE && candidateTid != null) {
                    formTournamentNode(candidateTid, lobbyId, currentData.child("players"));
                    lobbyRef(lobbyId).child("tid").setValue(candidateTid);
                }
            }
        });
    }

    /**
     * Eksplicitno napuštanje lobija (dugme "Napusti lobi"). Članstvo je inače
     * trajno — izlazak sa ekrana NE uklanja igrača — pa se ovo poziva samo na
     * izričitu akciju korisnika. Kada izađe poslednji član, ceo čvor lobija se
     * briše, pa lobi ponovo postaje prazan i spreman za nov turnir.
     *
     * <p>Napomena: po formiranju turnira lobi se sam resetuje kada se turnir
     * završi (vidi {@link #finalizeChampion}).</p>
     */
    public void leaveLobby(@NonNull String lobbyId, @NonNull String uid) {
        lobbyRef(lobbyId).runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                MutableData players = currentData.child("players");
                if (players.hasChild(uid)) {
                    players.child(uid).setValue(null);
                }
                if (players.getChildrenCount() == 0) {
                    currentData.setValue(null); // prazan lobi — resetuj (uklanja i tid)
                }
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed,
                                   @Nullable DataSnapshot currentData) {
                // bez povratne informacije
            }
        });
    }

    /** Gradi bracket i mečeve za četiri igrača iz popunjenog lobija. */
    private void formTournamentNode(@NonNull String newTid, @NonNull String lobbyId,
                                    @NonNull DataSnapshot playersSnapshot) {
        List<TournamentPlayer> players = new ArrayList<>();
        for (DataSnapshot child : playersSnapshot.getChildren()) {
            if (child.getKey() == null) continue;
            String username = child.child("username").getValue(String.class);
            int avatarIndex = (int) asLong(child.child("avatarIndex").getValue());
            players.add(new TournamentPlayer(child.getKey(),
                    username != null ? username : "?", avatarIndex));
        }
        if (players.size() < Constants.TOURNAMENT_SIZE) {
            return; // sigurnosna provera — ne bi trebalo da se desi
        }
        Collections.shuffle(players); // nasumično uparivanje
        TournamentPlayer a = players.get(0), b = players.get(1);
        TournamentPlayer c = players.get(2), d = players.get(3);

        String matchId1 = matchesRef().push().getKey();
        String matchId2 = matchesRef().push().getKey();
        if (matchId1 == null || matchId2 == null) {
            return;
        }

        Map<String, Object> playersMap = new HashMap<>();
        for (TournamentPlayer p : players) {
            Map<String, Object> pm = new HashMap<>();
            pm.put("uid", p.getUid());
            pm.put("username", p.getUsername());
            pm.put("avatarIndex", p.getAvatarIndex());
            playersMap.put(p.getUid(), pm);
        }

        Map<String, Object> tournament = new HashMap<>();
        tournament.put("status", Constants.TOURNAMENT_STATUS_SEMIS);
        tournament.put("createdAt", ServerValue.TIMESTAMP);
        tournament.put("lobbyId", lobbyId); // za reset lobija kada se turnir završi
        tournament.put("players", playersMap);
        tournament.put("semifinal1", slotMap(matchId1, a.getUid(), b.getUid()));
        tournament.put("semifinal2", slotMap(matchId2, c.getUid(), d.getUid()));

        tournamentsRef().child(newTid).setValue(tournament);

        createTournamentMatchNode(matchId1, newTid, Constants.TOURNAMENT_SLOT_SEMI1, a, b);
        createTournamentMatchNode(matchId2, newTid, Constants.TOURNAMENT_SLOT_SEMI2, c, d);
    }

    private Map<String, Object> slotMap(String matchId, String p1Uid, String p2Uid) {
        Map<String, Object> slot = new HashMap<>();
        slot.put("matchId", matchId);
        slot.put("p1Uid", p1Uid);
        slot.put("p2Uid", p2Uid);
        return slot;
    }

    /**
     * Pravi {@code matches/{matchId}} čvor za turnirski meč. Struktura je ista
     * kao kod 1v1 mečeva (player1/player2), uz dodatna polja {@code tournamentId}
     * i {@code stage} po kojima {@code MatchResultActivity} prepoznaje turnir.
     */
    private void createTournamentMatchNode(@NonNull String matchId, @NonNull String tid,
                                           @NonNull String stage,
                                           @NonNull TournamentPlayer p1,
                                           @NonNull TournamentPlayer p2) {
        Map<String, Object> player1 = new HashMap<>();
        player1.put("uid", p1.getUid());
        player1.put("username", p1.getUsername());

        Map<String, Object> player2 = new HashMap<>();
        player2.put("uid", p2.getUid());
        player2.put("username", p2.getUsername());

        Map<String, Object> match = new HashMap<>();
        match.put("player1", player1);
        match.put("player2", player2);
        match.put("status", "active");
        match.put("createdAt", ServerValue.TIMESTAMP);
        match.put("tournamentId", tid);
        match.put("stage", stage);

        matchesRef().child(matchId).setValue(match);
    }

    // =========================================================================
    // Praćenje stanja turnira
    // =========================================================================

    /** Sluša ceo turnir u realnom vremenu (bracket, spremnost, pobednici). */
    public Runnable listenTournament(@NonNull String tid, @NonNull TournamentListener listener) {
        DatabaseReference ref = tournamentsRef().child(tid);
        ValueEventListener vel = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    return;
                }
                listener.onTournament(parseTournament(tid, snapshot));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // ignorisano
            }
        };
        ref.addValueEventListener(vel);
        return () -> ref.removeEventListener(vel);
    }

    /** Označava da je igrač spreman da uđe u meč datog slota. */
    public void setReady(@NonNull String tid, @NonNull String slotKey, @NonNull String uid) {
        tournamentsRef().child(tid).child(slotKey).child("ready").child(uid).setValue(true);
    }

    /**
     * Prijavljuje pobednika meča (idempotentno — prvi upis pobeđuje). Kada oba
     * polufinala imaju pobednika, automatski kreira finale; kada finale ima
     * pobednika, upisuje šampiona i status DONE.
     */
    public void reportWinner(@NonNull String tid, @NonNull String slotKey,
                             @NonNull String winnerUid) {
        DatabaseReference winnerRef =
                tournamentsRef().child(tid).child(slotKey).child("winnerUid");
        winnerRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                if (currentData.getValue() != null) {
                    return Transaction.abort(); // pobednik već upisan
                }
                currentData.setValue(winnerUid);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed,
                                   @Nullable DataSnapshot currentData) {
                if (Constants.TOURNAMENT_SLOT_FINAL.equals(slotKey)) {
                    finalizeChampion(tid);
                } else {
                    maybeCreateFinal(tid);
                }
            }
        });
    }

    /** Ako su oba polufinala rešena a finale još nije napravljeno — pravi ga. */
    private void maybeCreateFinal(@NonNull String tid) {
        tournamentsRef().child(tid).get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                return;
            }
            Tournament t = parseTournament(tid, snapshot);
            if (!t.bothSemifinalsDecided() || t.finalMatch.matchId != null) {
                return;
            }
            String w1 = t.semifinal1.winnerUid;
            String w2 = t.semifinal2.winnerUid;
            TournamentPlayer p1 = t.player(w1);
            TournamentPlayer p2 = t.player(w2);
            if (w1 == null || w2 == null || p1 == null || p2 == null) {
                return;
            }
            String finalMatchId = matchesRef().push().getKey();
            if (finalMatchId == null) {
                return;
            }
            DatabaseReference finalRef = tournamentsRef().child(tid).child("final");
            finalRef.runTransaction(new Transaction.Handler() {
                @NonNull
                @Override
                public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                    if (currentData.child("matchId").getValue() != null) {
                        return Transaction.abort(); // finale je već napravljeno
                    }
                    currentData.child("matchId").setValue(finalMatchId);
                    currentData.child("p1Uid").setValue(w1);
                    currentData.child("p2Uid").setValue(w2);
                    return Transaction.success(currentData);
                }

                @Override
                public void onComplete(@Nullable DatabaseError error, boolean committed,
                                       @Nullable DataSnapshot currentData) {
                    if (committed) {
                        createTournamentMatchNode(finalMatchId, tid,
                                Constants.TOURNAMENT_SLOT_FINAL, p1, p2);
                        tournamentsRef().child(tid).child("status")
                                .setValue(Constants.TOURNAMENT_STATUS_FINAL);
                    }
                }
            });
        });
    }

    /**
     * Upisuje šampiona i status DONE na osnovu pobednika finala (idempotentno) i
     * resetuje matični lobi (briše ga) da bi ponovo bio slobodan za nov turnir.
     */
    private void finalizeChampion(@NonNull String tid) {
        tournamentsRef().child(tid).get().addOnSuccessListener(snap -> {
            String champion = snap.child("final").child("winnerUid").getValue(String.class);
            if (champion == null) {
                return;
            }
            Map<String, Object> updates = new HashMap<>();
            updates.put("champion", champion);
            updates.put("status", Constants.TOURNAMENT_STATUS_DONE);
            tournamentsRef().child(tid).updateChildren(updates);

            String lobbyId = snap.child("lobbyId").getValue(String.class);
            if (lobbyId != null) {
                lobbyRef(lobbyId).setValue(null); // oslobodi lobi za ponovnu upotrebu
            }
        });
    }

    // =========================================================================
    // Parsiranje
    // =========================================================================

    private Tournament parseTournament(@NonNull String tid, @NonNull DataSnapshot snap) {
        Tournament t = new Tournament();
        t.id = tid;
        t.status = snap.child("status").getValue(String.class);
        t.champion = snap.child("champion").getValue(String.class);

        for (DataSnapshot p : snap.child("players").getChildren()) {
            String uid = p.getKey();
            if (uid == null) continue;
            String username = p.child("username").getValue(String.class);
            int avatarIndex = (int) asLong(p.child("avatarIndex").getValue());
            t.players.put(uid, new TournamentPlayer(uid,
                    username != null ? username : "?", avatarIndex));
        }

        parseSlot(snap.child("semifinal1"), t.semifinal1);
        parseSlot(snap.child("semifinal2"), t.semifinal2);
        parseSlot(snap.child("final"), t.finalMatch);
        return t;
    }

    private void parseSlot(@NonNull DataSnapshot snap, @NonNull TournamentSlot slot) {
        if (!snap.exists()) {
            return;
        }
        slot.matchId = snap.child("matchId").getValue(String.class);
        slot.p1Uid = snap.child("p1Uid").getValue(String.class);
        slot.p2Uid = snap.child("p2Uid").getValue(String.class);
        slot.winnerUid = snap.child("winnerUid").getValue(String.class);
        for (DataSnapshot r : snap.child("ready").getChildren()) {
            if (r.getKey() != null && Boolean.TRUE.equals(r.getValue(Boolean.class))) {
                slot.ready.add(r.getKey());
            }
        }
    }

    private static long asLong(@Nullable Object value) {
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        if (value instanceof Double) return ((Double) value).longValue();
        return 0L;
    }

    // =========================================================================
    // Pomoćne reference
    // =========================================================================

    private DatabaseReference lobbyRef(@NonNull String lobbyId) {
        return database.getReference(Constants.RTDB_TOURNAMENT_LOBBIES).child(lobbyId);
    }

    private DatabaseReference tournamentsRef() {
        return database.getReference(Constants.RTDB_TOURNAMENTS);
    }

    private DatabaseReference matchesRef() {
        return database.getReference(Constants.RTDB_MATCHES);
    }
}

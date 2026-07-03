package com.example.slagalica.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.data.model.Friendship;
import com.example.slagalica.data.model.User;
import com.example.slagalica.util.Constants;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Singleton repozitorijum za prijatelje ("7. Prijatelji").
 *
 * <p>Lista prijatelja se čuva simetrično u Firestore podkolekciji
 * {@code users/{uid}/friends/{friendUid}} (vidi {@link Friendship}) — samo
 * kao pokazivač; trenutni profil (avatar, liga, zvezde) se uvek čita uživo
 * iz {@code users/{friendUid}}.</p>
 *
 * <p>Poziv na prijateljsku partiju ide preko Realtime Database putanje
 * {@code friendInvites/{targetUid}} — jedan aktivan poziv po primaocu, sa
 * statusima {@code pending → accepted/declined}. Ovo radi samo dok primalac
 * ima otvoren {@code FriendsActivity} (nema Cloud Functions/push-a sa servera
 * na besplatnom planu — ista simplifikacija kao kod regionalnog četa, vidi
 * {@link ChatRepository}).</p>
 */
public class FriendRepository {

    private static final String STATUS_PENDING  = "pending";
    private static final String STATUS_ACCEPTED = "accepted";
    private static final String STATUS_DECLINED = "declined";

    private static FriendRepository instance;

    private final FirebaseFirestore mDb;
    private final FirebaseDatabase mRtdb;

    private FriendRepository() {
        mDb = FirebaseFirestore.getInstance();
        // Eksplicitna adresa baze (europe-west1) — vidi napomenu u MatchRepository.
        mRtdb = FirebaseDatabase.getInstance(Constants.RTDB_URL);
    }

    @NonNull
    public static synchronized FriendRepository getInstance() {
        if (instance == null) {
            instance = new FriendRepository();
        }
        return instance;
    }

    // -------------------------------------------------------------------------
    // Callback interfejsi
    // -------------------------------------------------------------------------

    public interface SimpleCallback {
        void onSuccess();
        void onError(@NonNull String message);
    }

    public interface FriendsListCallback {
        void onSuccess(@NonNull List<User> friends);
        void onError(@NonNull String message);
    }

    public interface UserSearchCallback {
        void onSuccess(@NonNull List<User> users);
        void onError(@NonNull String message);
    }

    public interface BooleanCallback {
        void onResult(boolean value);
    }

    /** Status poziva na partiju koji vidi strana koja poziva. */
    public interface InviteStatusListener {
        void onPending();
        void onAccepted(@NonNull String matchId, @NonNull String friendUsername);
        void onDeclined();
    }

    /** Dolazni poziv na partiju koji vidi pozvana strana. */
    public interface IncomingInviteListener {
        void onIncomingInvite(@NonNull String fromUid, @NonNull String fromName);
        void onCleared();
    }

    /** Ishod odgovora pozvane strane na dolazni poziv. */
    public interface RespondCallback {
        void onAccepted(@NonNull String matchId);
        void onDeclined();
        void onError(@NonNull String message);
    }

    // -------------------------------------------------------------------------
    // Lista prijatelja
    // -------------------------------------------------------------------------

    /** Dodaje prijateljstvo simetrično kod oba korisnika (jedan batch upis). */
    public void addFriend(@NonNull String myUid, @NonNull String myUsername,
                          @NonNull String friendUid, @NonNull String friendUsername,
                          @NonNull SimpleCallback cb) {
        if (myUid.equals(friendUid)) {
            cb.onError("Ne možeš dodati sebe kao prijatelja.");
            return;
        }
        long now = System.currentTimeMillis();
        WriteBatch batch = mDb.batch();
        batch.set(friendsRef(myUid).document(friendUid), new Friendship(friendUid, friendUsername, now));
        batch.set(friendsRef(friendUid).document(myUid), new Friendship(myUid, myUsername, now));
        batch.commit()
                .addOnSuccessListener(v -> cb.onSuccess())
                .addOnFailureListener(e -> cb.onError("Dodavanje prijatelja nije uspelo."));
    }

    /** Proverava da li je {@code otherUid} već prijatelj korisnika {@code myUid}. */
    public void isFriend(@NonNull String myUid, @NonNull String otherUid, @NonNull BooleanCallback cb) {
        friendsRef(myUid).document(otherUid).get()
                .addOnSuccessListener(doc -> cb.onResult(doc.exists()))
                .addOnFailureListener(e -> cb.onResult(false));
    }

    /**
     * Učitava listu prijatelja sa <b>trenutnim</b> podacima profila (avatar,
     * liga, zvezde, mesečne zvezde) — čita svaki {@code users/{friendUid}}
     * dokument uživo, ne oslanja se na keširano ime iz {@link Friendship}.
     */
    public void getFriends(@NonNull String myUid, @NonNull FriendsListCallback cb) {
        friendsRef(myUid).get()
                .addOnSuccessListener(snapshot -> {
                    List<String> friendUids = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        String uid = doc.getString("friendUid");
                        if (uid != null) friendUids.add(uid);
                    }
                    if (friendUids.isEmpty()) {
                        cb.onSuccess(new ArrayList<>());
                        return;
                    }
                    loadLiveProfiles(friendUids, cb);
                })
                .addOnFailureListener(e ->
                        cb.onError("Učitavanje liste prijatelja nije uspelo. Proveri internet konekciju."));
    }

    private void loadLiveProfiles(@NonNull List<String> uids, @NonNull FriendsListCallback cb) {
        List<User> results = new ArrayList<>();
        int[] pending = {uids.size()};
        boolean[] failed = {false};

        for (String uid : uids) {
            mDb.collection(Constants.COLLECTION_USERS).document(uid).get()
                    .addOnSuccessListener(doc -> {
                        User u = doc.toObject(User.class);
                        if (u != null) results.add(u);
                        pending[0]--;
                        if (pending[0] == 0 && !failed[0]) {
                            results.sort((a, b) -> a.getUsername().compareToIgnoreCase(b.getUsername()));
                            cb.onSuccess(results);
                        }
                    })
                    .addOnFailureListener(e -> {
                        pending[0]--;
                        if (pending[0] == 0 && !failed[0]) {
                            results.sort((a, b) -> a.getUsername().compareToIgnoreCase(b.getUsername()));
                            cb.onSuccess(results);
                        }
                    });
        }
    }

    // -------------------------------------------------------------------------
    // Pretraga korisnika
    // -------------------------------------------------------------------------

    /**
     * Pretražuje korisnike po prefiksu korisničkog imena (Firestore range upit:
     * {@code startAt(prefix)}/{@code endAt(prefix + '')}).
     *
     * @param usernamePrefix uneti tekst pretrage (nije prazan)
     * @param excludeUid     uid trenutnog korisnika (izostavlja se iz rezultata)
     */
    public void searchUsersByUsername(@NonNull String usernamePrefix, @NonNull String excludeUid,
                                      @NonNull UserSearchCallback cb) {
        if (usernamePrefix.trim().isEmpty()) {
            cb.onSuccess(new ArrayList<>());
            return;
        }
        mDb.collection(Constants.COLLECTION_USERS)
                .orderBy("username")
                .startAt(usernamePrefix)
                .endAt(usernamePrefix + "")
                .limit(20)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<User> results = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        User u = doc.toObject(User.class);
                        if (u != null && !excludeUid.equals(u.getUid())) {
                            results.add(u);
                        }
                    }
                    cb.onSuccess(results);
                })
                .addOnFailureListener(e ->
                        cb.onError("Pretraga korisnika nije uspela. Proveri internet konekciju."));
    }

    // -------------------------------------------------------------------------
    // Poziv na prijateljsku partiju (Realtime Database)
    // -------------------------------------------------------------------------

    /**
     * Šalje poziv na partiju prijatelju. Prepisuje eventualni prethodni
     * (istekli) poziv istom primaocu.
     */
    public void sendMatchInvite(@NonNull String myUid, @NonNull String myUsername,
                                @NonNull String friendUid, @NonNull SimpleCallback cb) {
        Map<String, Object> invite = new HashMap<>();
        invite.put("fromUid", myUid);
        invite.put("fromName", myUsername);
        invite.put("status", STATUS_PENDING);
        invite.put("createdAt", System.currentTimeMillis());
        inviteRef(friendUid).setValue(invite)
                .addOnSuccessListener(v -> cb.onSuccess())
                .addOnFailureListener(e -> cb.onError("Slanje poziva nije uspelo."));
    }

    /** Prekida poslati poziv (igrač koji zove partiju može da odustane pre odgovora). */
    public void cancelMatchInvite(@NonNull String friendUid) {
        inviteRef(friendUid).removeValue();
    }

    /**
     * Strana koja poziva sluša status svog poslatog poziva
     * ({@code friendInvites/{friendUid}}, jer je to sanduče primaoca).
     */
    public Runnable listenForInviteStatus(@NonNull String friendUid, @NonNull String friendUsername,
                                          @NonNull InviteStatusListener listener) {
        DatabaseReference ref = inviteRef(friendUid);
        ValueEventListener vel = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    return;
                }
                String status = snapshot.child("status").getValue(String.class);
                if (STATUS_ACCEPTED.equals(status)) {
                    String matchId = snapshot.child("matchId").getValue(String.class);
                    if (matchId != null) {
                        listener.onAccepted(matchId, friendUsername);
                    }
                } else if (STATUS_DECLINED.equals(status)) {
                    listener.onDeclined();
                } else {
                    listener.onPending();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // prekid veze se tiho ignoriše — korisnik može ručno da otkaže poziv
            }
        };
        ref.addValueEventListener(vel);
        return () -> ref.removeEventListener(vel);
    }

    /**
     * Pozvana strana sluša dolazne pozive na sopstvenom sandučetu
     * ({@code friendInvites/{myUid}}). Radi samo dok je ovaj ekran otvoren —
     * nema Cloud Functions/push-a sa servera koji bi probudio aplikaciju.
     */
    public Runnable listenForIncomingInvites(@NonNull String myUid,
                                             @NonNull IncomingInviteListener listener) {
        DatabaseReference ref = inviteRef(myUid);
        ValueEventListener vel = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    listener.onCleared();
                    return;
                }
                String status = snapshot.child("status").getValue(String.class);
                if (!STATUS_PENDING.equals(status)) {
                    return; // već odgovoreno — čeka se da pošiljalac obriše čvor
                }
                String fromUid = snapshot.child("fromUid").getValue(String.class);
                String fromName = snapshot.child("fromName").getValue(String.class);
                if (fromUid != null && fromName != null) {
                    listener.onIncomingInvite(fromUid, fromName);
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

    /**
     * Pozvana strana odgovara na poziv. Prihvatanjem se kreira meč
     * (vidi {@link MatchRepository#createDirectMatch}) i upisuje njegov id u
     * isti čvor poziva; odbijanjem se samo menja status. Pošiljalac (koji
     * sluša isti čvor) je odgovoran da ga obriše nakon što reaguje.
     */
    public void respondToInvite(@NonNull String myUid, @NonNull String myUsername,
                                @NonNull String fromUid, @NonNull String fromUsername,
                                boolean accept, @NonNull RespondCallback cb) {
        if (!accept) {
            inviteRef(myUid).child("status").setValue(STATUS_DECLINED)
                    .addOnSuccessListener(v -> cb.onDeclined())
                    .addOnFailureListener(e -> cb.onError("Slanje odgovora nije uspelo."));
            return;
        }
        String matchId = mRtdb.getReference(Constants.RTDB_MATCHES).push().getKey();
        if (matchId == null) {
            cb.onError("Kreiranje meča nije uspelo.");
            return;
        }
        MatchRepository.getInstance().createDirectMatch(matchId, fromUid, fromUsername, myUid, myUsername,
                new MatchRepository.SimpleCallback() {
                    @Override
                    public void onSuccess() {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("status", STATUS_ACCEPTED);
                        updates.put("matchId", matchId);
                        inviteRef(myUid).updateChildren(updates)
                                .addOnSuccessListener(v -> cb.onAccepted(matchId))
                                .addOnFailureListener(e -> cb.onError("Slanje odgovora nije uspelo."));
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        cb.onError(message);
                    }
                });
    }

    /** Čisti čvor poziva nakon što je pošiljalac reagovao na prihvatanje/odbijanje. */
    public void clearInvite(@NonNull String friendUid) {
        inviteRef(friendUid).removeValue();
    }

    // -------------------------------------------------------------------------
    // Pomoćne reference
    // -------------------------------------------------------------------------

    private com.google.firebase.firestore.CollectionReference friendsRef(@NonNull String uid) {
        return mDb.collection(Constants.COLLECTION_USERS).document(uid)
                .collection(Constants.COLLECTION_FRIENDS);
    }

    private DatabaseReference inviteRef(@NonNull String targetUid) {
        return mRtdb.getReference(Constants.RTDB_FRIEND_INVITES).child(targetUid);
    }
}

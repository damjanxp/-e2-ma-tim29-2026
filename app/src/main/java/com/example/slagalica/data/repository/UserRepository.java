package com.example.slagalica.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.data.model.User;
import com.example.slagalica.logic.league.LeagueLogic;
import com.example.slagalica.logic.match.MatchRewardCalculator;
import com.example.slagalica.util.AvatarProvider;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

/**
 * Singleton repozitorijum koji upravlja svim operacijama vezanim za
 * autentifikaciju i korisničke podatke.
 *
 * <p>Koristi Firebase Auth za autentifikaciju i Firestore kolekciju
 * "users" za profile korisnika.</p>
 *
 * <p>Sve metode su asinhrone i komuniciraju s UI slojem isključivo
 * putem callback interfejsa — nikad ne vraćaju {@code Task<>} direktno.</p>
 */
public class UserRepository {

    private static final String COLLECTION_USERS = "users";

    /** Broj milisekundi u 24 sata — prag za dnevnu dodelu žetona. */
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    /** Interni marker greške u transakciji kada igrač nema žetona. */
    private static final String NO_TOKENS_MARKER = "NO_TOKENS";

    /** Interni marker greške u transakciji kada igrač nema dovoljno uloga za izazov. */
    private static final String NO_STAKE_MARKER = "NO_STAKE";

    // -------------------------------------------------------------------------
    // Singleton
    // -------------------------------------------------------------------------

    private static UserRepository instance;

    private final FirebaseAuth mAuth;
    private final FirebaseFirestore mDb;

    private UserRepository() {
        mAuth = FirebaseAuth.getInstance();
        mDb = FirebaseFirestore.getInstance();
    }

    /**
     * Vraća jedinu instancu {@link UserRepository}.
     *
     * @return singleton instanca
     */
    public static synchronized UserRepository getInstance() {
        if (instance == null) {
            instance = new UserRepository();
        }
        return instance;
    }

    // -------------------------------------------------------------------------
    // Callback interfejsi
    // -------------------------------------------------------------------------

    /** Callback za operacije koje vraćaju {@link FirebaseUser} pri uspehu. */
    public interface AuthCallback {
        void onSuccess(FirebaseUser user);
        void onError(String message);
    }

    /** Callback za operacije koje vraćaju {@link User} POJO pri uspehu. */
    public interface UserCallback {
        void onSuccess(User user);
        void onError(String message);
    }

    /** Callback bez povratne vrednosti (Student 2 — profil/igre). */
    public interface SimpleCallback {
        void onSuccess();
        void onError(String message);
    }

    // -------------------------------------------------------------------------
    // Registracija
    // -------------------------------------------------------------------------

    /**
     * Registruje novog korisnika:
     * <ol>
     *   <li>Proveri jedinstvenost korisničkog imena u Firestore-u.</li>
     *   <li>Kreira Firebase Auth nalog.</li>
     *   <li>Šalje email verifikaciju.</li>
     *   <li>Upisuje {@link User} dokument u Firestore.</li>
     * </ol>
     *
     * @param email    email adresa
     * @param username željeno korisničko ime (mora biti jedinstveno)
     * @param region   izabrani region
     * @param password lozinka
     * @param cb       callback sa rezultatom operacije
     */
    public void register(@NonNull String email,
                         @NonNull String username,
                         @NonNull String region,
                         @NonNull String password,
                         @NonNull AuthCallback cb) {

        // Korak 1: proveri jedinstvenost korisničkog imena
        mDb.collection(COLLECTION_USERS)
                .whereEqualTo("username", username)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        cb.onError("Korisničko ime je već zauzeto.");
                        return;
                    }
                    // Korak 2: kreiraj Firebase Auth nalog
                    createAuthUser(email, username, region, password, cb);
                })
                .addOnFailureListener(e ->
                        cb.onError("Greška pri proveri korisničkog imena. Pokušaj ponovo."));
    }

    /** Pomoćna metoda — kreira Auth nalog nakon provere korisničkog imena. */
    private void createAuthUser(String email, String username, String region,
                                String password, AuthCallback cb) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser == null) {
                        cb.onError("Greška pri kreaciji naloga. Pokušaj ponovo.");
                        return;
                    }

                    // Korak 3: pošalji email verifikaciju
                    firebaseUser.sendEmailVerification()
                            .addOnFailureListener(e -> {
                                // Ne blokiraj registraciju ako verifikacija ne uspe
                            });

                    // Korak 4: upiši User profil u Firestore
                    saveUserToFirestore(firebaseUser, email, username, region, cb);
                })
                .addOnFailureListener(e -> cb.onError(mapAuthError(e)));
    }

    /** Pomoćna metoda — upisuje novi {@link User} dokument u Firestore. */
    private void saveUserToFirestore(FirebaseUser firebaseUser, String email,
                                     String username, String region, AuthCallback cb) {
        long now = System.currentTimeMillis();
        User user = new User(
                firebaseUser.getUid(),
                email,
                username,
                region,
                null,   // avatarUrl — null, koristi se lokalni placeholder
                5,      // tokens
                0,      // totalStars
                0,      // currentLeague
                now,    // lastTokenGrantTimestamp
                now,    // createdAt
                0       // avatarFrameType
        );

        mDb.collection(COLLECTION_USERS)
                .document(firebaseUser.getUid())
                .set(user)
                .addOnSuccessListener(unused -> cb.onSuccess(firebaseUser))
                .addOnFailureListener(e -> cb.onError("Profil nije mogao biti sačuvan. Pokušaj ponovo."));
    }

    // -------------------------------------------------------------------------
    // Prijava
    // -------------------------------------------------------------------------

    /**
     * Prijavljuje korisnika putem emaila ili korisničkog imena.
     *
     * <p>Ako {@code emailOrUsername} sadrži "@", tretira se kao email.
     * U suprotnom, email se traži u Firestore-u po korisničkom imenu.</p>
     *
     * <p>Nakon uspešne prijave, proverava se verifikacija emaila.
     * Ako email nije verifikovan, korisnik se odmah odjavljuje.</p>
     *
     * @param emailOrUsername email adresa ili korisničko ime
     * @param password        lozinka
     * @param cb              callback sa rezultatom operacije
     */
    public void login(@NonNull String emailOrUsername,
                      @NonNull String password,
                      @NonNull AuthCallback cb) {

        if (emailOrUsername.contains("@")) {
            // Direktna prijava putem emaila
            signInWithEmail(emailOrUsername, password, cb);
        } else {
            // Pronađi email po korisničkom imenu
            mDb.collection(COLLECTION_USERS)
                    .whereEqualTo("username", emailOrUsername)
                    .limit(1)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        if (querySnapshot.isEmpty()) {
                            cb.onError("Korisničko ime ne postoji.");
                            return;
                        }
                        QueryDocumentSnapshot doc =
                                (QueryDocumentSnapshot) querySnapshot.getDocuments().get(0);
                        String email = doc.getString("email");
                        if (email == null || email.isEmpty()) {
                            cb.onError("Greška pri učitavanju naloga. Pokušaj ponovo.");
                            return;
                        }
                        signInWithEmail(email, password, cb);
                    })
                    .addOnFailureListener(e ->
                            cb.onError("Greška pri pretrazi korisnika. Pokušaj ponovo."));
        }
    }

    /** Pomoćna metoda — prijava putem emaila i lozinke uz proveru verifikacije. */
    private void signInWithEmail(String email, String password, AuthCallback cb) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();
                    if (user == null) {
                        cb.onError("Greška pri prijavi. Pokušaj ponovo.");
                        return;
                    }
                    if (!user.isEmailVerified()) {
                        mAuth.signOut();
                        cb.onError("Mejl adresa nije verifikovana. Proveri svoju poštu i klikni na link za potvrdu.");
                        return;
                    }
                    cb.onSuccess(user);
                })
                .addOnFailureListener(e -> cb.onError(mapAuthError(e)));
    }

    // -------------------------------------------------------------------------
    // Promena lozinke
    // -------------------------------------------------------------------------

    /**
     * Menja lozinku trenutno ulogovanog korisnika.
     *
     * <p>Koristi reauthentikaciju pre promene lozinke (Firebase zahtev za
     * sigurnosno-osetljive operacije).</p>
     *
     * @param oldPassword stara lozinka (za reauthentikaciju)
     * @param newPassword nova lozinka
     * @param cb          callback sa rezultatom operacije
     */
    public void changePassword(@NonNull String oldPassword,
                               @NonNull String newPassword,
                               @NonNull AuthCallback cb) {

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            cb.onError("Nisi prijavljen.");
            return;
        }

        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), oldPassword);

        user.reauthenticate(credential)
                .addOnSuccessListener(unused ->
                        user.updatePassword(newPassword)
                                .addOnSuccessListener(v -> cb.onSuccess(user))
                                .addOnFailureListener(e -> cb.onError(mapAuthError(e))))
                .addOnFailureListener(e -> cb.onError("Trenutna lozinka je netačna."));
    }

    // -------------------------------------------------------------------------
    // Čitanje podataka korisnika
    // -------------------------------------------------------------------------

    /**
     * Učitava {@link User} profil trenutno ulogovanog korisnika iz Firestore-a.
     *
     * @param cb callback sa {@link User} objektom pri uspehu
     */
    public void getCurrentUserData(@NonNull UserCallback cb) {
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null) {
            cb.onError("Nisi prijavljen.");
            return;
        }

        mDb.collection(COLLECTION_USERS)
                .document(firebaseUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        cb.onError("Korisnički profil nije pronađen.");
                        return;
                    }
                    User user = documentSnapshot.toObject(User.class);
                    if (user == null) {
                        cb.onError("Greška pri čitanju korisničkog profila.");
                        return;
                    }
                    cb.onSuccess(user);
                })
                .addOnFailureListener(e ->
                        cb.onError("Greška pri učitavanju profila. Proveri internet vezu."));
    }

    // -------------------------------------------------------------------------
    // Odjava
    // -------------------------------------------------------------------------

    /**
     * Odjavljuje trenutno ulogovanog korisnika iz Firebase Auth.
     */
    public void logout() {
        mAuth.signOut();
    }

    // -------------------------------------------------------------------------
    // Pomoćne metode
    // -------------------------------------------------------------------------

    /**
     * Vraća {@link FirebaseUser} trenutno ulogovanog korisnika, ili {@code null}
     * ako niko nije prijavljen.
     *
     * @return trenutni Firebase korisnik ili null
     */
    @Nullable
    public FirebaseUser getCurrentFirebaseUser() {
        return mAuth.getCurrentUser();
    }

    /**
     * Proverava da li je korisnik prijavljen.
     *
     * @return true ako postoji aktivna sesija
     */
    public boolean isLoggedIn() {
        return mAuth.getCurrentUser() != null;
    }

    /**
     * Mapira Firebase Auth izuzetke na srpske poruke razumljive korisniku.
     *
     * @param e izuzetak koji je Firebase Auth bacio
     * @return poruka na srpskom jeziku
     */
    private String mapAuthError(@NonNull Exception e) {
        if (e instanceof FirebaseAuthWeakPasswordException) {
            return "Lozinka mora imati najmanje 6 karaktera.";
        } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("password")) {
                return "Pogrešna lozinka.";
            } else if (msg.contains("email")) {
                return "Email adresa nije ispravnog formata.";
            }
            return "Pogrešni podaci za prijavu.";
        } else if (e instanceof FirebaseAuthUserCollisionException) {
            return "Nalog sa ovom email adresom već postoji.";
        } else if (e instanceof FirebaseAuthInvalidUserException) {
            String errorCode = ((FirebaseAuthInvalidUserException) e).getErrorCode();
            if ("ERROR_USER_NOT_FOUND".equals(errorCode)) {
                return "Nalog ne postoji.";
            } else if ("ERROR_USER_DISABLED".equals(errorCode)) {
                return "Nalog je onemogućen. Kontaktiraj podršku.";
            }
            return "Korisnički nalog nije pronađen.";
        }
        return "Došlo je do greške. Pokušaj ponovo.";
    }

    // =========================================================================
    // Profil, avatar i statistika (Student 2 — Prikaz profila + igre)
    // =========================================================================

    /** Vraća UID trenutno prijavljenog korisnika ili {@code null}. */
    @Nullable
    public String getCurrentUid() {
        FirebaseUser user = mAuth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    /**
     * Obezbeđuje da postoji prijavljen Firebase korisnik. Ako niko nije prijavljen
     * (npr. neregistrovan igrač koji po specifikaciji sme da igra protiv drugog),
     * prijavljuje anonimnog korisnika.
     */
    public void ensureSignedIn(@NonNull SimpleCallback cb) {
        if (mAuth.getCurrentUser() != null) {
            cb.onSuccess();
            return;
        }
        mAuth.signInAnonymously()
                .addOnSuccessListener(result -> cb.onSuccess())
                .addOnFailureListener(e ->
                        cb.onError("Prijava nije uspela. Proveri internet konekciju."));
    }

    /**
     * Učitava profil korisnika; ako dokument ne postoji (npr. anonimni igrač),
     * kreira ga sa podrazumevanim vrednostima koristeći timsku šemu.
     */
    public void getOrCreateUser(@NonNull String uid, @NonNull UserCallback cb) {
        mDb.collection(COLLECTION_USERS).document(uid).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        User user = snapshot.toObject(User.class);
                        if (user != null) {
                            cb.onSuccess(user);
                        } else {
                            cb.onError("Greška pri čitanju korisničkog profila.");
                        }
                        return;
                    }
                    // Dokument ne postoji — kreiraj podrazumevani (anonimni/gost)
                    FirebaseUser fu = mAuth.getCurrentUser();
                    String email = fu != null && fu.getEmail() != null ? fu.getEmail() : "";
                    String username = !email.isEmpty()
                            ? email.substring(0, email.indexOf('@'))
                            : "Igrac-" + uid.substring(0, Math.min(4, uid.length()));
                    long now = System.currentTimeMillis();
                    User newUser = new User(uid, email, username, "Beograd",
                            null, 5, 0, 0, now, now, 0);
                    mDb.collection(COLLECTION_USERS).document(uid).set(newUser)
                            .addOnSuccessListener(v -> cb.onSuccess(newUser))
                            .addOnFailureListener(e -> cb.onError("Kreiranje profila nije uspelo."));
                })
                .addOnFailureListener(e ->
                        cb.onError("Učitavanje profila nije uspelo. Proveri internet konekciju."));
    }

    /**
     * Učitava profil korisnika po UID-u bez kreiranja — za slučajeve gde
     * nepostojeći korisnik treba da bude greška, ne novi (mock) nalog (npr.
     * dodavanje prijatelja skeniranjem QR koda ili pretragom po imenu).
     *
     * @param uid UID igrača koji se traži
     * @param cb  callback: {@link User} pri uspehu, greška ako ne postoji
     */
    public void getUserIfExists(@NonNull String uid, @NonNull UserCallback cb) {
        mDb.collection(COLLECTION_USERS).document(uid).get()
                .addOnSuccessListener(snap -> {
                    if (!snap.exists()) {
                        cb.onError("Korisnik nije pronađen.");
                        return;
                    }
                    User user = snap.toObject(User.class);
                    if (user == null) {
                        cb.onError("Greška pri čitanju korisničkog profila.");
                        return;
                    }
                    cb.onSuccess(user);
                })
                .addOnFailureListener(e ->
                        cb.onError("Učitavanje korisnika nije uspelo. Proveri internet konekciju."));
    }

    /**
     * Menja avatar korisnika. Lokalni avatar (indeks) se čuva kao string u polju
     * {@code avatarUrl} (bez Firebase Storage, po AGENTS.md).
     */
    public void updateAvatar(@NonNull String uid, int avatarIndex, @NonNull SimpleCallback cb) {
        mDb.collection(COLLECTION_USERS).document(uid)
                .update("avatarUrl", AvatarProvider.storedValue(avatarIndex))
                .addOnSuccessListener(v -> cb.onSuccess())
                .addOnFailureListener(e -> cb.onError("Izmena avatara nije uspela."));
    }

    /** Beleži rezultat jedne igre "Ko zna zna" u statistiku korisnika. */
    public void recordKzzResult(@NonNull String uid, int correct, int wrong, int points) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("kzzCorrect", FieldValue.increment(correct));
        updates.put("kzzWrong", FieldValue.increment(wrong));
        updates.put("kzzPointsSum", FieldValue.increment(points));
        updates.put("kzzGames", FieldValue.increment(1));
        mDb.collection(COLLECTION_USERS).document(uid).update(updates);
    }

    /** Beleži rezultat jedne igre "Spojnice" u statistiku korisnika. */
    public void recordSpojniceResult(@NonNull String uid, int connected, int missed, int points) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("spojniceConnected", FieldValue.increment(connected));
        updates.put("spojniceMissed", FieldValue.increment(missed));
        updates.put("spojnicePointsSum", FieldValue.increment(points));
        updates.put("spojniceGames", FieldValue.increment(1));
        mDb.collection(COLLECTION_USERS).document(uid).update(updates);
    }

    /** Beleži odigranu partiju (i pobedu) u statistiku korisnika. */
    public void recordMatchResult(@NonNull String uid, boolean won) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("matchesPlayed", FieldValue.increment(1));
        if (won) {
            updates.put("matchesWon", FieldValue.increment(1));
        }
        mDb.collection(COLLECTION_USERS).document(uid).update(updates);
    }

    // =========================================================================
    // Žetoni i nagrade za partiju (Student 1 — 3. Igranje partija)
    // =========================================================================

    /**
     * Skida 1 žeton kao ulaz u rankiranu partiju, atomično kroz Firestore
     * transakciju (sprečava trošenje ispod nule pri istovremenim partijama).
     *
     * <p>Ako igrač nema žetona, vraća {@link SimpleCallback#onError(String)} sa
     * porukom o nedostatku žetona. Prijateljske partije <b>ne</b> pozivaju ovu
     * metodu (ne troše žetone).</p>
     *
     * @param uid UID igrača
     * @param cb  callback: uspeh kad je žeton skinut, greška inače
     */
    public void consumeTokenForMatch(@NonNull String uid, @NonNull SimpleCallback cb) {
        DocumentReference ref = mDb.collection(COLLECTION_USERS).document(uid);
        mDb.runTransaction(transaction -> {
            DocumentSnapshot snap = transaction.get(ref);
            Long tokens = snap.getLong("tokens");
            long current = tokens != null ? tokens : 0;
            if (current <= 0) {
                throw new FirebaseFirestoreException(NO_TOKENS_MARKER,
                        FirebaseFirestoreException.Code.ABORTED);
            }
            transaction.update(ref, "tokens", current - 1);
            return null;
        }).addOnSuccessListener(v -> cb.onSuccess())
          .addOnFailureListener(e -> {
              if (e.getMessage() != null && e.getMessage().contains(NO_TOKENS_MARKER)) {
                  cb.onError("Nemaš dovoljno žetona za partiju.");
              } else {
                  cb.onError("Trošenje žetona nije uspelo. Proveri internet konekciju.");
              }
          });
    }

    /**
     * Dodeljuje dnevne žetone ako je od poslednje dodele prošlo najmanje 24 sata;
     * u suprotnom ne menja ništa.
     *
     * <p>Broj dodeljenih žetona zavisi od trenutne lige igrača — svaka liga
     * donosi po dodatni token (vidi {@link LeagueLogic#dailyTokensForLeague(int)}),
     * npr. treća liga daje 5 + 3 = 8 žetona dnevno.</p>
     *
     * <p>Poziva se tiho pri ulasku na glavni ekran; uspeh se vraća i kada nije
     * bilo dodele (nije istekao dan).</p>
     *
     * @param uid UID igrača
     * @param cb  callback (uspeh i kada nema promene)
     */
    public void grantDailyTokensIfDue(@NonNull String uid, @NonNull SimpleCallback cb) {
        DocumentReference ref = mDb.collection(COLLECTION_USERS).document(uid);
        ref.get().addOnSuccessListener(snap -> {
            if (!snap.exists()) {
                cb.onSuccess();
                return;
            }
            Long last = snap.getLong("lastTokenGrantTimestamp");
            long lastTs = last != null ? last : 0;
            long now = System.currentTimeMillis();
            if (now - lastTs < DAY_MS) {
                cb.onSuccess();
                return;
            }
            Long leagueField = snap.getLong("currentLeague");
            int league = leagueField != null ? leagueField.intValue() : 0;
            int dailyTokens = LeagueLogic.dailyTokensForLeague(league);

            Map<String, Object> updates = new HashMap<>();
            updates.put("tokens", FieldValue.increment(dailyTokens));
            updates.put("lastTokenGrantTimestamp", now);
            ref.update(updates)
                    .addOnSuccessListener(v -> cb.onSuccess())
                    .addOnFailureListener(e -> cb.onError("Dodela dnevnih žetona nije uspela."));
        }).addOnFailureListener(e ->
                cb.onError("Učitavanje profila nije uspelo. Proveri internet konekciju."));
    }

    /**
     * Primenjuje nagrade nakon završene <b>rankirane</b> partije po pravilima
     * specifikacije (vidi {@link MatchRewardCalculator}):
     * <ul>
     *   <li>izračuna novu vrednost zvezda (uz donju granicu na nuli),</li>
     *   <li>izračuna nove žetone jer je pređen prag od 50 zvezda,</li>
     *   <li>upiše {@code totalStars} i uveća {@code tokens} u Firestore,</li>
     *   <li>preračuna ligu na osnovu novog broja zvezda (vidi {@link LeagueLogic})
     *       i upiše je ako se promenila.</li>
     * </ul>
     *
     * <p>Vraćeni {@link User} ima ažurnu {@code currentLeague} — pozivalac (UI)
     * upoređuje je sa ligom pre partije da bi prikazao obaveštenje o promeni lige.</p>
     *
     * <p><b>Ne</b> poziva se za prijateljske partije.</p>
     *
     * @param uid      UID igrača
     * @param won      da li je igrač pobedio u partiji
     * @param myPoints ukupan broj bodova igrača u partiji
     * @param cb       callback sa ažuriranim {@link User} objektom pri uspehu
     */
    public void applyMatchRewards(@NonNull String uid, boolean won, int myPoints,
                                  @NonNull UserCallback cb) {
        DocumentReference ref = mDb.collection(COLLECTION_USERS).document(uid);
        ref.get().addOnSuccessListener(snap -> {
            User user = snap.toObject(User.class);
            if (user == null) {
                cb.onError("Greška pri čitanju korisničkog profila.");
                return;
            }
            int oldStars = user.getTotalStars();
            int delta = won
                    ? MatchRewardCalculator.starsForWinner(myPoints)
                    : MatchRewardCalculator.starsDeltaForLoser(myPoints);
            int newStars = MatchRewardCalculator.applyStarFloor(oldStars, delta);
            int newTokens = MatchRewardCalculator.tokensFromStars(oldStars, newStars);
            int newLeague = LeagueLogic.leagueForStars(newStars);
            // Iste zvezde idu i na profil (totalStars) i u tekući nedeljni/mesečni
            // ciklus rang liste (specifikacija 4b) — svako polje floor-ovano na nulu zasebno.
            int newWeeklyStars = MatchRewardCalculator.applyStarFloor(user.getWeeklyStars(), delta);
            int newMonthlyStars = MatchRewardCalculator.applyStarFloor(user.getMonthlyStars(), delta);

            Map<String, Object> updates = new HashMap<>();
            updates.put("totalStars", newStars);
            updates.put("weeklyStars", newWeeklyStars);
            updates.put("monthlyStars", newMonthlyStars);
            if (newTokens > 0) {
                updates.put("tokens", FieldValue.increment(newTokens));
            }
            if (newLeague != user.getCurrentLeague()) {
                updates.put("currentLeague", newLeague);
            }
            ref.update(updates).addOnSuccessListener(v -> {
                user.setTotalStars(newStars);
                user.setWeeklyStars(newWeeklyStars);
                user.setMonthlyStars(newMonthlyStars);
                user.setTokens(user.getTokens() + newTokens);
                user.setCurrentLeague(newLeague);
                cb.onSuccess(user);
            }).addOnFailureListener(e -> cb.onError("Upis nagrada nije uspeo."));
        }).addOnFailureListener(e ->
                cb.onError("Učitavanje profila nije uspelo. Proveri internet konekciju."));
    }

    /** Odjava (alias za {@link #logout()} — koristi ga Student 2 kod profila). */
    public void signOut() {
        mAuth.signOut();
    }

    // =========================================================================
    // Izazov — ulog i nagrade (Student 1 — 9. Izazov)
    // =========================================================================

    /**
     * Skida ulog (zvezde i žetone) za učešće u izazovu, atomično kroz Firestore
     * transakciju (sprečava skidanje ispod nule pri istovremenim izazovima).
     *
     * <p>Ako igrač nema dovoljno zvezda ili žetona, vraća
     * {@link SimpleCallback#onError(String)} sa porukom o nedostatku uloga.</p>
     *
     * @param uid         UID igrača
     * @param stakeStars  broj zvezda koje se skidaju
     * @param stakeTokens broj žetona koji se skidaju
     * @param cb          callback: uspeh kad je ulog skinut, greška inače
     */
    public void deductChallengeStake(@NonNull String uid, int stakeStars, int stakeTokens,
                                     @NonNull SimpleCallback cb) {
        DocumentReference ref = mDb.collection(COLLECTION_USERS).document(uid);
        mDb.runTransaction(transaction -> {
            DocumentSnapshot snap = transaction.get(ref);
            Long stars = snap.getLong("totalStars");
            Long tokens = snap.getLong("tokens");
            long currentStars = stars != null ? stars : 0;
            long currentTokens = tokens != null ? tokens : 0;
            if (currentStars < stakeStars || currentTokens < stakeTokens) {
                throw new FirebaseFirestoreException(NO_STAKE_MARKER,
                        FirebaseFirestoreException.Code.ABORTED);
            }
            long newStars = currentStars - stakeStars;
            transaction.update(ref, "totalStars", newStars);
            transaction.update(ref, "tokens", currentTokens - stakeTokens);
            transaction.update(ref, "currentLeague", LeagueLogic.leagueForStars((int) newStars));

            // Ulog se skida i iz tekućeg nedeljnog/mesečnog ciklusa (iste zvezde, specifikacija 4b).
            Long weeklyStars = snap.getLong("weeklyStars");
            Long monthlyStars = snap.getLong("monthlyStars");
            transaction.update(ref, "weeklyStars",
                    Math.max(0, (weeklyStars != null ? weeklyStars : 0) - stakeStars));
            transaction.update(ref, "monthlyStars",
                    Math.max(0, (monthlyStars != null ? monthlyStars : 0) - stakeStars));
            return null;
        }).addOnSuccessListener(v -> cb.onSuccess())
          .addOnFailureListener(e -> {
              if (e.getMessage() != null && e.getMessage().contains(NO_STAKE_MARKER)) {
                  cb.onError("Nemaš dovoljno zvezda ili žetona za ovaj izazov.");
              } else {
                  cb.onError("Skidanje uloga nije uspelo. Proveri internet konekciju.");
              }
          });
    }

    /**
     * Dodeljuje nagradu (zvezde i/ili žetone) igraču nakon završenog izazova
     * (pobedniku ili vraćanje uloga za drugoplasiranog).
     *
     * <p>Ako se dodeljuju zvezde, preračunava i upisuje ligu na osnovu novog
     * broja zvezda (vidi {@link LeagueLogic}) — otud atomična transakcija
     * umesto prostog {@code increment}-a.</p>
     *
     * @param uid    UID igrača
     * @param stars  broj zvezda za dodelu (0 ako nema)
     * @param tokens broj žetona za dodelu (0 ako nema)
     * @param cb     callback po završetku, može biti {@code null}
     */
    public void creditChallengeReward(@NonNull String uid, int stars, int tokens,
                                      @Nullable SimpleCallback cb) {
        if (stars <= 0 && tokens <= 0) {
            if (cb != null) cb.onSuccess();
            return;
        }
        DocumentReference ref = mDb.collection(COLLECTION_USERS).document(uid);
        mDb.runTransaction(transaction -> {
            DocumentSnapshot snap = transaction.get(ref);
            Long currentStars = snap.getLong("totalStars");
            long newStars = (currentStars != null ? currentStars : 0) + stars;
            if (stars > 0) {
                transaction.update(ref, "totalStars", newStars);
                transaction.update(ref, "currentLeague", LeagueLogic.leagueForStars((int) newStars));
                // Nagrada ide i u tekući nedeljni/mesečni ciklus (iste zvezde, specifikacija 4b).
                transaction.update(ref, "weeklyStars", FieldValue.increment(stars));
                transaction.update(ref, "monthlyStars", FieldValue.increment(stars));
            }
            if (tokens > 0) {
                transaction.update(ref, "tokens", FieldValue.increment(tokens));
            }
            return null;
        }).addOnSuccessListener(v -> { if (cb != null) cb.onSuccess(); })
          .addOnFailureListener(e -> {
              if (cb != null) cb.onError("Dodela nagrade za izazov nije uspela.");
          });
    }
}


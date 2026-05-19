package com.example.slagalica.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.data.model.User;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

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
}


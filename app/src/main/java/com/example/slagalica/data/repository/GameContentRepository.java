package com.example.slagalica.data.repository;

import androidx.annotation.NonNull;

import com.example.slagalica.data.model.KorakPoKorakZadatak;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Singleton repozitorijum za učitavanje sadržaja igara iz Firestore-a.
 *
 * <p><b>Važno:</b> Sve Firestore kolekcije moraju biti popunjene ručno
 * ili putem seed skripte pre prvog korišćenja aplikacije. Kolekcije koje
 * ovaj repozitorijum koristi:</p>
 * <ul>
 *   <li>{@code korakPoKorak} — zadaci za igru "Korak po korak"</li>
 *   <li>{@code koZnaZna} — pitanja za "Ko zna zna" (Student 2)</li>
 * </ul>
 *
 * <p>Svaki dokument mora imati strukturu koja odgovara odgovarajućem POJO modelu.</p>
 */
public class GameContentRepository {

    private static final String COLLECTION_KORAK   = "korakPoKorak";
    private static final String COLLECTION_KO_ZNA  = "koZnaZna";

    // -------------------------------------------------------------------------
    // Singleton
    // -------------------------------------------------------------------------

    private static GameContentRepository instance;

    private final FirebaseFirestore mDb;
    private final Random random;

    private GameContentRepository() {
        mDb = FirebaseFirestore.getInstance();
        random = new Random();
    }

    /**
     * Vraća jedinu instancu {@link GameContentRepository}.
     *
     * @return singleton instanca
     */
    public static synchronized GameContentRepository getInstance() {
        if (instance == null) {
            instance = new GameContentRepository();
        }
        return instance;
    }

    // -------------------------------------------------------------------------
    // Callback interfejsi
    // -------------------------------------------------------------------------

    /** Callback za učitavanje zadatka za igru "Korak po korak". */
    public interface KorakLoadCallback {
        void onSuccess(KorakPoKorakZadatak zadatak);
        void onError(String message);
    }

    /**
     * Generički callback za jednostavan uspeh/greška signal.
     * Koristi se za placeholder metode dok ih studenti ne implementiraju.
     */
    public interface SimpleCallback {
        void onSuccess();
        void onError(String message);
    }

    // -------------------------------------------------------------------------
    // Korak po korak
    // -------------------------------------------------------------------------

    /**
     * Učitava slučajno izabrani zadatak iz Firestore kolekcije {@code korakPoKorak}.
     *
     * <p>Implementacija učitava sve dokumente, pa bira nasumičan.
     * Ovo je prihvatljivo dok je broj zadataka mali (desetine). Za veće
     * kolekcije razmotriti paginaciju ili server-side random selection.</p>
     *
     * <p><b>Preduslov:</b> kolekcija {@code korakPoKorak} mora biti popunjena
     * pre poziva ove metode — aplikacija ne seeduje podatke automatski.</p>
     *
     * @param cb callback koji prima {@link KorakPoKorakZadatak} pri uspehu,
     *           ili poruku greške pri neuspehu
     */
    public void getRandomKorakPoKorak(@NonNull KorakLoadCallback cb) {
        mDb.collection(COLLECTION_KORAK)
                .get()
                .addOnSuccessListener((QuerySnapshot querySnapshot) -> {
                    if (querySnapshot.isEmpty()) {
                        cb.onError("Nema dostupnih zadataka. Kontaktiraj administratora.");
                        return;
                    }

                    List<QueryDocumentSnapshot> docs = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        docs.add(doc);
                    }

                    // Slučajan odabir dokumenta
                    QueryDocumentSnapshot chosen = docs.get(random.nextInt(docs.size()));
                    KorakPoKorakZadatak zadatak = chosen.toObject(KorakPoKorakZadatak.class);

                    if (zadatak == null) {
                        cb.onError("Greška pri čitanju zadatka. Pokušaj ponovo.");
                        return;
                    }

                    // Postavi ID iz document reference (nije serijalizovano automatski)
                    zadatak.setId(chosen.getId());
                    cb.onSuccess(zadatak);
                })
                .addOnFailureListener(e ->
                        cb.onError("Greška pri učitavanju zadatka. Proveri internet vezu."));
    }

    // -------------------------------------------------------------------------
    // Ko zna zna — placeholder (Student 2)
    // -------------------------------------------------------------------------

    /**
     * Učitava slučajno pitanje za igru "Ko zna zna" iz kolekcije {@code koZnaZna}.
     *
     * <p><b>TODO (Student 2):</b> Implementirati ovu metodu kada bude definisan
     * POJO model za pitanja "Ko zna zna". Isti obrazac kao
     * {@link #getRandomKorakPoKorak(KorakLoadCallback)}.</p>
     *
     * @param cb callback — trenutno uvek vraća grešku (nije implementirano)
     */
    public void getRandomKoZnaZna(@NonNull SimpleCallback cb) {
        // TODO (Student 2): implementirati učitavanje pitanja iz kolekcije "koZnaZna"
        // Obrazac:
        // mDb.collection(COLLECTION_KO_ZNA).get()
        //     .addOnSuccessListener(snapshot -> { ... cb.onSuccess(); })
        //     .addOnFailureListener(e -> cb.onError(...));
        cb.onError("getRandomKoZnaZna nije još implementirano.");
    }

    // -------------------------------------------------------------------------
    // Moj broj — placeholder
    // -------------------------------------------------------------------------

    /**
     * Učitava preset rundu za igru "Moj broj" (opciono — igra generiše brojeve
     * i lokalno, pa ovo može ostati nepopunjeno).
     *
     * <p><b>TODO:</b> Implementirati ako se žele predefinisane runde sa serverski
     * generisanim brojevima umesto nasumičnih lokalnih.</p>
     *
     * @param cb callback — trenutno uvek vraća grešku (nije implementirano)
     */
    public void getRandomMojBroj(@NonNull SimpleCallback cb) {
        // TODO: implementirati ako se koriste server-side preset runde za "Moj broj"
        // Za KT2 igra generiše brojeve lokalno u MojBrojActivity.generisi6Brojeva()
        cb.onError("getRandomMojBroj nije implementirano — koristi se lokalna generacija.");
    }
}


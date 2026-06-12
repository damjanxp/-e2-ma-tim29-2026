package com.example.slagalica.data.repository;

import androidx.annotation.NonNull;

import com.example.slagalica.data.model.KorakPoKorakZadatak;
import com.example.slagalica.data.model.KzzQuestion;
import com.example.slagalica.data.model.SpojnicePuzzle;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

    private static final String COLLECTION_KORAK    = "korakPoKorak";
    private static final String COLLECTION_KO_ZNA   = "koZnaZna";
    private static final String COLLECTION_SPOJNICE = "spojnice";

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

    // =========================================================================
    // Ko zna zna i Spojnice — sadržaj za 1v1 partiju (Student 2)
    // Ako je kolekcija prazna, popuni je primerima (specifikacija dozvoljava
    // unos par primera skriptom; ovde je to "seed" pri prvom pokretanju).
    // =========================================================================

    /** Callback za listu pitanja "Ko zna zna". */
    public interface KzzCallback {
        void onSuccess(@NonNull List<KzzQuestion> questions);
        void onError(@NonNull String message);
    }

    /** Callback za listu slagalica "Spojnice". */
    public interface SpojniceCallback {
        void onSuccess(@NonNull List<SpojnicePuzzle> puzzles);
        void onError(@NonNull String message);
    }

    /** Učitava {@code count} nasumičnih pitanja za "Ko zna zna". */
    public void loadKzzQuestions(int count, @NonNull KzzCallback cb) {
        mDb.collection(COLLECTION_KO_ZNA).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        seedKzz(() -> loadKzzQuestions(count, cb), cb);
                        return;
                    }
                    List<KzzQuestion> all = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        KzzQuestion q = doc.toObject(KzzQuestion.class);
                        if (q.getText() != null && q.getAnswers() != null && q.getAnswers().size() == 4) {
                            all.add(q);
                        }
                    }
                    Collections.shuffle(all);
                    cb.onSuccess(new ArrayList<>(all.subList(0, Math.min(count, all.size()))));
                })
                .addOnFailureListener(e -> cb.onError("Učitavanje pitanja nije uspelo."));
    }

    /** Učitava {@code count} nasumičnih slagalica za "Spojnice". */
    public void loadSpojnicePuzzles(int count, @NonNull SpojniceCallback cb) {
        mDb.collection(COLLECTION_SPOJNICE).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        seedSpojnice(() -> loadSpojnicePuzzles(count, cb), cb);
                        return;
                    }
                    List<SpojnicePuzzle> all = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        SpojnicePuzzle p = doc.toObject(SpojnicePuzzle.class);
                        if (p.getCriterion() != null
                                && p.getLeftItems().size() == 5
                                && p.getRightItems().size() == 5
                                && p.getCorrectMap().size() == 5) {
                            all.add(p);
                        }
                    }
                    Collections.shuffle(all);
                    cb.onSuccess(new ArrayList<>(all.subList(0, Math.min(count, all.size()))));
                })
                .addOnFailureListener(e -> cb.onError("Učitavanje spojnica nije uspelo."));
    }

    private void seedKzz(Runnable onSeeded, KzzCallback cb) {
        WriteBatch batch = mDb.batch();
        for (KzzQuestion q : sampleQuestions()) {
            batch.set(mDb.collection(COLLECTION_KO_ZNA).document(), q);
        }
        batch.commit()
                .addOnSuccessListener(v -> onSeeded.run())
                .addOnFailureListener(e -> cb.onError("Inicijalni unos pitanja nije uspeo."));
    }

    private void seedSpojnice(Runnable onSeeded, SpojniceCallback cb) {
        WriteBatch batch = mDb.batch();
        for (SpojnicePuzzle p : samplePuzzles()) {
            batch.set(mDb.collection(COLLECTION_SPOJNICE).document(), p);
        }
        batch.commit()
                .addOnSuccessListener(v -> onSeeded.run())
                .addOnFailureListener(e -> cb.onError("Inicijalni unos spojnica nije uspeo."));
    }

    private List<KzzQuestion> sampleQuestions() {
        List<KzzQuestion> list = new ArrayList<>();
        list.add(new KzzQuestion("Koji grad je prestonica Francuske?",
                Arrays.asList("Berlin", "Madrid", "Pariz", "Rim"), 2));
        list.add(new KzzQuestion("Koliko planeta ima Sunčev sistem?",
                Arrays.asList("7", "8", "9", "10"), 1));
        list.add(new KzzQuestion("Ko je napisao \"Romeo i Julija\"?",
                Arrays.asList("Dikens", "Tolstoj", "Šekspir", "Homer"), 2));
        list.add(new KzzQuestion("Koji element ima hemijski simbol O?",
                Arrays.asList("Zlato", "Kiseonik", "Srebro", "Vodonik"), 1));
        list.add(new KzzQuestion("Koja je najveća planeta Sunčevog sistema?",
                Arrays.asList("Saturn", "Zemlja", "Mars", "Jupiter"), 3));
        list.add(new KzzQuestion("Koja reka protiče kroz Beograd?",
                Arrays.asList("Drina", "Morava", "Dunav", "Tisa"), 2));
        list.add(new KzzQuestion("Koliko sekundi ima jedan sat?",
                Arrays.asList("360", "600", "3600", "6000"), 2));
        list.add(new KzzQuestion("Ko je naslikao Mona Lizu?",
                Arrays.asList("Mikelanđelo", "Da Vinči", "Pikaso", "Van Gog"), 1));
        list.add(new KzzQuestion("Koji je najviši vrh Srbije?",
                Arrays.asList("Midžor", "Đeravica", "Kopaonik", "Rtanj"), 0));
        list.add(new KzzQuestion("Koje godine je počeo Prvi svetski rat?",
                Arrays.asList("1912", "1914", "1918", "1939"), 1));
        list.add(new KzzQuestion("Koja država ima najviše stanovnika?",
                Arrays.asList("SAD", "Indija", "Kina", "Rusija"), 1));
        list.add(new KzzQuestion("Koliko igrača ima fudbalski tim na terenu?",
                Arrays.asList("9", "10", "11", "12"), 2));
        return list;
    }

    private List<SpojnicePuzzle> samplePuzzles() {
        List<SpojnicePuzzle> list = new ArrayList<>();
        list.add(new SpojnicePuzzle("Povežite države sa prestonicama",
                Arrays.asList("Srbija", "Francuska", "Japan", "Brazil", "Australija"),
                Arrays.asList("Pariz", "Beograd", "Brazilija", "Kanbera", "Tokio"),
                Arrays.asList(1L, 0L, 4L, 2L, 3L)));
        list.add(new SpojnicePuzzle("Povežite životinje sa zvukovima",
                Arrays.asList("Pas", "Mačka", "Krava", "Ptica", "Žaba"),
                Arrays.asList("Cvrkuće", "Reži", "Muče", "Kreče", "Mijauče"),
                Arrays.asList(1L, 4L, 2L, 0L, 3L)));
        list.add(new SpojnicePuzzle("Povežite pisce sa delima",
                Arrays.asList("Andrić", "Njegoš", "Nušić", "Servantes", "Orvel"),
                Arrays.asList("Gorski vijenac", "Na Drini ćuprija", "1984", "Don Kihot", "Sumnjivo lice"),
                Arrays.asList(1L, 0L, 4L, 3L, 2L)));
        list.add(new SpojnicePuzzle("Povežite sportove sa terenima",
                Arrays.asList("Tenis", "Plivanje", "Boks", "Skijanje", "Hokej"),
                Arrays.asList("Ring", "Staza", "Teren", "Led", "Bazen"),
                Arrays.asList(2L, 4L, 0L, 1L, 3L)));
        list.add(new SpojnicePuzzle("Povežite valute sa državama",
                Arrays.asList("Dinar", "Jen", "Funta", "Rublja", "Forinta"),
                Arrays.asList("Japan", "Mađarska", "Srbija", "Velika Britanija", "Rusija"),
                Arrays.asList(2L, 0L, 3L, 4L, 1L)));
        return list;
    }
}


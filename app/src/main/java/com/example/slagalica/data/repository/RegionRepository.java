package com.example.slagalica.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.data.model.RegionCycleResult;
import com.example.slagalica.data.model.User;
import com.example.slagalica.util.Constants;
import com.example.slagalica.util.RegionKey;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Singleton repozitorijum za prikaz regiona ("5. Prikaz regiona").
 *
 * <p>Podaci o pripadnosti regionu i zvezdama tekućeg mesečnog ciklusa čitaju se
 * direktno iz Firestore kolekcije "users" ({@link User#getRegion()},
 * {@link User#getMonthlyStars()}) — nema posebne kolekcije za članstvo u
 * regionu. Istorijski broj osvojenih 1./2./3. mesta po regionu čuva se u
 * kolekciji "regions" (vidi {@link Constants#COLLECTION_REGIONS}), a ažurira
 * ga {@link LeaderboardRepository#concludeCycle} pri zaključivanju mesečnog
 * ciklusa.</p>
 */
public class RegionRepository {

    private static RegionRepository instance;

    private final FirebaseFirestore mDb;

    private RegionRepository() {
        mDb = FirebaseFirestore.getInstance();
    }

    @NonNull
    public static synchronized RegionRepository getInstance() {
        if (instance == null) {
            instance = new RegionRepository();
        }
        return instance;
    }

    // -------------------------------------------------------------------------
    // Rezultati / DTO klase
    // -------------------------------------------------------------------------

    /** Jedan red mesečne rang liste po regionu — naziv regiona i zbir zvezda igrača u njemu. */
    public static final class RegionRanking {
        private final String regionName;
        private final int totalStars;
        private final int playerCount;

        public RegionRanking(String regionName, int totalStars, int playerCount) {
            this.regionName = regionName;
            this.totalStars = totalStars;
            this.playerCount = playerCount;
        }

        public String getRegionName() { return regionName; }
        public int getTotalStars() { return totalStars; }
        public int getPlayerCount() { return playerCount; }
    }

    /** Statistika jednog regiona za dijalog otvoren klikom na region. */
    public static final class RegionStats {
        private final int firstPlaceCount;
        private final int secondPlaceCount;
        private final int thirdPlaceCount;
        private final int activePlayers;
        private final int totalRegistered;

        public RegionStats(int firstPlaceCount, int secondPlaceCount, int thirdPlaceCount,
                            int activePlayers, int totalRegistered) {
            this.firstPlaceCount = firstPlaceCount;
            this.secondPlaceCount = secondPlaceCount;
            this.thirdPlaceCount = thirdPlaceCount;
            this.activePlayers = activePlayers;
            this.totalRegistered = totalRegistered;
        }

        public int getFirstPlaceCount() { return firstPlaceCount; }
        public int getSecondPlaceCount() { return secondPlaceCount; }
        public int getThirdPlaceCount() { return thirdPlaceCount; }
        public int getActivePlayers() { return activePlayers; }
        public int getTotalRegistered() { return totalRegistered; }
    }

    // -------------------------------------------------------------------------
    // Callback interfejsi
    // -------------------------------------------------------------------------

    public interface RegionLeaderboardCallback {
        void onSuccess(@NonNull List<RegionRanking> ranking, @Nullable String myRegion);
        void onError(@NonNull String message);
    }

    public interface RegionStatsCallback {
        void onSuccess(@NonNull RegionStats stats);
        void onError(@NonNull String message);
    }

    public interface RegionPlayersCallback {
        void onSuccess(@NonNull List<User> players);
        void onError(@NonNull String message);
    }

    public interface RegionCycleResultCallback {
        void onSuccess(@Nullable RegionCycleResult result);
        void onError(@NonNull String message);
    }

    // -------------------------------------------------------------------------
    // Igrači po regionu (za nasumične tačke na mapi)
    // -------------------------------------------------------------------------

    /** Vraća sve registrovane igrače u regionu {@code regionName} (za tačke na mapi). */
    public void getPlayersInRegion(@NonNull String regionName, @NonNull RegionPlayersCallback cb) {
        mDb.collection(Constants.COLLECTION_USERS)
                .whereEqualTo("region", regionName)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<User> players = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        User u = doc.toObject(User.class);
                        if (u != null) players.add(u);
                    }
                    cb.onSuccess(players);
                })
                .addOnFailureListener(e ->
                        cb.onError("Učitavanje igrača regiona nije uspelo. Proveri internet konekciju."));
    }

    // -------------------------------------------------------------------------
    // Mesečna rang lista po regionu
    // -------------------------------------------------------------------------

    /**
     * Računa mesečnu rang listu regiona — zbir {@code monthlyStars} svih igrača
     * grupisan po regionu, sortirano opadajuće.
     *
     * @param myUid uid trenutnog igrača (da bi se odredio {@code myRegion} za isticanje reda)
     */
    public void getMonthlyRegionLeaderboard(@Nullable String myUid, @NonNull RegionLeaderboardCallback cb) {
        mDb.collection(Constants.COLLECTION_USERS)
                .get()
                .addOnSuccessListener(snapshot -> {
                    Map<String, Integer> starsByRegion = new HashMap<>();
                    Map<String, Integer> countByRegion = new HashMap<>();
                    String myRegion = null;

                    for (QueryDocumentSnapshot doc : snapshot) {
                        String region = doc.getString("region");
                        if (region == null || region.isEmpty()) continue;
                        Long stars = doc.getLong("monthlyStars");
                        int add = stars != null ? stars.intValue() : 0;
                        starsByRegion.merge(region, add, Integer::sum);
                        countByRegion.merge(region, 1, Integer::sum);
                        if (myUid != null && myUid.equals(doc.getId())) {
                            myRegion = region;
                        }
                    }

                    List<RegionRanking> ranking = new ArrayList<>();
                    for (Map.Entry<String, Integer> entry : starsByRegion.entrySet()) {
                        int count = countByRegion.getOrDefault(entry.getKey(), 0);
                        ranking.add(new RegionRanking(entry.getKey(), entry.getValue(), count));
                    }
                    ranking.sort((a, b) -> Integer.compare(b.getTotalStars(), a.getTotalStars()));

                    cb.onSuccess(ranking, myRegion);
                })
                .addOnFailureListener(e ->
                        cb.onError("Učitavanje rang liste regiona nije uspelo. Proveri internet konekciju."));
    }

    // -------------------------------------------------------------------------
    // Statistika jednog regiona
    // -------------------------------------------------------------------------

    /**
     * Vraća statistiku regiona: broj osvojenih 1./2./3. mesta (istorijski, iz
     * kolekcije "regions"), broj trenutno aktivnih igrača (odigrali bar jednu
     * partiju) i ukupan broj registrovanih igrača u regionu.
     */
    public void getRegionStats(@NonNull String regionName, @NonNull RegionStatsCallback cb) {
        mDb.collection(Constants.COLLECTION_USERS)
                .whereEqualTo("region", regionName)
                .get()
                .addOnSuccessListener(usersSnapshot -> {
                    int total = usersSnapshot.size();
                    int active = 0;
                    for (QueryDocumentSnapshot doc : usersSnapshot) {
                        Long matchesPlayed = doc.getLong("matchesPlayed");
                        if (matchesPlayed != null && matchesPlayed > 0) active++;
                    }
                    int finalActive = active;

                    String key = RegionKey.toKey(regionName);
                    mDb.collection(Constants.COLLECTION_REGIONS).document(key).get()
                            .addOnSuccessListener(regionDoc -> {
                                Long first = regionDoc.getLong("firstPlaceCount");
                                Long second = regionDoc.getLong("secondPlaceCount");
                                Long third = regionDoc.getLong("thirdPlaceCount");
                                cb.onSuccess(new RegionStats(
                                        first != null ? first.intValue() : 0,
                                        second != null ? second.intValue() : 0,
                                        third != null ? third.intValue() : 0,
                                        finalActive, total));
                            })
                            .addOnFailureListener(e ->
                                    cb.onError("Učitavanje statistike regiona nije uspelo."));
                })
                .addOnFailureListener(e ->
                        cb.onError("Učitavanje igrača regiona nije uspelo. Proveri internet konekciju."));
    }

    // -------------------------------------------------------------------------
    // Rezultat poslednjeg ciklusa (za obeležavanje regiona na mapi)
    // -------------------------------------------------------------------------

    /** Vraća prva tri regiona poslednjeg zaključenog mesečnog ciklusa, ili null ako nema podataka. */
    public void getLastCycleResult(@NonNull RegionCycleResultCallback cb) {
        mDb.collection(Constants.COLLECTION_REGION_CYCLE_RESULTS)
                .document(Constants.REGION_CYCLE_RESULTS_DOC)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        cb.onSuccess(null);
                        return;
                    }
                    cb.onSuccess(doc.toObject(RegionCycleResult.class));
                })
                .addOnFailureListener(e ->
                        cb.onError("Učitavanje prethodnog ciklusa regiona nije uspelo."));
    }
}

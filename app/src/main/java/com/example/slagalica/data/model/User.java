package com.example.slagalica.data.model;

/**
 * POJO klasa koja predstavlja korisnika aplikacije.
 * Koristi se za čitanje/pisanje u Firestore kolekciji "users".
 *
 * <p>Firestore zahteva: public no-arg konstruktor, public getteri i setteri,
 * bez final polja.</p>
 */
public class User {

    private String uid;
    private String email;
    private String username;
    private String region;
    /** URL slike avatara. Ako je null, koristi se lokalni default placeholder. */
    private String avatarUrl;
    private int tokens;
    private int totalStars;
    /** Broj zvezda u tekućem nedeljnom ciklusu rang liste; resetuje se na 0 kada se ciklus završi. */
    private int weeklyStars;
    /** Broj zvezda u tekućem mesečnom ciklusu rang liste; resetuje se na 0 kada se ciklus završi. */
    private int monthlyStars;
    private int currentLeague;
    /** Unix timestamp (ms) poslednjeg dodeljivanja dnevnih tokena. */
    private long lastTokenGrantTimestamp;
    /** Unix timestamp (ms) kreiranja naloga. */
    private long createdAt;
    /**
     * Tip okvira avatara:
     * 0 = default, 1 = zlatni, 2 = srebrni, 3 = bronzani.
     */
    private int avatarFrameType;

    // -------------------------------------------------------------------------
    // Statistika igara (dodatak Studenta 2 — Prikaz profila / statistika).
    // Uvećavaju se kroz FieldValue.increment nakon svake igre; podrazumevano 0.
    // -------------------------------------------------------------------------

    private long matchesPlayed;
    private long matchesWon;

    private long kzzCorrect;
    private long kzzWrong;
    private long kzzPointsSum;
    private long kzzGames;

    private long spojniceConnected;
    private long spojniceMissed;
    private long spojnicePointsSum;
    private long spojniceGames;

    private long mojBrojHits;
    private long mojBrojGames;
    private long mojBrojPointsSum;
    /** Broj odigranih rundi "Moj broj" (2 po partiji) — imenilac za procenat pogodaka. */
    private long mojBrojRounds;
    private long korakStepSum;
    private long korakHits;
    private long korakGames;
    private long korakPointsSum;
    // Broj pogodaka po koraku (1-7) dok je igrač bio aktivan u rundi — imenilac je korakGames
    // (igrač je aktivan tačno jednom po partiji). Vidi specifikaciju 2.c.iv.
    private long korakStepHit1;
    private long korakStepHit2;
    private long korakStepHit3;
    private long korakStepHit4;
    private long korakStepHit5;
    private long korakStepHit6;
    private long korakStepHit7;
    private long asocSolved;
    private long asocUnsolved;
    private long asocGames;
    private long asocPointsSum;
    private long skockoEarlyHits;
    private long skockoGames;
    private long skockoPointsSum;

    // -------------------------------------------------------------------------
    // Konstruktori
    // -------------------------------------------------------------------------

    /** Prazan konstruktor — obavezan za Firestore deserijalizaciju. */
    public User() {
        this.tokens = 5;
        this.totalStars = 0;
        this.currentLeague = 0;
        this.avatarFrameType = 0;
    }

    /**
     * Konstruktor sa svim poljima.
     *
     * @param uid                      Firebase Auth UID
     * @param email                    email adresa korisnika
     * @param username                 korisničko ime
     * @param region                   region (npr. "Beograd")
     * @param avatarUrl                URL avatara, može biti null
     * @param tokens                   trenutni broj tokena
     * @param totalStars               ukupan broj zvezda
     * @param currentLeague            trenutna liga (0 = bronzana, itd.)
     * @param lastTokenGrantTimestamp  timestamp poslednjeg dodeljivanja tokena (ms)
     * @param createdAt                timestamp kreiranja naloga (ms)
     * @param avatarFrameType          tip okvira avatara (0–3)
     */
    public User(String uid, String email, String username, String region,
                String avatarUrl, int tokens, int totalStars, int currentLeague,
                long lastTokenGrantTimestamp, long createdAt, int avatarFrameType) {
        this.uid = uid;
        this.email = email;
        this.username = username;
        this.region = region;
        this.avatarUrl = avatarUrl;
        this.tokens = tokens;
        this.totalStars = totalStars;
        this.currentLeague = currentLeague;
        this.lastTokenGrantTimestamp = lastTokenGrantTimestamp;
        this.createdAt = createdAt;
        this.avatarFrameType = avatarFrameType;
    }

    // -------------------------------------------------------------------------
    // Getteri
    // -------------------------------------------------------------------------

    public String getUid() {
        return uid;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    public String getRegion() {
        return region;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public int getTokens() {
        return tokens;
    }

    public int getTotalStars() {
        return totalStars;
    }

    public int getWeeklyStars() {
        return weeklyStars;
    }

    public int getMonthlyStars() {
        return monthlyStars;
    }

    public int getCurrentLeague() {
        return currentLeague;
    }

    public long getLastTokenGrantTimestamp() {
        return lastTokenGrantTimestamp;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public int getAvatarFrameType() {
        return avatarFrameType;
    }

    // -------------------------------------------------------------------------
    // Setteri
    // -------------------------------------------------------------------------

    public void setUid(String uid) {
        this.uid = uid;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public void setTokens(int tokens) {
        this.tokens = tokens;
    }

    public void setTotalStars(int totalStars) {
        this.totalStars = totalStars;
    }

    public void setWeeklyStars(int weeklyStars) {
        this.weeklyStars = weeklyStars;
    }

    public void setMonthlyStars(int monthlyStars) {
        this.monthlyStars = monthlyStars;
    }

    public void setCurrentLeague(int currentLeague) {
        this.currentLeague = currentLeague;
    }

    public void setLastTokenGrantTimestamp(long lastTokenGrantTimestamp) {
        this.lastTokenGrantTimestamp = lastTokenGrantTimestamp;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public void setAvatarFrameType(int avatarFrameType) {
        this.avatarFrameType = avatarFrameType;
    }

    // -------------------------------------------------------------------------
    // Getteri/setteri statistike (Student 2)
    // -------------------------------------------------------------------------

    public long getMatchesPlayed() { return matchesPlayed; }
    public void setMatchesPlayed(long matchesPlayed) { this.matchesPlayed = matchesPlayed; }

    public long getMatchesWon() { return matchesWon; }
    public void setMatchesWon(long matchesWon) { this.matchesWon = matchesWon; }

    public long getKzzCorrect() { return kzzCorrect; }
    public void setKzzCorrect(long kzzCorrect) { this.kzzCorrect = kzzCorrect; }

    public long getKzzWrong() { return kzzWrong; }
    public void setKzzWrong(long kzzWrong) { this.kzzWrong = kzzWrong; }

    public long getKzzPointsSum() { return kzzPointsSum; }
    public void setKzzPointsSum(long kzzPointsSum) { this.kzzPointsSum = kzzPointsSum; }

    public long getKzzGames() { return kzzGames; }
    public void setKzzGames(long kzzGames) { this.kzzGames = kzzGames; }

    public long getSpojniceConnected() { return spojniceConnected; }
    public void setSpojniceConnected(long spojniceConnected) { this.spojniceConnected = spojniceConnected; }

    public long getSpojniceMissed() { return spojniceMissed; }
    public void setSpojniceMissed(long spojniceMissed) { this.spojniceMissed = spojniceMissed; }

    public long getSpojnicePointsSum() { return spojnicePointsSum; }
    public void setSpojnicePointsSum(long spojnicePointsSum) { this.spojnicePointsSum = spojnicePointsSum; }

    public long getSpojniceGames() { return spojniceGames; }
    public void setSpojniceGames(long spojniceGames) { this.spojniceGames = spojniceGames; }

    public long getMojBrojHits() { return mojBrojHits; }
    public void setMojBrojHits(long mojBrojHits) { this.mojBrojHits = mojBrojHits; }

    public long getMojBrojGames() { return mojBrojGames; }
    public void setMojBrojGames(long mojBrojGames) { this.mojBrojGames = mojBrojGames; }

    public long getMojBrojPointsSum() { return mojBrojPointsSum; }
    public void setMojBrojPointsSum(long mojBrojPointsSum) { this.mojBrojPointsSum = mojBrojPointsSum; }

    public long getMojBrojRounds() { return mojBrojRounds; }
    public void setMojBrojRounds(long mojBrojRounds) { this.mojBrojRounds = mojBrojRounds; }

    public long getKorakStepSum() { return korakStepSum; }
    public void setKorakStepSum(long korakStepSum) { this.korakStepSum = korakStepSum; }

    public long getKorakHits() { return korakHits; }
    public void setKorakHits(long korakHits) { this.korakHits = korakHits; }

    public long getKorakGames() { return korakGames; }
    public void setKorakGames(long korakGames) { this.korakGames = korakGames; }

    public long getKorakPointsSum() { return korakPointsSum; }
    public void setKorakPointsSum(long korakPointsSum) { this.korakPointsSum = korakPointsSum; }

    public long getKorakStepHit1() { return korakStepHit1; }
    public void setKorakStepHit1(long korakStepHit1) { this.korakStepHit1 = korakStepHit1; }

    public long getKorakStepHit2() { return korakStepHit2; }
    public void setKorakStepHit2(long korakStepHit2) { this.korakStepHit2 = korakStepHit2; }

    public long getKorakStepHit3() { return korakStepHit3; }
    public void setKorakStepHit3(long korakStepHit3) { this.korakStepHit3 = korakStepHit3; }

    public long getKorakStepHit4() { return korakStepHit4; }
    public void setKorakStepHit4(long korakStepHit4) { this.korakStepHit4 = korakStepHit4; }

    public long getKorakStepHit5() { return korakStepHit5; }
    public void setKorakStepHit5(long korakStepHit5) { this.korakStepHit5 = korakStepHit5; }

    public long getKorakStepHit6() { return korakStepHit6; }
    public void setKorakStepHit6(long korakStepHit6) { this.korakStepHit6 = korakStepHit6; }

    public long getKorakStepHit7() { return korakStepHit7; }
    public void setKorakStepHit7(long korakStepHit7) { this.korakStepHit7 = korakStepHit7; }

    public long getAsocSolved() { return asocSolved; }
    public void setAsocSolved(long asocSolved) { this.asocSolved = asocSolved; }

    public long getAsocUnsolved() { return asocUnsolved; }
    public void setAsocUnsolved(long asocUnsolved) { this.asocUnsolved = asocUnsolved; }

    public long getAsocGames() { return asocGames; }
    public void setAsocGames(long asocGames) { this.asocGames = asocGames; }

    public long getAsocPointsSum() { return asocPointsSum; }
    public void setAsocPointsSum(long asocPointsSum) { this.asocPointsSum = asocPointsSum; }

    public long getSkockoEarlyHits() { return skockoEarlyHits; }
    public void setSkockoEarlyHits(long skockoEarlyHits) { this.skockoEarlyHits = skockoEarlyHits; }

    public long getSkockoGames() { return skockoGames; }
    public void setSkockoGames(long skockoGames) { this.skockoGames = skockoGames; }

    public long getSkockoPointsSum() { return skockoPointsSum; }
    public void setSkockoPointsSum(long skockoPointsSum) { this.skockoPointsSum = skockoPointsSum; }

    // -------------------------------------------------------------------------
    // toString
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        return "User{" +
                "uid='" + uid + '\'' +
                ", email='" + email + '\'' +
                ", username='" + username + '\'' +
                ", region='" + region + '\'' +
                ", avatarUrl='" + avatarUrl + '\'' +
                ", tokens=" + tokens +
                ", totalStars=" + totalStars +
                ", currentLeague=" + currentLeague +
                ", lastTokenGrantTimestamp=" + lastTokenGrantTimestamp +
                ", createdAt=" + createdAt +
                ", avatarFrameType=" + avatarFrameType +
                '}';
    }
}


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


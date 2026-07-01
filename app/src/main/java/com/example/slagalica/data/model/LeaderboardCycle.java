package com.example.slagalica.data.model;

/**
 * POJO klasa koja predstavlja jedan ciklus rang liste (nedeljni ili mesečni).
 * Koristi se za čitanje/pisanje u Firestore kolekciji "leaderboardCycles".
 *
 * <p>Firestore zahteva: public no-arg konstruktor, public getteri i setteri.</p>
 */
public class LeaderboardCycle {

    /** Unix timestamp (ms) početka tekućeg ciklusa. */
    private long cycleStart;
    /** Unix timestamp (ms) planiranog kraja tekućeg ciklusa. */
    private long cycleEnd;

    /** Prazan konstruktor — obavezan za Firestore deserijalizaciju. */
    public LeaderboardCycle() {
    }

    public LeaderboardCycle(long cycleStart, long cycleEnd) {
        this.cycleStart = cycleStart;
        this.cycleEnd = cycleEnd;
    }

    public long getCycleStart() {
        return cycleStart;
    }

    public void setCycleStart(long cycleStart) {
        this.cycleStart = cycleStart;
    }

    public long getCycleEnd() {
        return cycleEnd;
    }

    public void setCycleEnd(long cycleEnd) {
        this.cycleEnd = cycleEnd;
    }
}

package com.example.slagalica.data.model;

/**
 * Jedan učesnik izazova — čvor u {@code challenges/{challengeId}/participants/{uid}}.
 */
public class ChallengeParticipant {

    private String uid;
    private String name;
    private boolean joined;
    private boolean finished;
    private int score;
    private long finishedAt;

    /** Obavezan prazan konstruktor za Realtime Database. */
    public ChallengeParticipant() {
    }

    public ChallengeParticipant(String uid, String name, boolean joined, boolean finished,
                                int score, long finishedAt) {
        this.uid = uid;
        this.name = name;
        this.joined = joined;
        this.finished = finished;
        this.score = score;
        this.finishedAt = finishedAt;
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isJoined() { return joined; }
    public void setJoined(boolean joined) { this.joined = joined; }

    public boolean isFinished() { return finished; }
    public void setFinished(boolean finished) { this.finished = finished; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public long getFinishedAt() { return finishedAt; }
    public void setFinishedAt(long finishedAt) { this.finishedAt = finishedAt; }
}

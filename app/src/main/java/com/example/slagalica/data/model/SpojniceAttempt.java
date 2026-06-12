package com.example.slagalica.data.model;

/**
 * Pokušaj povezivanja jednog levog pojma u igri "Spojnice".
 * Čuva se u Realtime Database pod
 * {@code matches/{matchId}/spojnice/state/{round}/attempts/{leftIdx}}.
 */
public class SpojniceAttempt {

    private String uid;
    private int rightIndex;
    private boolean ok;

    public SpojniceAttempt() {
        // obavezan prazan konstruktor za Realtime Database
    }

    public SpojniceAttempt(String uid, int rightIndex, boolean ok) {
        this.uid = uid;
        this.rightIndex = rightIndex;
        this.ok = ok;
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public int getRightIndex() { return rightIndex; }
    public void setRightIndex(int rightIndex) { this.rightIndex = rightIndex; }

    public boolean isOk() { return ok; }
    public void setOk(boolean ok) { this.ok = ok; }
}

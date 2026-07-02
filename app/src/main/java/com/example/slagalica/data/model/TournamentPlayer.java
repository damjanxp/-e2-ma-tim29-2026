package com.example.slagalica.data.model;

/**
 * Jedan učesnik turnira — snima se u {@code tournaments/{tid}/players/{uid}} pri
 * formiranju turnira, tako da bracket ekran može da prikaže avatar i nadimak bez
 * dodatnih upita ka Firestore-u.
 */
public class TournamentPlayer {

    private String uid;
    private String username;
    private int avatarIndex;

    public TournamentPlayer() {
        // obavezan prazan konstruktor za Realtime Database
    }

    public TournamentPlayer(String uid, String username, int avatarIndex) {
        this.uid = uid;
        this.username = username;
        this.avatarIndex = avatarIndex;
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public int getAvatarIndex() { return avatarIndex; }
    public void setAvatarIndex(int avatarIndex) { this.avatarIndex = avatarIndex; }
}

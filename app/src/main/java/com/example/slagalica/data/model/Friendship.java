package com.example.slagalica.data.model;

/**
 * POJO klasa koja predstavlja jednu vezu prijateljstva.
 * Koristi se za čitanje/pisanje u Firestore podkolekciji
 * {@code users/{uid}/friends/{friendUid}} (vidi "7. Prijatelji").
 *
 * <p>Veza se upisuje simetrično kod oba korisnika kroz
 * {@code FriendRepository#addFriend}. Ovaj zapis je samo pokazivač na
 * prijatelja — trenutni podaci (avatar, liga, zvezde) se uvek čitaju uživo
 * iz {@code users/{friendUid}} kada se prikazuje lista.</p>
 *
 * <p>Firestore zahteva: public no-arg konstruktor, public getteri i setteri.</p>
 */
public class Friendship {

    private String friendUid;
    private String friendUsername;
    private long addedAt;

    /** Prazan konstruktor — obavezan za Firestore deserijalizaciju. */
    public Friendship() {
    }

    public Friendship(String friendUid, String friendUsername, long addedAt) {
        this.friendUid = friendUid;
        this.friendUsername = friendUsername;
        this.addedAt = addedAt;
    }

    public String getFriendUid() { return friendUid; }
    public void setFriendUid(String friendUid) { this.friendUid = friendUid; }

    public String getFriendUsername() { return friendUsername; }
    public void setFriendUsername(String friendUsername) { this.friendUsername = friendUsername; }

    public long getAddedAt() { return addedAt; }
    public void setAddedAt(long addedAt) { this.addedAt = addedAt; }
}

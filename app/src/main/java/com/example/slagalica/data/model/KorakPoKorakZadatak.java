package com.example.slagalica.data.model;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * Firestore POJO koji predstavlja jedan zadatak za igru "Korak po korak".
 *
 * <p>Čuva se u Firestore kolekciji {@code korakPoKorak}.
 * Lista {@code koraci} sadrži tačno 7 hintova — indeks 0 je najteži
 * (donosi 20 poena), indeks 6 je najlakši (donosi 8 poena).</p>
 *
 * <p>Firestore zahteva: public no-arg konstruktor, public getteri i setteri,
 * bez {@code final} polja.</p>
 */
public class KorakPoKorakZadatak {

    /** Firestore document ID. */
    private String id;

    /** Tačno rešenje — odgovor koji igrač treba da pogodi. */
    private String resenje;

    /**
     * Lista od tačno 7 hintova.
     * Indeks 0 = najteži hint (prvi koji se prikazuje, 20 poena).
     * Indeks 6 = najlakši hint (poslednji, 8 poena).
     */
    private List<String> koraci;

    // -------------------------------------------------------------------------
    // Konstruktori
    // -------------------------------------------------------------------------

    /** Prazan konstruktor — obavezan za Firestore deserijalizaciju. */
    public KorakPoKorakZadatak() {
    }

    /**
     * Konstruktor sa svim poljima.
     *
     * @param id      Firestore document ID
     * @param resenje tačno rešenje zadatka
     * @param koraci  lista od tačno 7 hintova (od najtežeg ka najlakšem)
     */
    public KorakPoKorakZadatak(String id, String resenje, List<String> koraci) {
        this.id = id;
        this.resenje = resenje;
        this.koraci = koraci;
    }

    // -------------------------------------------------------------------------
    // Getteri
    // -------------------------------------------------------------------------

    public String getId() {
        return id;
    }

    public String getResenje() {
        return resenje;
    }

    public List<String> getKoraci() {
        return koraci;
    }

    // -------------------------------------------------------------------------
    // Setteri
    // -------------------------------------------------------------------------

    public void setId(String id) {
        this.id = id;
    }

    public void setResenje(String resenje) {
        this.resenje = resenje;
    }

    public void setKoraci(List<String> koraci) {
        this.koraci = koraci;
    }

    // -------------------------------------------------------------------------
    // toString
    // -------------------------------------------------------------------------

    @NonNull
    @Override
    public String toString() {
        return "KorakPoKorakZadatak{" +
                "id='" + id + '\'' +
                ", resenje='" + resenje + '\'' +
                ", koraci=" + koraci +
                '}';
    }
}




package com.example.slagalica.data.model;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Stanje jednog turnira za četiri igrača — dva polufinala i finale između
 * pobednika. Popunjava se ručnim parsiranjem u {@code TournamentRepository}
 * (vidi {@link TournamentSlot}), pa nije Firebase POJO.
 *
 * <pre>
 * tournaments/{tid}
 *   status       — SEMIS | FINAL | DONE
 *   createdAt
 *   players/{uid}         — {uid, username, avatarIndex}
 *   semifinal1 / semifinal2 / final — {@link TournamentSlot}
 *   champion     — uid pobednika turnira
 * </pre>
 */
public class Tournament {

    @Nullable public String id;
    @Nullable public String status;
    @Nullable public String champion;

    public final Map<String, TournamentPlayer> players = new HashMap<>();
    public TournamentSlot semifinal1 = new TournamentSlot();
    public TournamentSlot semifinal2 = new TournamentSlot();
    public TournamentSlot finalMatch = new TournamentSlot();

    /** Vraća slot po ključu ({@code semifinal1|semifinal2|final}) ili {@code null}. */
    @Nullable
    public TournamentSlot slotByKey(@Nullable String key) {
        if (key == null) return null;
        switch (key) {
            case "semifinal1": return semifinal1;
            case "semifinal2": return semifinal2;
            case "final":      return finalMatch;
            default:           return null;
        }
    }

    /**
     * Vraća ključ polufinala u kom učestvuje dati igrač, ili {@code null}
     * ako nije ni u jednom polufinalu.
     */
    @Nullable
    public String semifinalKeyFor(@Nullable String uid) {
        if (semifinal1.contains(uid)) return "semifinal1";
        if (semifinal2.contains(uid)) return "semifinal2";
        return null;
    }

    @Nullable
    public TournamentPlayer player(@Nullable String uid) {
        return uid == null ? null : players.get(uid);
    }

    /** True kada su oba polufinala dala pobednika. */
    public boolean bothSemifinalsDecided() {
        return semifinal1.isDecided() && semifinal2.isDecided();
    }
}

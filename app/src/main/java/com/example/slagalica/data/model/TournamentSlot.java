package com.example.slagalica.data.model;

import androidx.annotation.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Jedan meč u turnirskom bracketu (polufinale ili finale). Parsira se ručno iz
 * {@link com.google.firebase.database.DataSnapshot} u
 * {@code TournamentRepository} — nije Firebase POJO jer sadrži ugnežden
 * {@code ready} skup.
 *
 * <pre>
 * tournaments/{tid}/{slotKey}
 *   matchId      — id meča u čvoru {@code matches/}
 *   p1Uid, p2Uid — učesnici para (p1 je "player1" i upisuje sadržaj meča)
 *   winnerUid    — pobednik (postavljen tek kada se meč završi)
 *   ready/{uid}  — true kada je igrač spreman da uđe u meč
 * </pre>
 */
public class TournamentSlot {

    @Nullable public String matchId;
    @Nullable public String p1Uid;
    @Nullable public String p2Uid;
    @Nullable public String winnerUid;
    public final Set<String> ready = new HashSet<>();

    /** True kada su poznata oba učesnika para. */
    public boolean hasBothPlayers() {
        return p1Uid != null && p2Uid != null;
    }

    /** True kada su oba učesnika označila spremnost. */
    public boolean bothReady() {
        return p1Uid != null && p2Uid != null
                && ready.contains(p1Uid) && ready.contains(p2Uid);
    }

    /** True kada je meč završen (poznat pobednik). */
    public boolean isDecided() {
        return winnerUid != null;
    }

    /** Vraća uid protivnika za dati uid unutar ovog para, ili {@code null}. */
    @Nullable
    public String opponentOf(String uid) {
        if (uid == null) return null;
        if (uid.equals(p1Uid)) return p2Uid;
        if (uid.equals(p2Uid)) return p1Uid;
        return null;
    }

    /** True ako je dati igrač učesnik ovog para. */
    public boolean contains(@Nullable String uid) {
        return uid != null && (uid.equals(p1Uid) || uid.equals(p2Uid));
    }
}

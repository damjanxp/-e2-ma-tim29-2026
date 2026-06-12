package com.example.slagalica.util;

import androidx.annotation.Nullable;

import com.example.slagalica.R;

/**
 * Mapira izbor avatara na lokalni drawable resurs.
 *
 * <p>Firebase Storage se ne koristi (zahteva plaćeni plan, po AGENTS.md), pa su
 * avatari skup lokalnih vektorskih slika. Redni broj izabranog avatara čuva se
 * kao string u polju {@code avatarUrl} korisničkog profila (timska šema koristi
 * to polje za avatar). Kada tim uvede prave URL-ove, ovaj sloj se proširuje da
 * učita sliku (npr. Glide-om); do tada se string koji nije broj tretira kao
 * podrazumevani avatar.</p>
 */
public final class AvatarProvider {

    private static final int[] AVATARS = {
            R.drawable.avatar_0,
            R.drawable.avatar_1,
            R.drawable.avatar_2,
            R.drawable.avatar_3,
            R.drawable.avatar_4,
            R.drawable.avatar_5
    };

    private AvatarProvider() {
        // ne instancira se
    }

    /** Vraća drawable resurs za zadati indeks (van opsega → prvi avatar). */
    public static int getDrawableRes(int avatarIndex) {
        if (avatarIndex < 0 || avatarIndex >= AVATARS.length) {
            return AVATARS[0];
        }
        return AVATARS[avatarIndex];
    }

    /** Vraća drawable za vrednost zapamćenu u {@code avatarUrl} polju profila. */
    public static int getDrawableForStored(@Nullable String stored) {
        return getDrawableRes(indexFromStored(stored));
    }

    /** Parsira {@code avatarUrl} string u indeks lokalnog avatara (fallback 0). */
    public static int indexFromStored(@Nullable String stored) {
        if (stored == null || stored.isEmpty()) {
            return 0;
        }
        try {
            int idx = Integer.parseInt(stored.trim());
            return (idx >= 0 && idx < AVATARS.length) ? idx : 0;
        } catch (NumberFormatException e) {
            return 0; // pravi URL ili nepoznata vrednost — podrazumevani avatar
        }
    }

    /** Vrednost koja se upisuje u {@code avatarUrl} za izabrani lokalni avatar. */
    public static String storedValue(int avatarIndex) {
        return String.valueOf(avatarIndex);
    }

    /** Ukupan broj dostupnih avatara. */
    public static int count() {
        return AVATARS.length;
    }
}

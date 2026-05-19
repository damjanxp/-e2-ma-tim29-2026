package com.example.slagalica.logic.games;

import androidx.annotation.NonNull;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Statička utility klasa sa poslovnom logikom za igru "Moj broj".
 *
 * <p>Ne sme da instancira stanje — sve metode su {@code static}.
 * Ne importuje ništa iz {@code ui/} paketa.</p>
 *
 * <p>Zavisnosti: exp4j 0.4.8 (deklarisan u {@code app/build.gradle}).</p>
 */
public final class MojBrojLogic {

    /** Regex koji izvlači sve celobrojne tokene iz izraza. */
    private static final Pattern BROJ_PATTERN = Pattern.compile("\\d+");

    /** Sprečava instanciranje utility klase. */
    private MojBrojLogic() {
    }

    // =========================================================================
    // Pomoćna klasa za rezultat evaluacije
    // =========================================================================

    /**
     * Nosi rezultat evaluacije matematičkog izraza.
     *
     * <ul>
     *   <li>Ako je {@code validan == true}: {@code rezultat} sadrži izračunatu vrednost.</li>
     *   <li>Ako je {@code validan == false}: {@code greska} opisuje razlog nevalidnosti.</li>
     * </ul>
     */
    public static class IzrazRezultat {
        /** True ako je izraz sintaksno ispravan, koristi dozvoljena pitanja i evaluacija uspešna. */
        public boolean validan;
        /** Izračunata vrednost izraza. Relevantno samo ako {@code validan == true}. */
        public double rezultat;
        /** Opis greške. Relevantno samo ako {@code validan == false}. */
        public String greska;

        /** Fabrikovna metoda za uspešan rezultat. */
        static IzrazRezultat ok(double rezultat) {
            IzrazRezultat r = new IzrazRezultat();
            r.validan  = true;
            r.rezultat = rezultat;
            return r;
        }

        /** Fabrikovna metoda za neuspešan rezultat. */
        static IzrazRezultat greska(String poruka) {
            IzrazRezultat r = new IzrazRezultat();
            r.validan = false;
            r.greska  = poruka;
            return r;
        }
    }

    // =========================================================================
    // Evaluacija izraza
    // =========================================================================

    /**
     * Evaluira matematički izraz unet od strane igrača.
     *
     * <p>Koraci:</p>
     * <ol>
     *   <li>Zamena UI simbola ({@code ×}→{@code *}, {@code ÷}→{@code /}).</li>
     *   <li>Sintaksna validacija (zagrade, operatori).</li>
     *   <li>Validacija multiset-a — svaki broj mora biti dostupan u tačnom broju pojavljivanja.</li>
     *   <li>Evaluacija putem exp4j.</li>
     * </ol>
     *
     * <p><b>Unit test primeri:</b></p>
     * <pre>
     *   evaluate("25+3",    [25,3,5,7,10,50])  → validan=true,  rezultat=28.0
     *   evaluate("3*3",     [25,3,5,7,10,50])  → validan=false, greska="Broj 3 nije dovoljno puta dostupan"
     *   evaluate("25/0",    [25,3,5,7,10,50])  → validan=false, greska="Deljenje nulom"
     *   evaluate("25++3",   [25,3,5,7,10,50])  → validan=false, greska="Dva uzastopna operatora"
     *   evaluate("(25+3",   [25,3,5,7,10,50])  → validan=false, greska="Nebalansirane zagrade"
     *   evaluate("",        [25,3,5,7,10,50])  → validan=false, greska="Izraz je prazan"
     * </pre>
     *
     * @param izraz           matematički izraz koji je uneo igrač (može sadržati × i ÷)
     * @param dostupniBrojevi niz od 6 dostupnih brojeva za ovu rundu
     * @return {@link IzrazRezultat} sa rezultatom ili opisom greške
     */
    @NonNull
    public static IzrazRezultat evaluate(@NonNull String izraz, @NonNull int[] dostupniBrojevi) {
        // Korak 1: normalizuj simbole
        String normalized = izraz.trim()
                .replace('×', '*')
                .replace('÷', '/');

        if (normalized.isEmpty()) {
            return IzrazRezultat.greska("Izraz je prazan.");
        }

        // Korak 2: sintaksna validacija
        IzrazRezultat sintaksaGreska = validirajSintaksu(normalized);
        if (sintaksaGreska != null) {
            return sintaksaGreska;
        }

        // Korak 3: validacija multiset-a
        IzrazRezultat multisetGreska = validirajMultiset(normalized, dostupniBrojevi);
        if (multisetGreska != null) {
            return multisetGreska;
        }

        // Korak 4: evaluacija putem exp4j
        try {
            Expression expression = new ExpressionBuilder(normalized).build();
            double rezultat = expression.evaluate();

            if (Double.isInfinite(rezultat) || Double.isNaN(rezultat)) {
                return IzrazRezultat.greska("Deljenje nulom nije dozvoljeno.");
            }

            return IzrazRezultat.ok(rezultat);

        } catch (ArithmeticException e) {
            return IzrazRezultat.greska("Deljenje nulom nije dozvoljeno.");
        } catch (Exception e) {
            return IzrazRezultat.greska("Izraz nije ispravan: " + e.getMessage());
        }
    }

    // =========================================================================
    // Bodovanje
    // =========================================================================

    /**
     * Izračunava bodove oba igrača za igru "Moj broj".
     *
     * <p>Pravila bodovanja:</p>
     * <ul>
     *   <li>Oba točna (jednaki traženom): svaki dobija 10 bodova.</li>
     *   <li>Samo jedan tačan: tačan dobija 10, drugi 0.</li>
     *   <li>Oba validni ali netačni, isti rezultat: igrač čija je runda dobija 5.</li>
     *   <li>Oba validni ali netačni, različiti: bliži traženom dobija 5.</li>
     *   <li>Nevalidan/prazan izraz: 0 bodova.</li>
     * </ul>
     *
     * <p><b>Unit test primeri:</b></p>
     * <pre>
     *   izracunajBodovanje(100, ok(100), ok(100), true)  → [10, 10]
     *   izracunajBodovanje(100, ok(100), ok(95),  true)  → [10, 0]
     *   izracunajBodovanje(100, ok(90),  ok(100), true)  → [0, 10]
     *   izracunajBodovanje(100, ok(95),  ok(90),  true)  → [5, 0]   // bliži
     *   izracunajBodovanje(100, ok(95),  ok(95),  true)  → [5, 0]   // isti, aktivan=r1
     *   izracunajBodovanje(100, greska,  greska,  true)  → [0, 0]
     * </pre>
     *
     * @param trazeni          traženi broj (cilj)
     * @param r1               rezultat prvog igrača
     * @param r2               rezultat drugog igrača
     * @param prviIgracJeAktivan true ako je runda "prvog igrača" (koristi se za rešavanje izjednačenja)
     * @return int[] dužine 2: {@code [bodoviPrvog, bodoviDrugog]}
     */
    @NonNull
    public static int[] izracunajBodovanje(double trazeni,
                                           @NonNull IzrazRezultat r1,
                                           @NonNull IzrazRezultat r2,
                                           boolean prviIgracJeAktivan) {
        boolean tacan1 = r1.validan && r1.rezultat == trazeni;
        boolean tacan2 = r2.validan && r2.rezultat == trazeni;

        // Oba tačna
        if (tacan1 && tacan2) {
            return new int[]{10, 10};
        }

        // Samo prvi tačan
        if (tacan1) {
            return new int[]{10, 0};
        }

        // Samo drugi tačan
        if (tacan2) {
            return new int[]{0, 10};
        }

        // Ni jedan nije tačan — daj 5 bliže ili aktiv igraču
        if (!r1.validan && !r2.validan) {
            return new int[]{0, 0};
        }

        if (!r1.validan) {
            // r2 je jedini validan, ali nije tačan → 5 njemu
            return new int[]{0, 5};
        }

        if (!r2.validan) {
            // r1 je jedini validan, ali nije tačan → 5 njemu
            return new int[]{5, 0};
        }

        // Oba validna ali netačna
        double razlika1 = Math.abs(r1.rezultat - trazeni);
        double razlika2 = Math.abs(r2.rezultat - trazeni);

        if (razlika1 < razlika2) {
            return new int[]{5, 0};
        } else if (razlika2 < razlika1) {
            return new int[]{0, 5};
        } else {
            // Isti rezultat ili ista udaljenost — aktivan igrač dobija 5
            return prviIgracJeAktivan ? new int[]{5, 0} : new int[]{0, 5};
        }
    }

    // =========================================================================
    // Pomoćne metode — validacija
    // =========================================================================

    /**
     * Proverava sintaksnu ispravnost izraza.
     *
     * @return {@link IzrazRezultat} sa greškom, ili {@code null} ako je sintaksa OK
     */
    private static IzrazRezultat validirajSintaksu(@NonNull String izraz) {
        // Provera balansiranosti zagrada
        int balance = 0;
        for (char c : izraz.toCharArray()) {
            if (c == '(') balance++;
            else if (c == ')') balance--;
            if (balance < 0) {
                return IzrazRezultat.greska("Nebalansirane zagrade.");
            }
        }
        if (balance != 0) {
            return IzrazRezultat.greska("Nebalansirane zagrade.");
        }

        // Ne sme počinjati sa operatorom (osim unarnog minus)
        if (izraz.matches("^[+*/].*")) {
            return IzrazRezultat.greska("Izraz ne sme počinjati sa operatorom.");
        }

        // Ne sme završavati se operatorom
        if (izraz.matches(".*[+\\-*/]$")) {
            return IzrazRezultat.greska("Izraz ne sme završavati se operatorom.");
        }

        // Nema dva uzastopna binarna operatora (npr. ++ ili */)
        if (izraz.matches(".*[+\\-*/]{2,}.*")) {
            // Dozvoli -- (dvostruki minus može biti unaran) i *- ili +- (unaran minus iza operatora)
            // Ali zabrani ++, **, //, /*, itd.
            if (izraz.matches(".*[+*/][+*/].*") || izraz.matches(".*[+*/]{2,}.*")) {
                return IzrazRezultat.greska("Dva uzastopna operatora nisu dozvoljena.");
            }
        }

        return null; // sintaksa OK
    }

    /**
     * Proverava da li svi brojevi u izrazu postoje u dostupnim brojevima (multiset provera).
     *
     * <p>Izvlači sve celobrojne tokene regex-om, pravi multiset od dostupnih brojeva,
     * i proverava da se svaki broj koristi najviše onoliko puta koliko se pojavljuje u
     * {@code dostupniBrojevi}.</p>
     *
     * @return {@link IzrazRezultat} sa greškom, ili {@code null} ako su svi brojevi dostupni
     */
    private static IzrazRezultat validirajMultiset(@NonNull String izraz,
                                                    @NonNull int[] dostupniBrojevi) {
        // Napravi multiset dostupnih
        Map<Integer, Integer> dostupniMap = new HashMap<>();
        for (int b : dostupniBrojevi) {
            dostupniMap.compute(b, (k, v) -> v == null ? 1 : v + 1);
        }

        // Izvuci sve brojeve iz izraza
        Matcher matcher = BROJ_PATTERN.matcher(izraz);
        Map<Integer, Integer> koriscenoMap = new HashMap<>();
        while (matcher.find()) {
            int broj = Integer.parseInt(matcher.group());
            koriscenoMap.compute(broj, (k, v) -> v == null ? 1 : v + 1);
        }

        // Proveri da svaki korišćeni broj postoji u dovoljnom broju
        for (Map.Entry<Integer, Integer> entry : koriscenoMap.entrySet()) {
            int broj      = entry.getKey();
            int koristeno = entry.getValue();
            Integer dostupnoBoxed = dostupniMap.get(broj);
            int dostupno = dostupnoBoxed != null ? dostupnoBoxed : 0;

            if (dostupno == 0) {
                return IzrazRezultat.greska("Broj " + broj + " nije u dostupnim brojevima.");
            }
            if (koristeno > dostupno) {
                return IzrazRezultat.greska("Broj " + broj + " nije dovoljno puta dostupan " +
                        "(dostupan " + dostupno + "×, korišćen " + koristeno + "×).");
            }
        }

        return null; // multiset OK
    }
}






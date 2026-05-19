package com.example.slagalica.ui.games;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.example.slagalica.logic.games.MojBrojLogic;
import com.example.slagalica.util.ShakeDetector;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Aktivnost za igru "Moj broj".
 * KT2: puna implementacija — shake-to-stop, exp4j validacija izraza, bodovanje.
 *
 * <p>Faze igre:
 * <ul>
 *   <li>0 — čeka prvi STOP (ili auto-timer 5s) → generiše traženi broj.</li>
 *   <li>1 — čeka drugi STOP (ili auto-timer 5s) → generiše 6 dostupnih brojeva + pokreće 60s timer.</li>
 *   <li>2 — aktivna igra: igrač unosi izraz, pritiska Potvrdi ili protekne 60s.</li>
 * </ul>
 * </p>
 */
public class MojBrojActivity extends AppCompatActivity {

    /** Ključ za Intent extra koji prenosi bodove nazad u MatchOrchestratorActivity. */
    public static final String EXTRA_BODOVI = "moj_broj_bodovi";

    private static final int ROUND_DURATION_MS = 60_000;
    private static final int ROUND_TICK_MS     = 1_000;
    private static final int AUTO_STOP_MS      = 5_000;   // auto-STOP ako igrač ne pritisne
    private static final int MAX_ROUNDS        = 2;

    // ─── Views ───────────────────────────────────────────────────────────────
    private TextView          tvTrazeniBroj;
    private TextView[]        tvBrojevi;
    private TextInputLayout   tlIzraz;
    private TextInputEditText etIzraz;
    private ProgressBar       pbTimer;
    private TextView          tvRound;
    private TextView          tvScore;
    private MaterialButton[]  btnBrojevi;

    // ─── Stanje igre ─────────────────────────────────────────────────────────
    private ShakeDetector    shakeDetector;
    private CountDownTimer   rundaTimer;
    private CountDownTimer   autoStopTimer;

    private final StringBuilder izraz        = new StringBuilder();
    private int[]               dostupniBrojevi;
    private int                 trazeniBroj;
    private int                 faza         = 0;
    private int                 trenutnaRunda = 1;
    private int                 ukupnoBodova  = 0;

    private final Random random = new Random();

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moj_broj);

        initViews();
        setupListeners();

        shakeDetector = new ShakeDetector(this, this::onStopClicked);
        pokreniRundu();
    }

    @Override
    protected void onResume() {
        super.onResume();
        shakeDetector.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        shakeDetector.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        otkaziTimere();
    }

    // =========================================================================
    // Inicijalizacija
    // =========================================================================

    private void initViews() {
        tvTrazeniBroj = findViewById(R.id.tvTrazeniBroj);
        pbTimer       = findViewById(R.id.pbTimer);
        tlIzraz       = findViewById(R.id.tlIzraz);
        etIzraz       = findViewById(R.id.etIzraz);
        tvRound       = findViewById(R.id.tvRound);
        tvScore       = findViewById(R.id.tvScore);

        tvBrojevi = new TextView[]{
                findViewById(R.id.tvBroj1), findViewById(R.id.tvBroj2),
                findViewById(R.id.tvBroj3), findViewById(R.id.tvBroj4),
                findViewById(R.id.tvBroj5), findViewById(R.id.tvBroj6)
        };

        btnBrojevi = new MaterialButton[]{
                findViewById(R.id.btnB1), findViewById(R.id.btnB2),
                findViewById(R.id.btnB3), findViewById(R.id.btnB4),
                findViewById(R.id.btnB5), findViewById(R.id.btnB6)
        };
    }

    private void setupListeners() {
        // Brojčana dugmad — samo u fazi 2
        for (int i = 0; i < btnBrojevi.length; i++) {
            final int idx = i;
            btnBrojevi[i].setOnClickListener(v -> {
                if (faza == 2) appendToIzraz(tvBrojevi[idx].getText().toString());
            });
        }

        // Operatori
        findViewById(R.id.btnPlus).setOnClickListener(v       -> appendToIzraz("+"));
        findViewById(R.id.btnMinus).setOnClickListener(v      -> appendToIzraz("-"));
        findViewById(R.id.btnMult).setOnClickListener(v       -> appendToIzraz("×"));
        findViewById(R.id.btnDiv).setOnClickListener(v        -> appendToIzraz("÷"));
        findViewById(R.id.btnLeftParen).setOnClickListener(v  -> appendToIzraz("("));
        findViewById(R.id.btnRightParen).setOnClickListener(v -> appendToIzraz(")"));

        // Brisanje i reset
        findViewById(R.id.btnBackspace).setOnClickListener(v -> {
            if (izraz.length() > 0) {
                izraz.deleteCharAt(izraz.length() - 1);
                osvežiIzraz();
            }
        });
        findViewById(R.id.btnClear).setOnClickListener(v -> {
            izraz.setLength(0);
            osvežiIzraz();
        });

        // STOP (dugme) i Potvrdi
        findViewById(R.id.btnStop).setOnClickListener(v   -> onStopClicked());
        findViewById(R.id.btnPotvrdi).setOnClickListener(v -> naPotvrdi());

        // Predaj
        findViewById(R.id.btnGiveUp).setOnClickListener(v -> showGiveUpDialog());
    }

    // =========================================================================
    // Tok runde
    // =========================================================================

    /** Resetuje UI i pokreće novu rundu od faze 0. */
    private void pokreniRundu() {
        faza = 0;
        izraz.setLength(0);
        osvežiIzraz();
        tlIzraz.setError(null);

        // Resetuj prikaz
        tvTrazeniBroj.setText(getString(R.string.moj_broj_target_default));
        for (int i = 0; i < tvBrojevi.length; i++) {
            tvBrojevi[i].setText(getString(R.string.moj_broj_number_default));
            btnBrojevi[i].setText(getString(R.string.moj_broj_number_default));
        }
        pbTimer.setProgress(60);
        azurirajHeader();

        // Auto-timer: ako igrač ne pritisne STOP za 5s, skripta to uradi sama
        pokreniAutoStop();
    }

    /**
     * Pokreće 5s auto-timer koji automatski izvršava STOP akciju
     * (koristi se i za fazu 0 i za fazu 1).
     */
    private void pokreniAutoStop() {
        otkaziAutoStop();
        autoStopTimer = new CountDownTimer(AUTO_STOP_MS, AUTO_STOP_MS) {
            @Override public void onTick(long ms) { }
            @Override public void onFinish() { onStopClicked(); }
        }.start();
    }

    // =========================================================================
    // STOP logika (klik dugmeta + shake + auto-timer)
    // =========================================================================

    private void onStopClicked() {
        otkaziAutoStop();

        if (faza == 0) {
            // Generiši traženi broj [100, 999]
            trazeniBroj = 100 + random.nextInt(900);
            tvTrazeniBroj.setText(String.valueOf(trazeniBroj));
            faza = 1;
            pokreniAutoStop();  // čekaj STOP za dostupne brojeve

        } else if (faza == 1) {
            // Generiši 6 dostupnih brojeva i pokreni 60s igru
            dostupniBrojevi = generisi6Brojeva();
            prikaziBrojeve(dostupniBrojevi);
            faza = 2;
            pokreniTimer60s();

        } else if (faza == 2) {
            // Shake tokom igre = predaja runde bez bodova
            onRundaZavrsena(null);
        }
    }

    // =========================================================================
    // Potvrdi (evaluacija izraza)
    // =========================================================================

    private void naPotvrdi() {
        if (faza != 2) return;

        String unosStr = etIzraz.getText() != null ? etIzraz.getText().toString() : "";
        if (unosStr.trim().isEmpty()) {
            tlIzraz.setError(getString(R.string.error_field_required));
            return;
        }
        tlIzraz.setError(null);

        MojBrojLogic.IzrazRezultat rezultat = MojBrojLogic.evaluate(unosStr, dostupniBrojevi);

        if (!rezultat.validan) {
            tlIzraz.setError(rezultat.greska);
            return;
        }

        // Zaokruži na int za poređenje
        int izracunato = (int) Math.round(rezultat.rezultat);

        if (izracunato == trazeniBroj) {
            // Tačan odgovor
            otkaziTimere();
            ukupnoBodova += 10;
            azurirajScore();
            onRundaZavrsena(rezultat);
        } else {
            // Nije tačno — prikaži rezultat, nastavi igru
            Snackbar.make(etIzraz,
                    getString(R.string.moj_broj_dialog_near_msg, izracunato, trazeniBroj),
                    Snackbar.LENGTH_SHORT).show();
        }
    }

    // =========================================================================
    // Tajmer 60s
    // =========================================================================

    private void pokreniTimer60s() {
        otkaziRunduTimer();
        rundaTimer = new CountDownTimer(ROUND_DURATION_MS, ROUND_TICK_MS) {
            @Override
            public void onTick(long ms) {
                pbTimer.setProgress((int) (ms / 1000));
            }

            @Override
            public void onFinish() {
                pbTimer.setProgress(0);
                onRundaIstekla();
            }
        }.start();
    }

    // =========================================================================
    // Kraj runde
    // =========================================================================

    /** Poziva se kada tajmer istekne bez tačnog odgovora. */
    private void onRundaIstekla() {
        faza = 0;
        boolean jePoslednja = (trenutnaRunda >= MAX_ROUNDS);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.moj_broj_dialog_timeout_title))
                .setMessage(getString(R.string.moj_broj_dialog_timeout_msg, trazeniBroj))
                .setCancelable(false)
                .setPositiveButton(
                        jePoslednja ? getString(R.string.moj_broj_dialog_finish)
                                    : getString(R.string.moj_broj_dialog_next_round),
                        (d, w) -> {
                            if (jePoslednja) zavrsiIgru();
                            else sledecarRunda();
                        })
                .show();
    }

    /**
     * Poziva se kada igrač pritisne Potvrdi sa tačnim ili dovoljno bliskim odgovorom,
     * ili kada shake u fazi 2 zavrsi rundu.
     *
     * @param r rezultat evaluacije, {@code null} ako je runda predata bez unosa
     */
    private void onRundaZavrsena(MojBrojLogic.IzrazRezultat r) {
        faza = 0;
        boolean jePoslednja = (trenutnaRunda >= MAX_ROUNDS);

        String naslov  = (r != null && r.validan && (int) Math.round(r.rezultat) == trazeniBroj)
                ? getString(R.string.moj_broj_dialog_correct_title)
                : getString(R.string.moj_broj_dialog_near_title);
        String poruka  = (r != null && r.validan)
                ? getString(R.string.moj_broj_dialog_near_msg,
                            (int) Math.round(r.rezultat), trazeniBroj)
                : getString(R.string.moj_broj_dialog_timeout_msg, trazeniBroj);

        // Ako je tačan odgovor → posebna poruka
        if (r != null && r.validan && (int) Math.round(r.rezultat) == trazeniBroj) {
            poruka = getString(R.string.moj_broj_dialog_correct_msg, (int) Math.round(r.rezultat));
        }

        final String finalPoruka = poruka;
        new AlertDialog.Builder(this)
                .setTitle(naslov)
                .setMessage(finalPoruka)
                .setCancelable(false)
                .setPositiveButton(
                        jePoslednja ? getString(R.string.moj_broj_dialog_finish)
                                    : getString(R.string.moj_broj_dialog_next_round),
                        (d, w) -> {
                            if (jePoslednja) zavrsiIgru();
                            else sledecarRunda();
                        })
                .show();
    }

    private void sledecarRunda() {
        trenutnaRunda++;
        pokreniRundu();
    }

    // =========================================================================
    // Dijalog: predaj
    // =========================================================================

    private void showGiveUpDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.moj_broj_dialog_give_up_title))
                .setMessage(getString(R.string.moj_broj_dialog_give_up_msg))
                .setPositiveButton(getString(R.string.moj_broj_dialog_give_up_yes), (d, w) -> {
                    otkaziTimere();
                    setResult(RESULT_CANCELED);
                    finish();
                })
                .setNegativeButton(getString(R.string.moj_broj_dialog_give_up_no), null)
                .show();
    }

    // =========================================================================
    // Završetak igre
    // =========================================================================

    private void zavrsiIgru() {
        Intent result = new Intent();
        result.putExtra(EXTRA_BODOVI, ukupnoBodova);
        setResult(RESULT_OK, result);
        finish();
    }

    // =========================================================================
    // Generisanje brojeva
    // =========================================================================

    /**
     * Generiše skup od 6 dostupnih brojeva:
     * 4 jednocifrena (1–9), 1 iz {10,15,20}, 1 iz {25,50,75,100}.
     */
    private int[] generisi6Brojeva() {
        int[] mali   = {10, 15, 20};
        int[] veliki = {25, 50, 75, 100};

        List<Integer> lista = new ArrayList<>();
        for (int i = 0; i < 4; i++) lista.add(1 + random.nextInt(9));
        lista.add(mali[random.nextInt(mali.length)]);
        lista.add(veliki[random.nextInt(veliki.length)]);
        Collections.shuffle(lista, random);

        int[] niz = new int[lista.size()];
        for (int i = 0; i < lista.size(); i++) niz[i] = lista.get(i);
        return niz;
    }

    private void prikaziBrojeve(int[] brojevi) {
        for (int i = 0; i < tvBrojevi.length; i++) {
            String v = String.valueOf(brojevi[i]);
            tvBrojevi[i].setText(v);
            btnBrojevi[i].setText(v);
        }
    }

    // =========================================================================
    // Pomoćne metode
    // =========================================================================

    private void appendToIzraz(String token) {
        if (faza != 2) return;
        izraz.append(token);
        osvežiIzraz();
    }

    private void osvežiIzraz() {
        etIzraz.setText(izraz.toString());
    }

    private void azurirajHeader() {
        tvRound.setText(getString(R.string.moj_broj_round_label, trenutnaRunda));
        azurirajScore();
    }

    private void azurirajScore() {
        tvScore.setText(getString(R.string.moj_broj_score_label, ukupnoBodova));
    }

    private void otkaziAutoStop() {
        if (autoStopTimer != null) {
            autoStopTimer.cancel();
            autoStopTimer = null;
        }
    }

    private void otkaziRunduTimer() {
        if (rundaTimer != null) {
            rundaTimer.cancel();
            rundaTimer = null;
        }
    }

    private void otkaziTimere() {
        otkaziAutoStop();
        otkaziRunduTimer();
    }
}

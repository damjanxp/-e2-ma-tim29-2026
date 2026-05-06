package com.example.slagalica.ui.games;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Aktivnost za igru "Moj broj".
 * KT1: UI radi, logika je mock — nema exp4j validacije ni shake senzora.
 * KT2: prova validacija izraza (multiset + exp4j), shake-to-stop, bodovanje.
 *
 * Faze igre:
 * 0 — čeka prvi STOP (generiše traženi broj)
 * 1 — čeka drugi STOP (generiše 6 dostupnih brojeva, pokreće tajmer)
 * 2 — aktivna igra (unos izraza je moguć)
 */
public class MojBrojActivity extends AppCompatActivity {

    private static final int TIMER_DURATION_MS = 60_000;
    private static final int TIMER_TICK_MS     = 1_000;

    // Views
    private TextView   tvTrazeniBroj;
    private TextView[] tvBrojevi;          // tvBroj1..tvBroj6
    private TextInputEditText etIzraz;
    private ProgressBar pbTimer;

    // Tastatura — brojčana dugmad za dostupne brojeve
    private MaterialButton[] btnBrojevi;   // btnB1..btnB6

    // Stanje
    private StringBuilder izraz = new StringBuilder();
    private int faza = 0;
    private CountDownTimer gameTimer;
    private final Random random = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moj_broj);

        initViews();
        setupListeners();
    }

    // =========================================================================
    // Inicijalizacija
    // =========================================================================

    private void initViews() {
        tvTrazeniBroj = findViewById(R.id.tvTrazeniBroj);
        pbTimer       = findViewById(R.id.pbTimer);
        etIzraz       = findViewById(R.id.etIzraz);

        tvBrojevi = new TextView[]{
                findViewById(R.id.tvBroj1),
                findViewById(R.id.tvBroj2),
                findViewById(R.id.tvBroj3),
                findViewById(R.id.tvBroj4),
                findViewById(R.id.tvBroj5),
                findViewById(R.id.tvBroj6)
        };

        btnBrojevi = new MaterialButton[]{
                findViewById(R.id.btnB1),
                findViewById(R.id.btnB2),
                findViewById(R.id.btnB3),
                findViewById(R.id.btnB4),
                findViewById(R.id.btnB5),
                findViewById(R.id.btnB6)
        };
    }

    private void setupListeners() {
        // Brojčana dugmad — dodaju vrednost broja u izraz (samo u fazi 2)
        for (int i = 0; i < btnBrojevi.length; i++) {
            final int idx = i;
            btnBrojevi[i].setOnClickListener(v -> {
                if (faza == 2) {
                    appendToIzraz(tvBrojevi[idx].getText().toString());
                }
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

        // STOP — dvofazni
        findViewById(R.id.btnStop).setOnClickListener(v -> onStopClicked());

        // Potvrdi
        findViewById(R.id.btnPotvrdi).setOnClickListener(v ->
                Toast.makeText(this, getString(R.string.moj_broj_toast_kt1_validate),
                        Toast.LENGTH_SHORT).show());

        // Predaj
        findViewById(R.id.btnGiveUp).setOnClickListener(v -> finish());
    }

    // =========================================================================
    // Logika faza
    // =========================================================================

    private void onStopClicked() {
        if (faza == 0) {
            // Faza 0 → generiši traženi broj
            int cilj = 100 + random.nextInt(900);   // [100, 999]
            tvTrazeniBroj.setText(String.valueOf(cilj));
            faza = 1;

        } else if (faza == 1) {
            // Faza 1 → generiši 6 dostupnih brojeva i pokreni tajmer
            int[] brojevi = generisi6Brojeva();
            prikaziBrojeve(brojevi);
            pokreniTimer60s();
            faza = 2;
        }
        // faza == 2: STOP pokrenut tokom igre → ignorisati ili tretirati kao predaju (KT2)
    }

    // =========================================================================
    // Generisanje brojeva
    // =========================================================================

    /**
     * Generiše skup od 6 dostupnih brojeva:
     * <ul>
     *   <li>4 jednocifrena (1–9)</li>
     *   <li>1 iz skupa {10, 15, 20}</li>
     *   <li>1 iz skupa {25, 50, 75, 100}</li>
     * </ul>
     * Rezultat je izmešan slučajnim redosledom.
     *
     * @return niz od 6 celih brojeva
     */
    private int[] generisi6Brojeva() {
        int[] mali    = {10, 15, 20};
        int[] veliki  = {25, 50, 75, 100};

        List<Integer> lista = new ArrayList<>();

        // 4 jednocifrena (1–9, ponavljanje dozvoljeno)
        for (int i = 0; i < 4; i++) {
            lista.add(1 + random.nextInt(9));
        }

        // 1 iz {10, 15, 20}
        lista.add(mali[random.nextInt(mali.length)]);

        // 1 iz {25, 50, 75, 100}
        lista.add(veliki[random.nextInt(veliki.length)]);

        Collections.shuffle(lista, random);

        int[] rezultat = new int[lista.size()];
        for (int i = 0; i < lista.size(); i++) {
            rezultat[i] = lista.get(i);
        }
        return rezultat;
    }

    /** Puni tvBroj1..tvBroj6 i btnB1..btnB6 sa generisanim brojevima. */
    private void prikaziBrojeve(int[] brojevi) {
        for (int i = 0; i < tvBrojevi.length; i++) {
            String vrednost = String.valueOf(brojevi[i]);
            tvBrojevi[i].setText(vrednost);
            btnBrojevi[i].setText(vrednost);
        }
    }

    // =========================================================================
    // Tajmer
    // =========================================================================

    /**
     * Pokreće CountDownTimer od 60s, ažurira ProgressBar svaki 1s.
     * Na isteku prikazuje Toast i završava igru.
     */
    private void pokreniTimer60s() {
        gameTimer = new CountDownTimer(TIMER_DURATION_MS, TIMER_TICK_MS) {

            @Override
            public void onTick(long millisUntilFinished) {
                int sekundePreostale = (int) (millisUntilFinished / 1000);
                pbTimer.setProgress(sekundePreostale);
            }

            @Override
            public void onFinish() {
                pbTimer.setProgress(0);
                Toast.makeText(MojBrojActivity.this,
                        getString(R.string.moj_broj_time_up),
                        Toast.LENGTH_SHORT).show();
            }
        }.start();
    }

    // =========================================================================
    // Pomoćne metode
    // =========================================================================

    /** Dodaje token na kraj izgradnje izraza i osvežava polje za prikaz. */
    private void appendToIzraz(String token) {
        izraz.append(token);
        osvežiIzraz();
    }

    /** Sinhronizuje TextInputEditText sa sadržajem StringBuilder-a. */
    private void osvežiIzraz() {
        etIzraz.setText(izraz.toString());
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (gameTimer != null) {
            gameTimer.cancel();
        }
    }
}


package com.example.slagalica.ui.challenge;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.example.slagalica.logic.games.MojBrojLogic;
import com.example.slagalica.util.Constants;
import com.example.slagalica.util.ShakeDetector;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Solo verzija igre "Moj broj" za Izazov — jedan igrač, jedna runda, bez
 * protivnika i bez RTDB. Tok je isti kao u multiplayer verziji
 * ({@code MojBrojActivity}): STOP (ili shake) otkriva traženi broj, drugi
 * STOP (ili shake) otkriva 6 brojeva, zatim igrač slaže izraz preko tastature
 * sa brojevima i operatorima i potvrđuje. Vraća bodove kroz
 * {@link Constants#EXTRA_MY_SCORE}.
 */
public class ChallengeSoloMojBrojActivity extends AppCompatActivity {

    private static final int ROUND_DURATION_MS = 60_000;
    private static final int AUTO_STOP_MS      = 5_000;
    private static final int ROUND_SECONDS     = ROUND_DURATION_MS / 1000;

    // ─── Token model (isti kao u multiplayer verziji) ─────────────────────────
    private static final class Token {
        final String value;
        final int numIndex;
        Token(String v, int idx) { value = v; numIndex = idx; }
        boolean isNumber() { return numIndex >= 0; }
    }
    private enum LastType { EMPTY, NUMBER_OR_CLOSE, OP_OR_OPEN }

    // ─── Views ───────────────────────────────────────────────────────────────
    private TextView        tvScore;
    private TextView        tvTimer;
    private ProgressBar     pbTimer;
    private TextView        tvStatus;
    private TextView        tvTrazeniBroj;
    private TextView        tvIzraz;
    private MaterialButton  btnStopPotvrdi;
    private MaterialButton  btnDel;
    private MaterialButton  btnGiveUp;
    private MaterialButton  btnLeftParen, btnRightParen;
    private MaterialButton  btnPlus, btnMinus, btnMult, btnDiv;
    private MaterialButton[] btnBrojevi;

    // ─── Stanje igre ─────────────────────────────────────────────────────────
    private int     faza = 0; // 0=stop za traženi, 1=stop za brojeve, 2=unos izraza
    private boolean finished = false;

    private int   trazeniBroj;
    private int[] dostupniBrojevi;

    // ─── Token unos ──────────────────────────────────────────────────────────
    private final List<Token> tokens   = new ArrayList<>();
    private final boolean[]   used     = new boolean[6];
    private LastType          lastType = LastType.EMPTY;

    // ─── Timeri / senzori ────────────────────────────────────────────────────
    private ShakeDetector  shakeDetector;
    private CountDownTimer rundaTimer;
    private CountDownTimer autoStopTimer;

    private final Random random = new Random();

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge_solo_mojbroj);

        initViews();
        setupListeners();

        shakeDetector = new ShakeDetector(this, () -> { if (faza < 2) onStopPotvrdiClicked(); });

        tvStatus.setText(R.string.challenge_solo_mojbroj_stop_target);
        pokreniAutoStop();
    }

    @Override protected void onResume() { super.onResume(); if (shakeDetector != null) shakeDetector.start(); }
    @Override protected void onPause()  { super.onPause();  if (shakeDetector != null) shakeDetector.stop();  }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        otkaziTimere();
        if (shakeDetector != null) shakeDetector.stop();
    }

    // =========================================================================
    // Init
    // =========================================================================

    private void initViews() {
        tvScore        = findViewById(R.id.tvScore);
        tvTimer        = findViewById(R.id.tvTimer);
        pbTimer        = findViewById(R.id.pbTimer);
        tvStatus       = findViewById(R.id.tvStatus);
        tvTrazeniBroj  = findViewById(R.id.tvTrazeniBroj);
        tvIzraz        = findViewById(R.id.tvIzraz);
        btnStopPotvrdi = findViewById(R.id.btnStopPotvrdi);
        btnDel         = findViewById(R.id.btnDel);
        btnGiveUp      = findViewById(R.id.btnGiveUp);
        btnLeftParen   = findViewById(R.id.btnLeftParen);
        btnRightParen  = findViewById(R.id.btnRightParen);
        btnPlus        = findViewById(R.id.btnPlus);
        btnMinus       = findViewById(R.id.btnMinus);
        btnMult        = findViewById(R.id.btnMult);
        btnDiv         = findViewById(R.id.btnDiv);

        btnBrojevi = new MaterialButton[]{
            findViewById(R.id.btnNum0), findViewById(R.id.btnNum1),
            findViewById(R.id.btnNum2), findViewById(R.id.btnNum3),
            findViewById(R.id.btnNum4), findViewById(R.id.btnNum5)
        };

        tvScore.setText(getString(R.string.challenge_solo_score, 0));
        tvTimer.setText(getString(R.string.challenge_solo_timer_seconds, ROUND_SECONDS));
        tvTrazeniBroj.setText(R.string.challenge_solo_mojbroj_target_unknown);
        pbTimer.setMax(ROUND_SECONDS);
        pbTimer.setProgress(ROUND_SECONDS);
    }

    private void setupListeners() {
        btnStopPotvrdi.setOnClickListener(v -> onStopPotvrdiClicked());
        btnDel.setOnClickListener(v -> onDel());
        btnGiveUp.setOnClickListener(v -> finishWithScore(0));

        for (int i = 0; i < btnBrojevi.length; i++) {
            final int idx = i;
            btnBrojevi[i].setOnClickListener(v -> onNumPressed(idx));
        }
        btnLeftParen.setOnClickListener(v  -> onOpPressed("("));
        btnRightParen.setOnClickListener(v -> onOpPressed(")"));
        btnPlus.setOnClickListener(v       -> onOpPressed("+"));
        btnMinus.setOnClickListener(v      -> onOpPressed("-"));
        btnMult.setOnClickListener(v       -> onOpPressed("×"));
        btnDiv.setOnClickListener(v        -> onOpPressed("÷"));
    }

    // =========================================================================
    // STOP / POTVRDI logika
    // =========================================================================

    private void onStopPotvrdiClicked() {
        if (finished) return;

        if (faza == 0) {
            otkaziAutoStop();
            trazeniBroj = 100 + random.nextInt(900);
            tvTrazeniBroj.setText(getString(R.string.challenge_solo_mojbroj_target, trazeniBroj));
            tvStatus.setText(R.string.challenge_solo_mojbroj_stop_numbers);
            faza = 1;
            pokreniAutoStop();

        } else if (faza == 1) {
            otkaziAutoStop();
            dostupniBrojevi = generisi6Brojeva();
            for (int i = 0; i < 6; i++) btnBrojevi[i].setText(String.valueOf(dostupniBrojevi[i]));
            tvStatus.setText(R.string.challenge_solo_mojbroj_enter);
            btnStopPotvrdi.setText(R.string.challenge_solo_mojbroj_btn_confirm);
            faza = 2;
            azurirajStanjeDugmadi();
            pokreniTimer60s();

        } else if (faza == 2) {
            submitIzraz();
        }
    }

    private void pokreniAutoStop() {
        otkaziAutoStop();
        autoStopTimer = new CountDownTimer(AUTO_STOP_MS, AUTO_STOP_MS) {
            @Override public void onTick(long ms) {}
            @Override public void onFinish() { onStopPotvrdiClicked(); }
        }.start();
    }

    private void pokreniTimer60s() {
        otkaziRunduTimer();
        rundaTimer = new CountDownTimer(ROUND_DURATION_MS, 1000) {
            @Override
            public void onTick(long ms) {
                int s = (int) Math.ceil(ms / 1000.0);
                tvTimer.setText(getString(R.string.challenge_solo_timer_seconds, s));
                pbTimer.setProgress(s);
            }

            @Override
            public void onFinish() {
                tvTimer.setText(getString(R.string.challenge_solo_timer_seconds, 0));
                pbTimer.setProgress(0);
                submitIzraz();
            }
        }.start();
    }

    // =========================================================================
    // Token — unos
    // =========================================================================

    private void onNumPressed(int idx) {
        if (faza != 2 || used[idx] || lastType == LastType.NUMBER_OR_CLOSE) return;
        used[idx] = true;
        tokens.add(new Token(String.valueOf(dostupniBrojevi[idx]), idx));
        lastType = LastType.NUMBER_OR_CLOSE;
        azurirajIzraz();
    }

    private void onOpPressed(String op) {
        if (faza != 2) return;
        switch (op) {
            case "(":
                if (lastType == LastType.NUMBER_OR_CLOSE) return;
                tokens.add(new Token("(", -1));
                lastType = LastType.OP_OR_OPEN;
                break;
            case ")":
                if (lastType != LastType.NUMBER_OR_CLOSE) return;
                tokens.add(new Token(")", -1));
                lastType = LastType.NUMBER_OR_CLOSE;
                break;
            default:
                if (lastType != LastType.NUMBER_OR_CLOSE) return;
                tokens.add(new Token(op, -1));
                lastType = LastType.OP_OR_OPEN;
                break;
        }
        azurirajIzraz();
    }

    private void onDel() {
        if (tokens.isEmpty()) return;
        Token last = tokens.remove(tokens.size() - 1);
        if (last.isNumber()) used[last.numIndex] = false;
        if (tokens.isEmpty()) {
            lastType = LastType.EMPTY;
        } else {
            String prev = tokens.get(tokens.size() - 1).value;
            lastType = (prev.equals("(") || prev.equals("+") || prev.equals("-")
                    || prev.equals("×") || prev.equals("÷"))
                    ? LastType.OP_OR_OPEN
                    : LastType.NUMBER_OR_CLOSE;
        }
        azurirajIzraz();
    }

    private void azurirajIzraz() {
        if (tokens.isEmpty()) {
            tvIzraz.setText("");
        } else {
            StringBuilder sb = new StringBuilder();
            for (Token t : tokens) sb.append(t.value);
            tvIzraz.setText(sb.toString());
        }
        azurirajStanjeDugmadi();
    }

    private void azurirajStanjeDugmadi() {
        boolean canNum = (lastType != LastType.NUMBER_OR_CLOSE);
        boolean canOp  = (lastType == LastType.NUMBER_OR_CLOSE);
        for (int i = 0; i < 6; i++) btnBrojevi[i].setEnabled(faza == 2 && canNum && !used[i]);
        btnLeftParen.setEnabled(faza == 2 && canNum);
        btnRightParen.setEnabled(faza == 2 && canOp);
        btnPlus.setEnabled(faza == 2 && canOp);
        btnMinus.setEnabled(faza == 2 && canOp);
        btnMult.setEnabled(faza == 2 && canOp);
        btnDiv.setEnabled(faza == 2 && canOp);
        btnDel.setEnabled(faza == 2 && !tokens.isEmpty());
    }

    // =========================================================================
    // Submit i bodovanje
    // =========================================================================

    private void submitIzraz() {
        if (finished) return;

        StringBuilder sb = new StringBuilder();
        for (Token t : tokens) sb.append(t.value);
        String expr = sb.toString();

        MojBrojLogic.IzrazRezultat myResult = new MojBrojLogic.IzrazRezultat();
        myResult.validan = false;
        if (!expr.isEmpty() && dostupniBrojevi != null) {
            myResult = MojBrojLogic.evaluate(expr, dostupniBrojevi);
        }

        // Solo bodovanje kroz istu logiku kao multiplayer: igrač je "prvi igrač"
        // i aktivan je (njegova je runda), a protivnika nema — nevalidan rezultat.
        // Time se dobija 10 za tačan pogodak, 5 za validan-ali-netačan izraz, 0 inače.
        MojBrojLogic.IzrazRezultat noOpponent = new MojBrojLogic.IzrazRezultat();
        noOpponent.validan = false;

        int score = MojBrojLogic.izracunajBodovanje(trazeniBroj, myResult, noOpponent, true)[0];
        tvScore.setText(getString(R.string.challenge_solo_score, score));
        finishWithScore(score);
    }

    private void finishWithScore(int score) {
        if (finished) return;
        finished = true;
        otkaziTimere();
        Intent result = new Intent();
        result.putExtra(Constants.EXTRA_MY_SCORE, score);
        setResult(RESULT_OK, result);
        finish();
    }

    // =========================================================================
    // Generisanje 6 brojeva (pozicije 0-3 = jednocifreni, 4 = mali, 5 = veliki)
    // =========================================================================

    private int[] generisi6Brojeva() {
        int[] maliSet   = {10, 15, 20};
        int[] velikiSet = {25, 50, 75, 100};
        List<Integer> jed = new ArrayList<>();
        for (int i = 0; i < 4; i++) jed.add(1 + random.nextInt(9));
        Collections.shuffle(jed, random);
        int[] result = new int[6];
        for (int i = 0; i < 4; i++) result[i] = jed.get(i);
        result[4] = maliSet[random.nextInt(maliSet.length)];
        result[5] = velikiSet[random.nextInt(velikiSet.length)];
        return result;
    }

    // =========================================================================
    // Timeri
    // =========================================================================

    private void otkaziAutoStop()   { if (autoStopTimer != null) { autoStopTimer.cancel(); autoStopTimer = null; } }
    private void otkaziRunduTimer() { if (rundaTimer    != null) { rundaTimer.cancel();    rundaTimer    = null; } }
    private void otkaziTimere()     { otkaziAutoStop(); otkaziRunduTimer(); }
}

package com.example.slagalica.ui.games;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.slagalica.R;
import com.google.android.material.button.MaterialButton;


public class SpojniceActivity extends AppCompatActivity {

    private static final int TURN_TIME_MS  = 30_000;
    private static final int BLINK_MS      = 700;
    private static final int TRANSITION_MS = 1_800;

    private static final String[] CRITERION = {
        "Povežite države sa prestonicama",
        "Povežite životinje sa zvukovima"
    };
    private static final String[][] LEFT  = {
        {"Srbija",  "Francuska", "Japan",   "Brazil",    "Australija"},
        {"Pas",     "Mačka",     "Krava",   "Ptica",     "Žaba"}
    };
    private static final String[][] RIGHT = {
        {"Pariz",   "Beograd",   "Brazilija", "Kanbera", "Tokio"},
        {"Cvrkuće", "Reži",      "Muče",      "Kreče",   "Mijauče"}
    };
    private static final int[][] CORRECT = {
        {1, 0, 4, 2, 3},
        {1, 4, 2, 0, 3}
    };

    // Views
    private TextView         tvRoundLabel;
    private TextView         tvPlayerName;
    private TextView         tvScore;
    private TextView         tvTimer;
    private TextView         tvCriterion;
    private ProgressBar      pbTimer;
    private MaterialButton[] leftBtns  = new MaterialButton[5];
    private MaterialButton[] rightBtns = new MaterialButton[5];

    // Tint liste
    private ColorStateList defaultTint;
    private ColorStateList selectedTint;
    private ColorStateList connectedTint;
    private ColorStateList wrongTint;
    private ColorStateList attemptedTint; // siv — igrač 1 iskoristio pokušaj, netačno

    // Stanje igre
    private int     round    = 0;
    private boolean isPhaseB = false;
    private boolean[] connected = new boolean[5]; // tačno spojen
    private boolean[] attempted = new boolean[5]; // igrač 1 pokušao ali pogrešio
    private int       selectedLeft = -1;
    private int[]     scores = {0, 0};  // [igrač1, igrač2]

    private CountDownTimer turnTimer;
    private final Handler  handler = new Handler(Looper.getMainLooper());

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spojnice);
        initViews();
        setupListeners();
        startPhase(0, false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (turnTimer != null) turnTimer.cancel();
    }

    // =========================================================================
    // Inicijalizacija
    // =========================================================================

    private void initViews() {
        tvRoundLabel = findViewById(R.id.tvRoundLabel);
        tvPlayerName = findViewById(R.id.tvPlayerName);
        tvScore      = findViewById(R.id.tvScore);
        tvTimer      = findViewById(R.id.tvTimer);
        tvCriterion  = findViewById(R.id.tvCriterion);
        pbTimer      = findViewById(R.id.pbTimer);

        leftBtns[0]  = findViewById(R.id.btnLeft0);
        leftBtns[1]  = findViewById(R.id.btnLeft1);
        leftBtns[2]  = findViewById(R.id.btnLeft2);
        leftBtns[3]  = findViewById(R.id.btnLeft3);
        leftBtns[4]  = findViewById(R.id.btnLeft4);
        rightBtns[0] = findViewById(R.id.btnRight0);
        rightBtns[1] = findViewById(R.id.btnRight1);
        rightBtns[2] = findViewById(R.id.btnRight2);
        rightBtns[3] = findViewById(R.id.btnRight3);
        rightBtns[4] = findViewById(R.id.btnRight4);

        defaultTint   = leftBtns[0].getBackgroundTintList();
        selectedTint  = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.spojnice_item_selected));
        connectedTint = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.spojnice_item_connected));
        wrongTint     = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.spojnice_item_wrong));
        attemptedTint = ColorStateList.valueOf(0xFFBDBDBD); // sivo
    }

    private void setupListeners() {
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            leftBtns[i].setOnClickListener(v  -> onLeftClicked(idx));
            rightBtns[i].setOnClickListener(v -> onRightClicked(idx));
        }
        findViewById(R.id.btnGiveUp).setOnClickListener(v -> finish());
    }

    // =========================================================================
    // Upravljanje fazama
    // =========================================================================

    /**
     * Pokreće fazu igre.
     * Runda 0 faza A → Igrač 1 igra; faza B → Igrač 2 igra preostale.
     * Runda 1 faza A → Igrač 2 igra; faza B → Igrač 1 igra preostale.
     */
    private void startPhase(int roundIdx, boolean phaseB) {
        round        = roundIdx;
        isPhaseB     = phaseB;
        selectedLeft = -1;

        if (!phaseB) {
            connected = new boolean[5];
            attempted = new boolean[5];
        }

        tvRoundLabel.setText(getString(R.string.spojnice_round_label, roundIdx + 1));
        tvCriterion.setText(getString(R.string.spojnice_criterion_label, CRITERION[roundIdx]));
        tvScore.setText(getString(R.string.spojnice_scores_label, scores[0], scores[1]));
        tvPlayerName.setText("▶ " + getString(R.string.spojnice_player_label, activePlayerName()));

        setupButtonsForPhase(roundIdx);
        startTurnTimer();
    }

    /** Postavlja dugmad: spojeni parovi zeleni+disabled, nespojen default+enabled. */
    private void setupButtonsForPhase(int roundIdx) {
        boolean[] rightUsed = new boolean[5];
        for (int i = 0; i < 5; i++) {
            if (connected[i]) rightUsed[CORRECT[roundIdx][i]] = true;
        }
        for (int i = 0; i < 5; i++) {
            leftBtns[i].setText(LEFT[roundIdx][i]);
            rightBtns[i].setText(RIGHT[roundIdx][i]);
            if (connected[i]) {
                leftBtns[i].setBackgroundTintList(connectedTint);
                leftBtns[i].setEnabled(false);
            } else if (attempted[i] && !isPhaseB) {
                // Faza A: pokušano ali netačno — prikaži sivo i onemogući
                leftBtns[i].setBackgroundTintList(attemptedTint);
                leftBtns[i].setEnabled(false);
            } else {
                // Faza B re-omogućava attempted (ali ne i connected) stavke
                leftBtns[i].setBackgroundTintList(defaultTint);
                leftBtns[i].setEnabled(true);
            }
            if (rightUsed[i]) {
                rightBtns[i].setBackgroundTintList(connectedTint);
                rightBtns[i].setEnabled(false);
            } else {
                rightBtns[i].setBackgroundTintList(defaultTint);
                rightBtns[i].setEnabled(true);
            }
        }
    }

    private void startTurnTimer() {
        pbTimer.setMax(30);
        pbTimer.setProgress(30);
        tvTimer.setText(getString(R.string.spojnice_time_left, 30));

        turnTimer = new CountDownTimer(TURN_TIME_MS, 1_000) {
            @Override public void onTick(long ms) {
                int s = (int) (ms / 1000) + 1;
                pbTimer.setProgress(s);
                tvTimer.setText(getString(R.string.spojnice_time_left, s));
            }
            @Override public void onFinish() {
                pbTimer.setProgress(0);
                tvTimer.setText(getString(R.string.spojnice_time_left, 0));
                onTurnEnd();
            }
        }.start();
    }

    /** Poziva se kada igrač poveže sve parove ili kada istekne vreme. */
    private void onTurnEnd() {
        disableAll();

        if (isPhaseB || allConnected()) {
            // Faza B (ili sve spojeno) — prelaz na sledeću rundu ili kraj igre
            handler.postDelayed(this::advanceRound, TRANSITION_MS);
        } else {
            // Faza A gotova — preostali parovi idu na fazu B
            if (countRemaining() == 0) {
                handler.postDelayed(this::advanceRound, TRANSITION_MS);
            } else {
                String nextPlayer = (round == 0)
                        ? getString(R.string.spojnice_player2)
                        : getString(R.string.spojnice_player1);
                Toast.makeText(this,
                        getString(R.string.spojnice_transition, nextPlayer),
                        Toast.LENGTH_LONG).show();
                handler.postDelayed(() -> startPhase(round, true), TRANSITION_MS);
            }
        }
    }

    private void advanceRound() {
        Toast.makeText(this, getString(R.string.spojnice_round_end), Toast.LENGTH_SHORT).show();
        if (round < 1) {
            handler.postDelayed(() -> startPhase(1, false), TRANSITION_MS);
        } else {
            endGame();
        }
    }

    private void endGame() {
        Toast.makeText(this,
                getString(R.string.spojnice_game_end, scores[0], scores[1]),
                Toast.LENGTH_LONG).show();
        finish();
    }

    // =========================================================================
    // Klikovi
    // =========================================================================

    private void onLeftClicked(int idx) {
        if (connected[idx]) return;
        if (selectedLeft != -1 && selectedLeft != idx) {
            leftBtns[selectedLeft].setBackgroundTintList(defaultTint);
        }
        if (selectedLeft == idx) {
            leftBtns[idx].setBackgroundTintList(defaultTint);
            selectedLeft = -1;
        } else {
            selectedLeft = idx;
            leftBtns[idx].setBackgroundTintList(selectedTint);
        }
    }

    private void onRightClicked(int rightIdx) {
        if (selectedLeft == -1) return;
        // Proveri da li je desni već spojen
        for (int i = 0; i < 5; i++) {
            if (connected[i] && CORRECT[round][i] == rightIdx) return;
        }

        int leftIdx = selectedLeft;
        selectedLeft = -1;
        leftBtns[leftIdx].setBackgroundTintList(defaultTint);

        if (rightIdx == CORRECT[round][leftIdx]) {
            // Tačno
            scores[activePlayerIdx()] += 2;
            connected[leftIdx] = true;
            leftBtns[leftIdx].setBackgroundTintList(connectedTint);
            leftBtns[leftIdx].setEnabled(false);
            rightBtns[rightIdx].setBackgroundTintList(connectedTint);
            rightBtns[rightIdx].setEnabled(false);
            tvScore.setText(getString(R.string.spojnice_scores_label, scores[0], scores[1]));
            Toast.makeText(this, getString(R.string.spojnice_correct), Toast.LENGTH_SHORT).show();
            boolean turnDone = isPhaseB ? allConnected() : allAttempted();
            if (turnDone) {
                if (turnTimer != null) turnTimer.cancel();
                onTurnEnd();
            }
        } else {
            // Netačno — blink
            leftBtns[leftIdx].setBackgroundTintList(wrongTint);
            rightBtns[rightIdx].setBackgroundTintList(wrongTint);
            Toast.makeText(this, getString(R.string.spojnice_wrong), Toast.LENGTH_SHORT).show();
            handler.postDelayed(() -> {
                if (!isPhaseB) {
                    // Faza A: levo zaključano (sivo), desno slobodno za igrača 2
                    attempted[leftIdx] = true;
                    leftBtns[leftIdx].setBackgroundTintList(attemptedTint);
                    leftBtns[leftIdx].setEnabled(false);
                    rightBtns[rightIdx].setBackgroundTintList(defaultTint);
                    if (allAttempted()) {
                        if (turnTimer != null) turnTimer.cancel();
                        onTurnEnd();
                    }
                } else {
                    // Faza B: igrač 2 može pokušati opet
                    leftBtns[leftIdx].setBackgroundTintList(defaultTint);
                    rightBtns[rightIdx].setBackgroundTintList(defaultTint);
                }
            }, BLINK_MS);
        }
    }

    // =========================================================================
    // Pomoćne metode
    // =========================================================================

    private String activePlayerName() {
        boolean p1 = (round == 0 && !isPhaseB) || (round == 1 && isPhaseB);
        return p1 ? getString(R.string.spojnice_player1) : getString(R.string.spojnice_player2);
    }

    private int activePlayerIdx() {
        boolean p1 = (round == 0 && !isPhaseB) || (round == 1 && isPhaseB);
        return p1 ? 0 : 1;
    }

    private boolean allConnected() {
        for (boolean c : connected) if (!c) return false;
        return true;
    }

    private boolean allAttempted() {
        for (int i = 0; i < 5; i++) if (!connected[i] && !attempted[i]) return false;
        return true;
    }

    private int countRemaining() {
        int n = 0;
        for (boolean c : connected) if (!c) n++;
        return n;
    }

    private void disableAll() {
        for (int i = 0; i < 5; i++) {
            leftBtns[i].setEnabled(false);
            rightBtns[i].setEnabled(false);
        }
    }
}

package com.example.slagalica.ui.games;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.example.slagalica.logic.games.SkockoLogic;
import com.example.slagalica.util.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Igra "Skočko" — dve runde za istog igrača.
 *
 * Za prelaz na 1v1: protivnička faza (OPPONENT_TURN) se preseli na drugi
 * uređaj; secret i attempt-rows se šalju kroz MatchRepository.
 */
public class SkockoActivity extends AppCompatActivity {

    private static final int MAIN_SECONDS = 30;
    /** Da li je igra pokrenuta kao deo solo runde Izazova (vidi Constants.EXTRA_SOLO). */
    private boolean isSolo;
    private static final int OPP_SECONDS  = 10;
    private static final int NUM_ROUNDS   = 2;

    // Drawable IDs za svaki simbol (indeks odgovara SkockoLogic konstantama 0-5)
    private static final int[] SYMBOL_DRAW = {
        R.drawable.ic_symbol_smiley,
        R.drawable.ic_symbol_square,
        R.drawable.ic_symbol_circle,
        R.drawable.ic_symbol_heart,
        R.drawable.ic_symbol_triangle,
        R.drawable.ic_symbol_star
    };

    private static final int HINT_DRAW_RED    = R.drawable.bg_skocko_hint_red;
    private static final int HINT_DRAW_YELLOW = R.drawable.bg_skocko_hint_yellow;
    private static final int HINT_DRAW_EMPTY  = R.drawable.bg_skocko_hint_empty;

    // ── Views ─────────────────────────────────────────────────────────────────
    private TextView     tvRound, tvScore, tvInstruction, tvTimer;
    private ProgressBar  pbTimer;
    private ImageView[][] slotViews;   // [row 0-5][col 0-3]
    private android.view.View[][] hintViews;  // [row 0-5][hint 0-3]
    private ImageView[]  opponentSlots;
    private android.view.View[]  opponentHints;
    private ImageView[]  solutionSlots;
    private LinearLayout rowOpponentGuess;

    // ── Game state ────────────────────────────────────────────────────────────
    private enum Phase { MAIN_TURN, OPPONENT_TURN, ROUND_OVER }

    private Phase        phase;
    private int[]        secret;
    private List<Integer> currentInput;
    private int          attemptsDone;
    private int          score;
    private int          currentRound;
    private final int[]  roundScores = new int[NUM_ROUNDS];
    private CountDownTimer timer;
    private final Random random = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_skocko);
        isSolo = getIntent().getBooleanExtra(Constants.EXTRA_SOLO, false);
        initViews();
        startRound(1);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelTimer();
    }

    // ── Initialisation ────────────────────────────────────────────────────────

    private void initViews() {
        tvRound       = findViewById(R.id.tvRound);
        tvScore       = findViewById(R.id.tvScore);
        tvInstruction = findViewById(R.id.tvInstruction);
        tvTimer       = findViewById(R.id.tvTimer);
        pbTimer       = findViewById(R.id.pbTimer);
        rowOpponentGuess = findViewById(R.id.rowOpponentGuess);

        slotViews = new ImageView[][]{
            { iv(R.id.ivRow1Col1), iv(R.id.ivRow1Col2), iv(R.id.ivRow1Col3), iv(R.id.ivRow1Col4) },
            { iv(R.id.ivRow2Col1), iv(R.id.ivRow2Col2), iv(R.id.ivRow2Col3), iv(R.id.ivRow2Col4) },
            { iv(R.id.ivRow3Col1), iv(R.id.ivRow3Col2), iv(R.id.ivRow3Col3), iv(R.id.ivRow3Col4) },
            { iv(R.id.ivRow4Col1), iv(R.id.ivRow4Col2), iv(R.id.ivRow4Col3), iv(R.id.ivRow4Col4) },
            { iv(R.id.ivRow5Col1), iv(R.id.ivRow5Col2), iv(R.id.ivRow5Col3), iv(R.id.ivRow5Col4) },
            { iv(R.id.ivRow6Col1), iv(R.id.ivRow6Col2), iv(R.id.ivRow6Col3), iv(R.id.ivRow6Col4) },
        };

        hintViews = new android.view.View[][]{
            { v(R.id.vRow1Hint1), v(R.id.vRow1Hint2), v(R.id.vRow1Hint3), v(R.id.vRow1Hint4) },
            { v(R.id.vRow2Hint1), v(R.id.vRow2Hint2), v(R.id.vRow2Hint3), v(R.id.vRow2Hint4) },
            { v(R.id.vRow3Hint1), v(R.id.vRow3Hint2), v(R.id.vRow3Hint3), v(R.id.vRow3Hint4) },
            { v(R.id.vRow4Hint1), v(R.id.vRow4Hint2), v(R.id.vRow4Hint3), v(R.id.vRow4Hint4) },
            { v(R.id.vRow5Hint1), v(R.id.vRow5Hint2), v(R.id.vRow5Hint3), v(R.id.vRow5Hint4) },
            { v(R.id.vRow6Hint1), v(R.id.vRow6Hint2), v(R.id.vRow6Hint3), v(R.id.vRow6Hint4) },
        };

        opponentSlots = new ImageView[]{
            iv(R.id.ivOpponentCol1), iv(R.id.ivOpponentCol2),
            iv(R.id.ivOpponentCol3), iv(R.id.ivOpponentCol4)
        };
        opponentHints = new android.view.View[]{
            v(R.id.vOpponentHint1), v(R.id.vOpponentHint2),
            v(R.id.vOpponentHint3), v(R.id.vOpponentHint4)
        };
        solutionSlots = new ImageView[]{
            iv(R.id.ivSolutionCol1), iv(R.id.ivSolutionCol2),
            iv(R.id.ivSolutionCol3), iv(R.id.ivSolutionCol4)
        };

        int[] symbolBtnIds = {
            R.id.btnSymbolSmiley, R.id.btnSymbolSquare, R.id.btnSymbolCircle,
            R.id.btnSymbolHeart,  R.id.btnSymbolTriangle, R.id.btnSymbolStar
        };
        for (int i = 0; i < symbolBtnIds.length; i++) {
            final int sym = i;
            findViewById(symbolBtnIds[i]).setOnClickListener(v2 -> onSymbolTapped(sym));
        }

        findViewById(R.id.btnClear).setOnClickListener(v2 -> onClear());
        findViewById(R.id.btnGiveUp).setOnClickListener(v2 -> onGiveUp());
    }

    private ImageView iv(int id) { return findViewById(id); }
    private android.view.View v(int id) { return findViewById(id); }

    // ── Round lifecycle ────────────────────────────────────────────────────────

    private void startRound(int round) {
        currentRound = round;
        secret       = SkockoLogic.randomSecret(random);
        currentInput = new ArrayList<>();
        attemptsDone = 0;
        score        = 0;
        phase        = Phase.MAIN_TURN;

        tvRound.setText(getString(R.string.skocko_round, round));
        updateScoreDisplay();
        resetBoard();
        updateInstruction();
        startTimer(MAIN_SECONDS);
    }

    private void resetBoard() {
        for (int r = 0; r < SkockoLogic.MAX_ATTEMPTS; r++) {
            for (int c = 0; c < SkockoLogic.COMBO_LEN; c++) {
                slotViews[r][c].setImageDrawable(null);
                hintViews[r][c].setBackgroundResource(HINT_DRAW_EMPTY);
            }
        }
        for (int c = 0; c < SkockoLogic.COMBO_LEN; c++) {
            opponentSlots[c].setImageDrawable(null);
            opponentHints[c].setBackgroundResource(HINT_DRAW_EMPTY);
            solutionSlots[c].setImageDrawable(null);
        }
        rowOpponentGuess.setAlpha(0.35f);
    }

    // ── Input handling ────────────────────────────────────────────────────────

    private void onSymbolTapped(int symbol) {
        if (phase == Phase.ROUND_OVER) return;
        if (currentInput.size() >= SkockoLogic.COMBO_LEN) return;

        currentInput.add(symbol);
        refreshActiveRow();

        if (currentInput.size() == SkockoLogic.COMBO_LEN) {
            submitGuess();
        }
    }

    private void onClear() {
        if (phase == Phase.ROUND_OVER) return;
        if (currentInput.isEmpty()) return;
        currentInput.remove(currentInput.size() - 1);
        refreshActiveRow();
    }

    private void onGiveUp() {
        if (phase == Phase.ROUND_OVER) return;
        cancelTimer();
        if (phase == Phase.MAIN_TURN) {
            enterOpponentPhase();
        } else {
            endRound();
        }
    }

    /** Syncs the ImageViews of the active (currently-being-built) row with currentInput. */
    private void refreshActiveRow() {
        ImageView[] slots = (phase == Phase.OPPONENT_TURN)
                ? opponentSlots
                : slotViews[attemptsDone];
        for (int c = 0; c < SkockoLogic.COMBO_LEN; c++) {
            if (c < currentInput.size()) {
                slots[c].setImageResource(SYMBOL_DRAW[currentInput.get(c)]);
            } else {
                slots[c].setImageDrawable(null);
            }
        }
    }

    private void submitGuess() {
        int[] guess = new int[SkockoLogic.COMBO_LEN];
        for (int i = 0; i < SkockoLogic.COMBO_LEN; i++) guess[i] = currentInput.get(i);

        int[] hints = SkockoLogic.computeHints(guess, secret);
        currentInput.clear();

        if (phase == Phase.MAIN_TURN) {
            applyHints(hintViews[attemptsDone], hints);
            attemptsDone++;

            if (SkockoLogic.isCorrect(hints)) {
                score += SkockoLogic.mainScore(attemptsDone - 1);
                updateScoreDisplay();
                cancelTimer();
                endRound();
            } else if (attemptsDone >= SkockoLogic.MAX_ATTEMPTS) {
                cancelTimer();
                enterOpponentPhase();
            } else {
                updateInstruction();
            }
        } else { // OPPONENT_TURN
            applyHints(opponentHints, hints);
            if (SkockoLogic.isCorrect(hints)) {
                score += SkockoLogic.OPPONENT_SCORE;
                updateScoreDisplay();
            }
            cancelTimer();
            endRound();
        }
    }

    // ── Phase transitions ─────────────────────────────────────────────────────

    private void enterOpponentPhase() {
        phase        = Phase.OPPONENT_TURN;
        currentInput = new ArrayList<>();
        rowOpponentGuess.setAlpha(1.0f);
        updateInstruction();
        startTimer(OPP_SECONDS);
    }

    private void endRound() {
        phase = Phase.ROUND_OVER;
        cancelTimer();
        revealSolution();
        roundScores[currentRound - 1] = score;

        solutionSlots[0].postDelayed(() -> {
            if (currentRound < NUM_ROUNDS) {
                showRoundSummary();
            } else {
                showGameOver();
            }
        }, 1200);
    }

    // ── Hint & solution display ────────────────────────────────────────────────

    private void applyHints(android.view.View[] views, int[] hints) {
        for (int i = 0; i < hints.length; i++) {
            int drawable;
            switch (hints[i]) {
                case SkockoLogic.HINT_RED:    drawable = HINT_DRAW_RED;    break;
                case SkockoLogic.HINT_YELLOW: drawable = HINT_DRAW_YELLOW; break;
                default:                      drawable = HINT_DRAW_EMPTY;  break;
            }
            views[i].setBackgroundResource(drawable);
        }
    }

    private void revealSolution() {
        for (int c = 0; c < SkockoLogic.COMBO_LEN; c++) {
            solutionSlots[c].setImageResource(SYMBOL_DRAW[secret[c]]);
        }
    }

    // ── Timer ─────────────────────────────────────────────────────────────────

    private void startTimer(int seconds) {
        cancelTimer();
        pbTimer.setMax(seconds);
        pbTimer.setProgress(seconds);
        tvTimer.setText(getString(R.string.skocko_time_left, seconds));

        timer = new CountDownTimer((long) seconds * 1000, 1000) {
            @Override
            public void onTick(long ms) {
                int s = (int) (ms / 1000) + 1;
                pbTimer.setProgress(s);
                tvTimer.setText(getString(R.string.skocko_time_left, s));
            }

            @Override
            public void onFinish() {
                pbTimer.setProgress(0);
                tvTimer.setText(getString(R.string.skocko_time_left, 0));
                onTimerExpired();
            }
        }.start();
    }

    private void cancelTimer() {
        if (timer != null) { timer.cancel(); timer = null; }
    }

    private void onTimerExpired() {
        if (phase == Phase.MAIN_TURN) {
            enterOpponentPhase();
        } else if (phase == Phase.OPPONENT_TURN) {
            endRound();
        }
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private void updateScoreDisplay() {
        tvScore.setText(getString(R.string.skocko_score, score));
    }

    private void updateInstruction() {
        if (phase == Phase.MAIN_TURN) {
            tvInstruction.setText(getString(R.string.skocko_phase_main, attemptsDone + 1));
        } else if (phase == Phase.OPPONENT_TURN) {
            tvInstruction.setText(R.string.skocko_phase_opponent);
        }
    }

    // ── Summary dialogs ───────────────────────────────────────────────────────

    private void showRoundSummary() {
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.skocko_round_end_title, currentRound))
            .setMessage(getString(R.string.skocko_round_end_msg, roundScores[currentRound - 1]))
            .setCancelable(false)
            .setPositiveButton(R.string.skocko_btn_next_round, (d, w) -> startRound(currentRound + 1))
            .show();
    }

    private void showGameOver() {
        int total = roundScores[0] + roundScores[1];
        new AlertDialog.Builder(this)
            .setTitle(R.string.skocko_game_over_title)
            .setMessage(getString(R.string.skocko_game_over_msg, roundScores[0], roundScores[1], total))
            .setCancelable(false)
            .setPositiveButton(R.string.skocko_btn_finish, (d, w) -> {
                if (isSolo) {
                    Intent result = new Intent();
                    result.putExtra(Constants.EXTRA_MY_SCORE, total);
                    setResult(RESULT_OK, result);
                }
                finish();
            })
            .show();
    }
}

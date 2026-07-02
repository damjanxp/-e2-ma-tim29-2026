package com.example.slagalica.ui.challenge;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.slagalica.R;
import com.example.slagalica.data.model.AsocijacijePuzzle;
import com.example.slagalica.logic.games.AsocijacijeLogic;
import com.example.slagalica.util.Constants;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

/**
 * Solo verzija igre "Asocijacije" za Izazov — jedan igrač, bez protivnika,
 * jedna runda. Vraća broj bodova pozivaocu kroz {@link Constants#EXTRA_MY_SCORE}.
 */
public class ChallengeSoloAsocijacijeActivity extends AppCompatActivity {

    private static final int TURN_SECONDS = 120;
    private static final int NUM_COLS     = AsocijacijeLogic.NUM_COLS;
    private static final int NUM_ROWS     = AsocijacijeLogic.NUM_ROWS;

    // ── Views ─────────────────────────────────────────────────────────────────
    private TextView        tvScore, tvTimer;
    private ProgressBar     pbTimer;
    private MaterialButton[][] clueButtons;
    private MaterialButton[] colButtons;
    private MaterialButton  finalButton;

    // ── Game state ────────────────────────────────────────────────────────────
    private AsocijacijePuzzle   puzzle;
    private boolean[][]         revealed;
    private boolean[]           colSolved;
    private boolean             finalSolved;
    private boolean             mustReveal;
    private int                 score;
    private CountDownTimer      timer;

    private static final String[] COL_LETTERS = {"A", "B", "C", "D"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge_solo_asocijacije);
        initViews();
        startRound();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelTimer();
    }

    // ── Initialisation ────────────────────────────────────────────────────────

    private void initViews() {
        tvScore  = findViewById(R.id.tvScore);
        tvTimer  = findViewById(R.id.tvTimer);
        pbTimer  = findViewById(R.id.pbTimer);

        clueButtons = new MaterialButton[][]{
            {
                findViewById(R.id.btnA1), findViewById(R.id.btnA2),
                findViewById(R.id.btnA3), findViewById(R.id.btnA4)
            },
            {
                findViewById(R.id.btnB1), findViewById(R.id.btnB2),
                findViewById(R.id.btnB3), findViewById(R.id.btnB4)
            },
            {
                findViewById(R.id.btnC1), findViewById(R.id.btnC2),
                findViewById(R.id.btnC3), findViewById(R.id.btnC4)
            },
            {
                findViewById(R.id.btnD1), findViewById(R.id.btnD2),
                findViewById(R.id.btnD3), findViewById(R.id.btnD4)
            }
        };

        colButtons = new MaterialButton[]{
            findViewById(R.id.btnA),
            findViewById(R.id.btnB),
            findViewById(R.id.btnC),
            findViewById(R.id.btnD)
        };

        finalButton = findViewById(R.id.btnCenter);

        findViewById(R.id.btnGiveUp).setOnClickListener(v -> endRound(true));
    }

    // ── Round lifecycle ────────────────────────────────────────────────────────

    private void startRound() {
        puzzle       = AsocijacijePuzzle.samplePuzzles()[0];
        revealed     = new boolean[NUM_COLS][NUM_ROWS];
        colSolved    = new boolean[NUM_COLS];
        finalSolved  = false;
        mustReveal   = false;
        score        = 0;

        updateScoreDisplay();
        bindBoard();
        startTimer();
    }

    private void endRound(boolean gaveUp) {
        cancelTimer();
        showGameOver();
    }

    // ── Board binding ─────────────────────────────────────────────────────────

    private void bindBoard() {
        for (int col = 0; col < NUM_COLS; col++) {
            for (int row = 0; row < NUM_ROWS; row++) {
                final int c = col, r = row;
                MaterialButton btn = clueButtons[col][row];
                btn.setText("?");
                btn.setEnabled(true);
                setButtonTint(btn, null);
                btn.setOnClickListener(v -> onClueTapped(c, r));
            }

            MaterialButton colBtn = colButtons[col];
            colBtn.setText(COL_LETTERS[col] + " ?");
            colBtn.setEnabled(true);
            setButtonTint(colBtn, null);
            final int fc = col;
            colBtn.setOnClickListener(v -> onColSolutionTapped(fc));
        }

        finalButton.setText(getString(R.string.asoc_final_label));
        finalButton.setEnabled(true);
        setButtonTint(finalButton, null);
        finalButton.setOnClickListener(v -> onFinalTapped());
    }

    // ── Interaction handlers ──────────────────────────────────────────────────

    private void onClueTapped(int col, int row) {
        if (revealed[col][row]) return;

        revealed[col][row] = true;
        mustReveal = false;

        MaterialButton btn = clueButtons[col][row];
        btn.setText(puzzle.getClue(col, row));
        btn.setEnabled(false);
        setButtonTint(btn, ContextCompat.getColor(this, R.color.asoc_cell_revealed));
    }

    private void onColSolutionTapped(int col) {
        if (colSolved[col]) return;
        if (mustReveal) {
            Snackbar.make(finalButton, R.string.asoc_must_reveal_first, Snackbar.LENGTH_SHORT).show();
            return;
        }
        showGuessDialog(
            getString(R.string.asoc_guess_col_title, COL_LETTERS[col]),
            answer -> {
                String correct = puzzle.getColSolution(col).trim().toUpperCase();
                if (answer.trim().toUpperCase().equals(correct)) {
                    int pts = AsocijacijeLogic.columnScore(countRevealed(col));
                    score += pts;
                    colSolved[col] = true;
                    mustReveal = false;
                    updateScoreDisplay();
                    markColumnSolved(col);
                    Snackbar.make(finalButton,
                        getString(R.string.asoc_correct_col, pts, COL_LETTERS[col]),
                        Snackbar.LENGTH_SHORT).show();
                } else {
                    mustReveal = true;
                    Snackbar.make(finalButton, R.string.asoc_wrong, Snackbar.LENGTH_SHORT).show();
                }
            }
        );
    }

    private void onFinalTapped() {
        if (finalSolved) return;
        if (mustReveal) {
            Snackbar.make(finalButton, R.string.asoc_must_reveal_first, Snackbar.LENGTH_SHORT).show();
            return;
        }
        showGuessDialog(
            getString(R.string.asoc_guess_final_title),
            answer -> {
                String correct = puzzle.getFinalSolution().trim().toUpperCase();
                if (answer.trim().toUpperCase().equals(correct)) {
                    int pts = AsocijacijeLogic.finalScore(revealedCountsPerCol(), colSolved);
                    score += pts;
                    finalSolved = true;
                    updateScoreDisplay();
                    setButtonTint(finalButton,
                        ContextCompat.getColor(this, R.color.asoc_final_solved));
                    finalButton.setText(puzzle.getFinalSolution());
                    finalButton.setEnabled(false);
                    Snackbar.make(finalButton,
                        getString(R.string.asoc_correct_final, pts),
                        Snackbar.LENGTH_SHORT).show();
                    // Short delay so the player can read the snackbar before the summary
                    finalButton.postDelayed(() -> endRound(false), 1500);
                } else {
                    mustReveal = true;
                    Snackbar.make(finalButton, R.string.asoc_wrong, Snackbar.LENGTH_SHORT).show();
                }
            }
        );
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private void markColumnSolved(int col) {
        setButtonTint(colButtons[col],
            ContextCompat.getColor(this, R.color.asoc_col_solved));
        colButtons[col].setText(puzzle.getColSolution(col));
        colButtons[col].setEnabled(false);
    }

    private void updateScoreDisplay() {
        tvScore.setText(getString(R.string.asoc_score, score));
    }

    private void setButtonTint(MaterialButton btn, Integer color) {
        if (color == null) {
            btn.setBackgroundTintList(null);
        } else {
            btn.setBackgroundTintList(ColorStateList.valueOf(color));
        }
    }

    // ── Guess dialog ──────────────────────────────────────────────────────────

    interface AnswerCallback {
        void onAnswer(String answer);
    }

    private void showGuessDialog(String title, AnswerCallback cb) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setHint(getString(R.string.asoc_guess_hint));
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setPadding(48, 24, 48, 8);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton(android.R.string.ok, (d, w) -> {
                String ans = input.getText() != null ? input.getText().toString() : "";
                cb.onAnswer(ans);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .create();

        // Also allow confirming via keyboard Done action
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String ans = input.getText() != null ? input.getText().toString() : "";
                dialog.dismiss();
                cb.onAnswer(ans);
                return true;
            }
            return false;
        });

        dialog.show();
    }

    // ── Timer ─────────────────────────────────────────────────────────────────

    private void startTimer() {
        pbTimer.setMax(TURN_SECONDS);
        pbTimer.setProgress(TURN_SECONDS);
        tvTimer.setText(getString(R.string.asoc_time_left, TURN_SECONDS));

        timer = new CountDownTimer((long) TURN_SECONDS * 1000, 1000) {
            @Override
            public void onTick(long ms) {
                int s = (int) (ms / 1000) + 1;
                pbTimer.setProgress(s);
                tvTimer.setText(getString(R.string.asoc_time_left, s));
            }

            @Override
            public void onFinish() {
                pbTimer.setProgress(0);
                tvTimer.setText(getString(R.string.asoc_time_left, 0));
                Snackbar.make(finalButton, R.string.asoc_time_up, Snackbar.LENGTH_SHORT).show();
                finalButton.postDelayed(() -> endRound(false), 1000);
            }
        }.start();
    }

    private void cancelTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    // ── Game-over dialog ──────────────────────────────────────────────────────

    private void showGameOver() {
        disableBoard();
        new AlertDialog.Builder(this)
            .setTitle(R.string.asoc_game_over_title)
            .setMessage(getString(R.string.asoc_round_end_msg, score))
            .setCancelable(false)
            .setPositiveButton(R.string.asoc_btn_finish, (d, w) -> finishWithScore(score))
            .show();
    }

    private void disableBoard() {
        for (int col = 0; col < NUM_COLS; col++) {
            for (int row = 0; row < NUM_ROWS; row++) {
                clueButtons[col][row].setEnabled(false);
            }
            colButtons[col].setEnabled(false);
        }
        finalButton.setEnabled(false);
    }

    private void finishWithScore(int score) {
        Intent result = new Intent();
        result.putExtra(Constants.EXTRA_MY_SCORE, score);
        setResult(RESULT_OK, result);
        finish();
    }

    // ── Scoring helpers ───────────────────────────────────────────────────────

    private int countRevealed(int col) {
        int count = 0;
        for (int r = 0; r < NUM_ROWS; r++) {
            if (revealed[col][r]) count++;
        }
        return count;
    }

    private int[] revealedCountsPerCol() {
        int[] counts = new int[NUM_COLS];
        for (int c = 0; c < NUM_COLS; c++) {
            counts[c] = countRevealed(c);
        }
        return counts;
    }
}

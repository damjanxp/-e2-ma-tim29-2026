package com.example.slagalica.ui.challenge;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.slagalica.R;
import com.example.slagalica.data.model.SpojniceAttempt;
import com.example.slagalica.data.model.SpojnicePuzzle;
import com.example.slagalica.data.repository.GameContentRepository;
import com.example.slagalica.logic.games.SpojniceLogic;
import com.example.slagalica.util.Constants;
import com.google.android.material.button.MaterialButton;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Solo verzija igre "Spojnice" za Izazov — jedan igrač, jedna runda.
 * Svaki levi pojam se pokušava tačno jednom: pogrešan pokušaj trajno
 * zaključava pojam (crveno), tačan ga označava zelenim. Igra se završava
 * kada su svi levi pojmovi pokušani ili kada istekne vreme. Vraća bodove
 * kroz {@link Constants#EXTRA_MY_SCORE}.
 */
public class ChallengeSoloSpojniceActivity extends AppCompatActivity {

    private static final String SOLO_UID = "solo";

    private TextView tvCriterion;
    private TextView tvTimer;
    private TextView tvScore;
    private ProgressBar pbTimer;
    private LinearLayout llLeft;
    private LinearLayout llRight;

    private SpojnicePuzzle puzzle;
    private final Map<Integer, SpojniceAttempt> attempts = new HashMap<>();
    private final Map<Integer, MaterialButton> leftButtons = new HashMap<>();
    private final Map<Integer, MaterialButton> rightButtons = new HashMap<>();

    private int selectedLeftIdx = -1;
    private CountDownTimer timer;
    private boolean finished = false;

    private int colorSelected, colorConnected, colorWrong;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge_solo_spojnice);

        tvCriterion = findViewById(R.id.tvCriterion);
        tvTimer = findViewById(R.id.tvTimer);
        tvScore = findViewById(R.id.tvScore);
        pbTimer = findViewById(R.id.pbTimer);
        llLeft = findViewById(R.id.llLeft);
        llRight = findViewById(R.id.llRight);

        colorSelected  = ContextCompat.getColor(this, R.color.spojnice_item_selected);
        colorConnected = ContextCompat.getColor(this, R.color.spojnice_item_connected);
        colorWrong     = ContextCompat.getColor(this, R.color.spojnice_item_wrong);

        GameContentRepository.getInstance().loadSpojnicePuzzles(1,
                new GameContentRepository.SpojniceCallback() {
                    @Override
                    public void onSuccess(@NonNull List<SpojnicePuzzle> puzzles) {
                        if (puzzles.isEmpty()) {
                            Toast.makeText(ChallengeSoloSpojniceActivity.this,
                                    R.string.challenge_solo_error_content, Toast.LENGTH_SHORT).show();
                            finishWithScore(0);
                            return;
                        }
                        puzzle = puzzles.get(0);
                        setupPuzzle();
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        Toast.makeText(ChallengeSoloSpojniceActivity.this, message, Toast.LENGTH_SHORT).show();
                        finishWithScore(0);
                    }
                });
    }

    private void setupPuzzle() {
        tvCriterion.setText(puzzle.getCriterion());
        tvScore.setText(getString(R.string.challenge_solo_score, 0));

        for (int i = 0; i < puzzle.getLeftItems().size(); i++) {
            int idx = i;
            MaterialButton button = new MaterialButton(this);
            button.setText(puzzle.getLeftItems().get(i));
            button.setAllCaps(false);
            button.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            button.setOnClickListener(v -> onLeftClicked(idx));
            llLeft.addView(button);
            leftButtons.put(idx, button);
        }
        for (int i = 0; i < puzzle.getRightItems().size(); i++) {
            int idx = i;
            MaterialButton button = new MaterialButton(this);
            button.setText(puzzle.getRightItems().get(i));
            button.setAllCaps(false);
            button.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            button.setOnClickListener(v -> onRightClicked(idx));
            llRight.addView(button);
            rightButtons.put(idx, button);
        }

        startTimer();
    }

    private void onLeftClicked(int leftIdx) {
        // Svaki levi pojam sme da se pokuša samo jednom — pokušani su zaključani.
        if (finished || attempts.containsKey(leftIdx)) {
            return;
        }
        if (selectedLeftIdx >= 0 && selectedLeftIdx != leftIdx) {
            setTint(leftButtons.get(selectedLeftIdx), null);
        }
        selectedLeftIdx = leftIdx;
        setTint(leftButtons.get(leftIdx), colorSelected);
    }

    private void onRightClicked(int rightIdx) {
        if (finished || selectedLeftIdx < 0) {
            return;
        }
        int leftIdx = selectedLeftIdx;
        selectedLeftIdx = -1;

        boolean correct = puzzle.correctRightFor(leftIdx) == rightIdx;
        attempts.put(leftIdx, new SpojniceAttempt(SOLO_UID, rightIdx, correct));

        MaterialButton leftButton = leftButtons.get(leftIdx);
        leftButton.setEnabled(false);
        if (correct) {
            setTint(leftButton, colorConnected);
            MaterialButton rightButton = rightButtons.get(rightIdx);
            setTint(rightButton, colorConnected);
            rightButton.setEnabled(false);
        } else {
            setTint(leftButton, colorWrong);
        }

        int score = SpojniceLogic.pointsFor(SOLO_UID, attempts);
        tvScore.setText(getString(R.string.challenge_solo_score, score));

        if (attempts.size() >= puzzle.getLeftItems().size()) {
            finishWithScore(score);
        }
    }

    private void setTint(MaterialButton button, Integer color) {
        if (button == null) {
            return;
        }
        if (color == null) {
            button.setBackgroundTintList(null);
        } else {
            button.setBackgroundTintList(ColorStateList.valueOf(color));
        }
    }

    private void startTimer() {
        int totalSeconds = (int) (Constants.SPOJNICE_ROUND_TIME_MS / 1000);
        pbTimer.setMax(totalSeconds);
        pbTimer.setProgress(totalSeconds);

        timer = new CountDownTimer(Constants.SPOJNICE_ROUND_TIME_MS, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int s = (int) Math.ceil(millisUntilFinished / 1000.0);
                tvTimer.setText(getString(R.string.challenge_solo_timer_seconds, s));
                pbTimer.setProgress(s);
            }

            @Override
            public void onFinish() {
                tvTimer.setText(getString(R.string.challenge_solo_timer_seconds, 0));
                pbTimer.setProgress(0);
                finishWithScore(SpojniceLogic.pointsFor(SOLO_UID, attempts));
            }
        }.start();
    }

    private void finishWithScore(int score) {
        if (finished) {
            return;
        }
        finished = true;
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        Intent result = new Intent();
        result.putExtra(Constants.EXTRA_MY_SCORE, score);
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }
}

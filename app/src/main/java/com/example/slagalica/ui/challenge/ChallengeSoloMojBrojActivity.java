package com.example.slagalica.ui.challenge;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.example.slagalica.logic.games.MojBrojLogic;
import com.example.slagalica.util.Constants;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Solo verzija igre "Moj broj" za Izazov — jedan igrač, jedna runda, bez
 * protivnika. Vraća broj bodova kroz {@link Constants#EXTRA_MY_SCORE}.
 */
public class ChallengeSoloMojBrojActivity extends AppCompatActivity {

    private static final long ROUND_TIME_MS = 60_000;

    private final Random random = new Random();

    private TextView tvTimer;
    private TextView tvTarget;
    private TextView tvNumbers;
    private TextInputEditText etExpression;
    private MaterialButton btnConfirm;
    private TextView tvResult;

    private int target;
    private int[] numbers;
    private CountDownTimer timer;
    private boolean finished = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge_solo_mojbroj);

        tvTimer = findViewById(R.id.tvTimer);
        tvTarget = findViewById(R.id.tvTarget);
        tvNumbers = findViewById(R.id.tvNumbers);
        etExpression = findViewById(R.id.etExpression);
        btnConfirm = findViewById(R.id.btnConfirm);
        tvResult = findViewById(R.id.tvResult);

        target = 100 + random.nextInt(900);
        numbers = generisi6Brojeva();

        tvTarget.setText(getString(R.string.challenge_solo_mojbroj_target, target));
        StringBuilder sb = new StringBuilder();
        for (int n : numbers) sb.append(n).append("   ");
        tvNumbers.setText(sb.toString().trim());

        btnConfirm.setOnClickListener(v -> onConfirm());
        startTimer();
    }

    /** Ista šema kao u {@code MojBrojActivity} — 4 jednocifrena + 1 mali + 1 veliki broj. */
    private int[] generisi6Brojeva() {
        int[] maliSet = {10, 15, 20};
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

    private void startTimer() {
        timer = new CountDownTimer(ROUND_TIME_MS, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvTimer.setText(getString(R.string.challenge_solo_timer_seconds,
                        (int) Math.ceil(millisUntilFinished / 1000.0)));
            }

            @Override
            public void onFinish() {
                finishWithScore(0);
            }
        }.start();
    }

    private void onConfirm() {
        if (finished) {
            return;
        }
        String expression = etExpression.getText() != null ? etExpression.getText().toString() : "";
        MojBrojLogic.IzrazRezultat myResult = MojBrojLogic.evaluate(expression, numbers);
        if (!myResult.validan) {
            tvResult.setText(getString(R.string.challenge_solo_mojbroj_error_invalid, myResult.greska));
            return;
        }

        // Nema protivnika — nevalidan rezultat kao "protivnik" daje čist scoring iz IzrazRezultat.
        MojBrojLogic.IzrazRezultat noOpponent = new MojBrojLogic.IzrazRezultat();
        noOpponent.validan = false;

        int score = MojBrojLogic.izracunajBodovanje(target, myResult, noOpponent, true)[0];
        finishWithScore(score);
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

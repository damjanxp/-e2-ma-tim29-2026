package com.example.slagalica.ui.challenge;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.example.slagalica.data.model.KorakPoKorakZadatak;
import com.example.slagalica.data.repository.GameContentRepository;
import com.example.slagalica.logic.games.KorakPoKorakLogic;
import com.example.slagalica.util.Constants;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

/**
 * Solo verzija igre "Korak po korak" za Izazov — jedan igrač pogađa jedan
 * zadatak bez protivnika. Kao u multiplayer verziji, runda traje 70 sekundi
 * i na svakih 10 sekundi se automatski otvara sledeći korak; igrač može i
 * ručno da otvori korak ranije. Vraća broj bodova kroz
 * {@link Constants#EXTRA_MY_SCORE}.
 */
public class ChallengeSoloKorakActivity extends AppCompatActivity {

    private static final int MAX_HINTS        = 7;
    private static final int ROUND_DURATION_MS = 70_000;
    private static final int ROUND_SECONDS     = ROUND_DURATION_MS / 1000;
    private static final int HINT_INTERVAL_S   = 10;

    private TextView tvProgress;
    private TextView tvHints;
    private TextView tvMessage;
    private TextView tvScore;
    private TextView tvTimer;
    private ProgressBar pbTimer;
    private TextInputEditText etGuess;
    private MaterialButton btnGuess;
    private MaterialButton btnReveal;

    private KorakPoKorakZadatak zadatak;
    private int openedHints = 1;
    private boolean finished = false;
    private CountDownTimer timer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge_solo_korak);

        tvProgress = findViewById(R.id.tvProgress);
        tvHints = findViewById(R.id.tvHints);
        tvMessage = findViewById(R.id.tvMessage);
        tvScore = findViewById(R.id.tvScore);
        tvTimer = findViewById(R.id.tvTimer);
        pbTimer = findViewById(R.id.pbTimer);
        etGuess = findViewById(R.id.etGuess);
        btnGuess = findViewById(R.id.btnGuess);
        btnReveal = findViewById(R.id.btnReveal);

        btnGuess.setOnClickListener(v -> onGuess());
        btnReveal.setOnClickListener(v -> onRevealNext());

        tvScore.setText(getString(R.string.challenge_solo_score, 0));

        GameContentRepository.getInstance().getRandomKorakPoKorak(
                new GameContentRepository.KorakLoadCallback() {
                    @Override
                    public void onSuccess(KorakPoKorakZadatak loaded) {
                        zadatak = loaded;
                        renderHints();
                        startTimer();
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(ChallengeSoloKorakActivity.this, message, Toast.LENGTH_SHORT).show();
                        finishWithScore(0);
                    }
                });
    }

    private void renderHints() {
        tvProgress.setText(getString(R.string.challenge_solo_korak_progress, openedHints, MAX_HINTS));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < openedHints; i++) {
            sb.append(i + 1).append(". ").append(zadatak.getKoraci().get(i)).append('\n');
        }
        tvHints.setText(sb.toString().trim());
        btnReveal.setEnabled(openedHints < MAX_HINTS);
    }

    /**
     * 70-sekundni tajmer runde: na svakih 10 sekundi automatski otvara sledeći
     * korak (kao u multiplayer verziji), a po isteku završava igru sa 0 bodova.
     */
    private void startTimer() {
        timer = new CountDownTimer(ROUND_DURATION_MS, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int s = (int) Math.ceil(millisUntilFinished / 1000.0);
                tvTimer.setText(getString(R.string.challenge_solo_timer_seconds, s));
                pbTimer.setProgress(s);

                // Automatsko otvaranje koraka: na 60s, 50s, 40s... otvara se 2., 3., ... korak
                int elapsed = ROUND_SECONDS - s;
                int shouldBeOpened = Math.min(1 + elapsed / HINT_INTERVAL_S, MAX_HINTS);
                if (shouldBeOpened > openedHints) {
                    openedHints = shouldBeOpened;
                    renderHints();
                }
            }

            @Override
            public void onFinish() {
                tvTimer.setText(getString(R.string.challenge_solo_timer_seconds, 0));
                pbTimer.setProgress(0);
                finishWithScore(0);
            }
        }.start();
    }

    private void onGuess() {
        if (finished || zadatak == null) {
            return;
        }
        String guess = etGuess.getText() != null ? etGuess.getText().toString() : "";
        if (KorakPoKorakLogic.tacanOdgovor(guess, zadatak.getResenje())) {
            int score = KorakPoKorakLogic.bodoviZaPogodakUKoraku(openedHints - 1);
            tvScore.setText(getString(R.string.challenge_solo_score, score));
            tvMessage.setText(getString(R.string.challenge_solo_korak_correct, score));
            finishWithScore(score);
        } else {
            tvMessage.setText(R.string.challenge_solo_korak_wrong);
        }
    }

    private void onRevealNext() {
        if (finished || openedHints >= MAX_HINTS) {
            return;
        }
        openedHints++;
        renderHints();
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

package com.example.slagalica.ui.challenge;

import android.content.Intent;
import android.os.Bundle;
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
 * zadatak bez protivnika i bez faze preuzimanja. Vraća broj bodova kroz
 * {@link Constants#EXTRA_MY_SCORE}.
 */
public class ChallengeSoloKorakActivity extends AppCompatActivity {

    private static final int MAX_HINTS = 7;

    private TextView tvProgress;
    private TextView tvHints;
    private TextView tvMessage;
    private TextInputEditText etGuess;
    private MaterialButton btnGuess;
    private MaterialButton btnReveal;

    private KorakPoKorakZadatak zadatak;
    private int openedHints = 1;
    private boolean finished = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge_solo_korak);

        tvProgress = findViewById(R.id.tvProgress);
        tvHints = findViewById(R.id.tvHints);
        tvMessage = findViewById(R.id.tvMessage);
        etGuess = findViewById(R.id.etGuess);
        btnGuess = findViewById(R.id.btnGuess);
        btnReveal = findViewById(R.id.btnReveal);

        btnGuess.setOnClickListener(v -> onGuess());
        btnReveal.setOnClickListener(v -> onRevealNext());

        GameContentRepository.getInstance().getRandomKorakPoKorak(
                new GameContentRepository.KorakLoadCallback() {
                    @Override
                    public void onSuccess(KorakPoKorakZadatak loaded) {
                        zadatak = loaded;
                        renderHints();
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

    private void onGuess() {
        if (finished || zadatak == null) {
            return;
        }
        String guess = etGuess.getText() != null ? etGuess.getText().toString() : "";
        if (KorakPoKorakLogic.tacanOdgovor(guess, zadatak.getResenje())) {
            int score = KorakPoKorakLogic.bodoviZaPogodakUKoraku(openedHints - 1);
            tvMessage.setText(getString(R.string.challenge_solo_korak_correct, score));
            finishWithScore(score);
        } else {
            tvMessage.setText(R.string.challenge_solo_korak_wrong);
            if (openedHints >= MAX_HINTS) {
                finishWithScore(0);
            }
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
        Intent result = new Intent();
        result.putExtra(Constants.EXTRA_MY_SCORE, score);
        setResult(RESULT_OK, result);
        finish();
    }
}

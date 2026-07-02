package com.example.slagalica.ui.challenge;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.slagalica.R;
import com.example.slagalica.data.model.KzzAnswer;
import com.example.slagalica.data.model.KzzQuestion;
import com.example.slagalica.data.repository.GameContentRepository;
import com.example.slagalica.logic.games.KoZnaZnaLogic;
import com.example.slagalica.util.Constants;
import com.google.android.material.button.MaterialButton;

import java.util.Collections;
import java.util.List;

/**
 * Solo verzija igre "Ko zna zna" za Izazov — jedan igrač, bez protivnika.
 * Igra {@link Constants#KZZ_QUESTION_COUNT} pitanja i vraća ukupan broj
 * bodova pozivaocu kroz {@link Constants#EXTRA_MY_SCORE}. Bodovi se
 * ažuriraju odmah nakon svakog odgovora, uz obojenu povratnu informaciju
 * kao u multiplayer verziji.
 */
public class ChallengeSoloKzzActivity extends AppCompatActivity {

    /** Ključ pod kojim se čuva sopstveni odgovor — nema pravog UID-a jer nema protivnika. */
    private static final String SOLO_KEY = "solo";

    private static final int FEEDBACK_DELAY_MS = 800;

    private TextView tvProgress;
    private TextView tvTimer;
    private TextView tvQuestion;
    private TextView tvScore;
    private ProgressBar pbTimer;
    private MaterialButton[] answerButtons;

    private List<KzzQuestion> questions;
    private int currentIndex;
    private int totalScore;
    private long questionStartMs;
    private boolean answered;

    private CountDownTimer timer;

    private int colorCorrect, colorWrong;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge_solo_kzz);

        initViews();
        GameContentRepository.getInstance().loadKzzQuestions(Constants.KZZ_QUESTION_COUNT,
                new GameContentRepository.KzzCallback() {
                    @Override
                    public void onSuccess(@NonNull List<KzzQuestion> loaded) {
                        questions = loaded;
                        showQuestion(0);
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        Toast.makeText(ChallengeSoloKzzActivity.this, message, Toast.LENGTH_SHORT).show();
                        finishWithScore(0);
                    }
                });
    }

    private void initViews() {
        tvProgress = findViewById(R.id.tvProgress);
        tvTimer = findViewById(R.id.tvTimer);
        tvQuestion = findViewById(R.id.tvQuestion);
        tvScore = findViewById(R.id.tvScore);
        pbTimer = findViewById(R.id.pbTimer);

        answerButtons = new MaterialButton[]{
                findViewById(R.id.btnAnswer0),
                findViewById(R.id.btnAnswer1),
                findViewById(R.id.btnAnswer2),
                findViewById(R.id.btnAnswer3)
        };
        for (int i = 0; i < answerButtons.length; i++) {
            final int idx = i;
            answerButtons[i].setOnClickListener(v -> onAnswer(idx));
        }

        colorCorrect = ContextCompat.getColor(this, R.color.kzz_answer_correct);
        colorWrong   = ContextCompat.getColor(this, R.color.kzz_answer_wrong);

        tvScore.setText(getString(R.string.challenge_solo_score, 0));
    }

    private void showQuestion(int index) {
        currentIndex = index;
        answered = false;
        KzzQuestion question = questions.get(index);

        tvProgress.setText(getString(R.string.challenge_solo_question_progress,
                index + 1, questions.size()));
        tvQuestion.setText(question.getText());
        List<String> answers = question.getAnswers();
        for (int i = 0; i < answerButtons.length; i++) {
            answerButtons[i].setText(answers.get(i));
            answerButtons[i].setBackgroundTintList(null);
            answerButtons[i].setEnabled(true);
        }

        questionStartMs = System.currentTimeMillis();
        startTimer();
    }

    private void startTimer() {
        cancelTimer();
        int totalSeconds = (int) (Constants.KZZ_QUESTION_TIME_MS / 1000);
        pbTimer.setMax(totalSeconds);
        pbTimer.setProgress(totalSeconds);

        timer = new CountDownTimer(Constants.KZZ_QUESTION_TIME_MS, 1000) {
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
                if (!answered) {
                    resolveAnswer(-1);
                }
            }
        }.start();
    }

    private void cancelTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private void onAnswer(int answerIndex) {
        if (answered) {
            return;
        }
        resolveAnswer(answerIndex);
    }

    private void resolveAnswer(int answerIndex) {
        answered = true;
        cancelTimer();

        KzzQuestion question = questions.get(currentIndex);
        long elapsed = System.currentTimeMillis() - questionStartMs;
        boolean correct = answerIndex >= 0 && answerIndex == question.getCorrectIndex();
        KzzAnswer myAnswer = new KzzAnswer(answerIndex, elapsed, correct);

        // Obojena povratna informacija kao u multiplayer verziji
        for (MaterialButton button : answerButtons) {
            button.setEnabled(false);
        }
        answerButtons[question.getCorrectIndex()]
                .setBackgroundTintList(ColorStateList.valueOf(colorCorrect));
        if (answerIndex >= 0 && !correct) {
            answerButtons[answerIndex].setBackgroundTintList(ColorStateList.valueOf(colorWrong));
        }

        int points = KoZnaZnaLogic.pointsFor(SOLO_KEY, Collections.singletonMap(SOLO_KEY, myAnswer));
        totalScore += points;
        tvScore.setText(getString(R.string.challenge_solo_score, totalScore));

        int next = currentIndex + 1;
        if (next < questions.size()) {
            tvQuestion.postDelayed(() -> showQuestion(next), FEEDBACK_DELAY_MS);
        } else {
            tvQuestion.postDelayed(() -> finishWithScore(totalScore), FEEDBACK_DELAY_MS);
        }
    }

    private void finishWithScore(int score) {
        Intent result = new Intent();
        result.putExtra(Constants.EXTRA_MY_SCORE, score);
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelTimer();
    }
}

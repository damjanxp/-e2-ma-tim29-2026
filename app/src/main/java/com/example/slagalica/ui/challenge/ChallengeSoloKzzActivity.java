package com.example.slagalica.ui.challenge;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

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
 * bodova pozivaocu kroz {@link Constants#EXTRA_MY_SCORE}.
 */
public class ChallengeSoloKzzActivity extends AppCompatActivity {

    /** Ključ pod kojim se čuva sopstveni odgovor — nema pravog UID-a jer nema protivnika. */
    private static final String SOLO_KEY = "solo";

    private TextView tvProgress;
    private TextView tvTimer;
    private TextView tvQuestion;
    private MaterialButton btnAnswer0;
    private MaterialButton btnAnswer1;
    private MaterialButton btnAnswer2;
    private MaterialButton btnAnswer3;
    private TextView tvScore;

    private List<KzzQuestion> questions;
    private int currentIndex;
    private int totalScore;
    private long questionStartMs;
    private boolean answered;

    private CountDownTimer timer;

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
        btnAnswer0 = findViewById(R.id.btnAnswer0);
        btnAnswer1 = findViewById(R.id.btnAnswer1);
        btnAnswer2 = findViewById(R.id.btnAnswer2);
        btnAnswer3 = findViewById(R.id.btnAnswer3);
        tvScore = findViewById(R.id.tvScore);

        btnAnswer0.setOnClickListener(v -> onAnswer(0));
        btnAnswer1.setOnClickListener(v -> onAnswer(1));
        btnAnswer2.setOnClickListener(v -> onAnswer(2));
        btnAnswer3.setOnClickListener(v -> onAnswer(3));
    }

    private void showQuestion(int index) {
        currentIndex = index;
        answered = false;
        KzzQuestion question = questions.get(index);

        tvProgress.setText(getString(R.string.challenge_solo_question_progress,
                index + 1, questions.size()));
        tvQuestion.setText(question.getText());
        List<String> answers = question.getAnswers();
        btnAnswer0.setText(answers.get(0));
        btnAnswer1.setText(answers.get(1));
        btnAnswer2.setText(answers.get(2));
        btnAnswer3.setText(answers.get(3));
        tvScore.setText(getString(R.string.challenge_solo_score, totalScore));

        questionStartMs = System.currentTimeMillis();
        startTimer();
    }

    private void startTimer() {
        cancelTimer();
        timer = new CountDownTimer(Constants.KZZ_QUESTION_TIME_MS, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvTimer.setText(getString(R.string.challenge_solo_timer_seconds,
                        (int) Math.ceil(millisUntilFinished / 1000.0)));
            }

            @Override
            public void onFinish() {
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

        int points = KoZnaZnaLogic.pointsFor(SOLO_KEY, Collections.singletonMap(SOLO_KEY, myAnswer));
        totalScore += points;
        tvScore.setText(getString(R.string.challenge_solo_score, totalScore));

        int next = currentIndex + 1;
        if (next < questions.size()) {
            tvQuestion.postDelayed(() -> showQuestion(next), 400);
        } else {
            tvQuestion.postDelayed(() -> finishWithScore(totalScore), 400);
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

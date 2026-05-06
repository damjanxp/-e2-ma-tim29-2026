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


public class KoZnaZnaActivity extends AppCompatActivity {

    private static final int QUESTION_TIME_MS  = 5_000;
    private static final int FEEDBACK_DELAY_MS = 1_200;

    private static final Object[][] QUESTIONS = {
        {"Koji grad je prestonica Francuske?",   "Berlin",   "Madrid",   "Pariz",   "Rim",      2},
        {"Koliko planeta ima Sunčev sistem?",    "7",        "8",        "9",        "10",       1},
        {"Ko je napisao 'Romeo i Julija'?",      "Dikens",   "Tolstoj",  "Šekspir",  "Homer",    2},
        {"Koji element ima hemijski simbol O?",  "Zlato",    "Kiseonik", "Srebro",   "Vodonik",  1},
        {"Koja je najveća planeta?",             "Saturn",   "Zemlja",   "Mars",     "Jupiter",  3},
    };

    // Views
    private TextView       tvPlayerName;
    private TextView       tvQuestionNumber;
    private TextView       tvQuestion;
    private TextView       tvScore;
    private TextView       tvTimer;
    private ProgressBar    pbTimer;
    private MaterialButton btnAnswer1;
    private MaterialButton btnAnswer2;
    private MaterialButton btnAnswer3;
    private MaterialButton btnAnswer4;
    private MaterialButton[] answerButtons;

    // Stanje
    private int            currentQuestion = 0;
    private int            score           = 0;
    private boolean        answerGiven     = false;
    private ColorStateList defaultButtonTint;
    private CountDownTimer questionTimer;
    private final Handler  handler         = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ko_zna_zna);

        initViews();
        setupListeners();
        showQuestion(currentQuestion);
    }

    private void initViews() {
        tvPlayerName     = findViewById(R.id.tvPlayerName);
        tvQuestionNumber = findViewById(R.id.tvQuestionNumber);
        tvQuestion       = findViewById(R.id.tvQuestion);
        tvScore          = findViewById(R.id.tvScore);
        tvTimer          = findViewById(R.id.tvTimer);
        pbTimer          = findViewById(R.id.pbTimer);
        btnAnswer1       = findViewById(R.id.btnAnswer1);
        btnAnswer2       = findViewById(R.id.btnAnswer2);
        btnAnswer3       = findViewById(R.id.btnAnswer3);
        btnAnswer4       = findViewById(R.id.btnAnswer4);

        answerButtons = new MaterialButton[]{btnAnswer1, btnAnswer2, btnAnswer3, btnAnswer4};

        // Sačuvaj podrazumevanu boju dugmadi za resetovanje
        defaultButtonTint = btnAnswer1.getBackgroundTintList();

        // KT1: mock ime igrača
        tvPlayerName.setText(getString(R.string.kzz_player_label, "Marko"));
        tvScore.setText(getString(R.string.kzz_score_label, score));
    }

    private void setupListeners() {
        for (int i = 0; i < answerButtons.length; i++) {
            final int answerIdx = i;
            answerButtons[i].setOnClickListener(v -> onAnswerClicked(answerIdx));
        }

        findViewById(R.id.btnGiveUp).setOnClickListener(v -> finish());
    }

    private void showQuestion(int idx) {
        if (idx >= QUESTIONS.length) {
            endGame();
            return;
        }

        answerGiven = false;
        Object[] q = QUESTIONS[idx];

        tvQuestionNumber.setText(getString(R.string.kzz_question_label, idx + 1));
        tvQuestion.setText((String) q[0]);
        for (int i = 0; i < 4; i++) {
            answerButtons[i].setText((String) q[i + 1]);
            resetButtonTint(answerButtons[i]);
            answerButtons[i].setEnabled(true);
        }

        startQuestionTimer();
    }

    private void onAnswerClicked(int answerIdx) {
        if (answerGiven) return;
        answerGiven = true;
        if (questionTimer != null) questionTimer.cancel();

        int correct = (int) QUESTIONS[currentQuestion][5];
        if (answerIdx == correct) {
            score += 10;
            setButtonTint(answerButtons[answerIdx], R.color.kzz_answer_correct);
            Toast.makeText(this, getString(R.string.kzz_correct), Toast.LENGTH_SHORT).show();
        } else {
            score -= 5;
            setButtonTint(answerButtons[answerIdx], R.color.kzz_answer_wrong);
            setButtonTint(answerButtons[correct], R.color.kzz_answer_correct);
            Toast.makeText(this, getString(R.string.kzz_wrong), Toast.LENGTH_SHORT).show();
        }

        tvScore.setText(getString(R.string.kzz_score_label, score));
        disableAllAnswers();
        handler.postDelayed(this::goToNextQuestion, FEEDBACK_DELAY_MS);
    }

    private void goToNextQuestion() {
        currentQuestion++;
        showQuestion(currentQuestion);
    }

    private void startQuestionTimer() {
        pbTimer.setMax(5);
        pbTimer.setProgress(5);
        tvTimer.setText(getString(R.string.kzz_time_left, 5));

        questionTimer = new CountDownTimer(QUESTION_TIME_MS, 1_000) {

            @Override
            public void onTick(long millisUntilFinished) {
                int sekundePreostale = (int) (millisUntilFinished / 1000) + 1;
                pbTimer.setProgress(sekundePreostale);
                tvTimer.setText(getString(R.string.kzz_time_left, sekundePreostale));
            }

            @Override
            public void onFinish() {
                pbTimer.setProgress(0);
                tvTimer.setText(getString(R.string.kzz_time_left, 0));
                if (!answerGiven) {
                    answerGiven = true;
                    // Prikaži tačan odgovor žutom bojom
                    int correct = (int) QUESTIONS[currentQuestion][5];
                    setButtonTint(answerButtons[correct], R.color.kzz_answer_missed);
                    disableAllAnswers();
                    Toast.makeText(KoZnaZnaActivity.this,
                            getString(R.string.kzz_time_up), Toast.LENGTH_SHORT).show();
                    handler.postDelayed(KoZnaZnaActivity.this::goToNextQuestion, FEEDBACK_DELAY_MS);
                }
            }
        }.start();
    }

    private void endGame() {
        Toast.makeText(this,
                getString(R.string.kzz_round_end, score),
                Toast.LENGTH_LONG).show();
        finish();
    }

    private void disableAllAnswers() {
        for (MaterialButton btn : answerButtons) btn.setEnabled(false);
    }

    private void resetButtonTint(MaterialButton btn) {
        btn.setBackgroundTintList(defaultButtonTint);
    }

    private void setButtonTint(MaterialButton btn, int colorResId) {
        btn.setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(this, colorResId)));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (questionTimer != null) questionTimer.cancel();
    }
}

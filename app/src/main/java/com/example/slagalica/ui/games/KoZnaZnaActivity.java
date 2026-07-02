package com.example.slagalica.ui.games;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.slagalica.R;
import com.example.slagalica.data.model.KzzAnswer;
import com.example.slagalica.data.model.KzzQuestion;
import com.example.slagalica.data.repository.MatchRepository;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.logic.games.KoZnaZnaLogic;
import com.example.slagalica.ui.main.MainActivity;
import com.example.slagalica.util.Constants;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Igra "Ko zna zna" — KT2, multiplayer 1 na 1.
 *
 * <p>Pitanja upisuje kreator meča u Realtime Database, tako da oba igrača vide
 * identičan sadržaj. Odgovori se sinhronizuju "lockstep" protokolom: na sledeće
 * pitanje se prelazi tek kada oba igrača odgovore (ili im istekne vreme).
 * Bodovanje: +10 tačan, −5 netačan, a ako su oba igrača tačna, bodove dobija
 * samo brži ({@link KoZnaZnaLogic}).</p>
 */
public class KoZnaZnaActivity extends AppCompatActivity {

    private static final int FEEDBACK_DELAY_MS = 1_500;

    private final MatchRepository matchRepository = MatchRepository.getInstance();
    private final UserRepository userRepository = UserRepository.getInstance();

    // Views
    private TextView       tvOpponentScore;
    private TextView       tvQuestionNumber;
    private TextView       tvScore;
    private TextView       tvTimer;
    private TextView       tvStatus;
    private TextView       tvQuestion;
    private ProgressBar    pbTimer;
    private MaterialButton[] answerButtons;
    private ColorStateList defaultButtonTint;

    // Meč
    private String matchId;
    private String myUid;
    private String opponentUid;
    private String opponentName;
    private boolean isPlayerOne;
    private boolean opponentOnline = true;

    // Stanje igre
    private List<KzzQuestion> questions;
    private int currentQuestion = 0;
    private int myScore = 0;
    private int opponentScore = 0;
    private int myCorrectCount = 0;
    private int myWrongCount = 0;
    private boolean answerGiven = false;
    private boolean questionResolved = false;
    private boolean gameEnded = false;
    private long questionStartMs;
    private Map<String, KzzAnswer> lastAnswers;

    private CountDownTimer questionTimer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Runnable> listenerDetachers = new ArrayList<>();
    private Runnable answersDetacher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ko_zna_zna);

        readExtras();
        initViews();
        setupListeners();
        setupBackHandler();
        joinMatch();
    }

    private void readExtras() {
        matchId      = getIntent().getStringExtra(Constants.EXTRA_MATCH_ID);
        opponentUid  = getIntent().getStringExtra(Constants.EXTRA_OPPONENT_UID);
        opponentName = getIntent().getStringExtra(Constants.EXTRA_OPPONENT_NAME);
        isPlayerOne  = getIntent().getBooleanExtra(Constants.EXTRA_IS_PLAYER_ONE, false);
        myUid        = userRepository.getCurrentUid();
    }

    private void initViews() {
        tvOpponentScore  = findViewById(R.id.tvOpponentScore);
        tvQuestionNumber = findViewById(R.id.tvQuestionNumber);
        tvScore          = findViewById(R.id.tvScore);
        tvTimer          = findViewById(R.id.tvTimer);
        tvStatus         = findViewById(R.id.tvStatus);
        tvQuestion       = findViewById(R.id.tvQuestion);
        pbTimer          = findViewById(R.id.pbTimer);

        answerButtons = new MaterialButton[]{
                findViewById(R.id.btnAnswer1),
                findViewById(R.id.btnAnswer2),
                findViewById(R.id.btnAnswer3),
                findViewById(R.id.btnAnswer4)
        };
        defaultButtonTint = answerButtons[0].getBackgroundTintList();

        updateScoreLabels();
        tvStatus.setText(R.string.kzz_waiting_content);
        disableAllAnswers();
    }

    private void setupListeners() {
        for (int i = 0; i < answerButtons.length; i++) {
            final int answerIdx = i;
            answerButtons[i].setOnClickListener(v -> onAnswerClicked(answerIdx));
        }
        findViewById(R.id.btnGiveUp).setOnClickListener(v -> confirmGiveUp());
    }

    private void setupBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmGiveUp();
            }
        });
    }

    // =========================================================================
    // Povezivanje na meč
    // =========================================================================

    private void joinMatch() {
        if (matchId == null || myUid == null) {
            Toast.makeText(this, getString(R.string.profile_error_load), Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        matchRepository.setPresence(matchId, myUid);

        listenerDetachers.add(matchRepository.listenOpponentPresence(matchId, opponentUid,
                online -> {
                    boolean wasOnline = opponentOnline;
                    opponentOnline = online;
                    if (wasOnline && !online) {
                        Toast.makeText(this, getString(R.string.kzz_opponent_left),
                                Toast.LENGTH_SHORT).show();
                        // Ne čekamo odsutnog protivnika (specifikacija 3f)
                        if (lastAnswers != null) {
                            maybeResolve(lastAnswers);
                        }
                    }
                }));

        listenerDetachers.add(matchRepository.listenKzzQuestions(matchId,
                new MatchRepository.KzzQuestionsListener() {
            @Override
            public void onQuestions(@NonNull List<KzzQuestion> loaded) {
                questions = loaded;
                showQuestion(0);
            }

            @Override
            public void onError(@NonNull String message) {
                Toast.makeText(KoZnaZnaActivity.this, message, Toast.LENGTH_LONG).show();
                finish();
            }
        }));
    }

    // =========================================================================
    // Tok igre
    // =========================================================================

    private void showQuestion(int idx) {
        if (gameEnded) {
            return;
        }
        if (idx >= questions.size()) {
            endGame();
            return;
        }
        currentQuestion = idx;
        answerGiven = false;
        questionResolved = false;
        lastAnswers = null;

        KzzQuestion q = questions.get(idx);
        tvQuestionNumber.setText(getString(R.string.kzz_question_label, idx + 1));
        tvQuestion.setText(q.getText());
        tvStatus.setText("");
        for (int i = 0; i < answerButtons.length; i++) {
            answerButtons[i].setText(q.getAnswers().get(i));
            answerButtons[i].setBackgroundTintList(defaultButtonTint);
            answerButtons[i].setEnabled(true);
        }

        // Slušaj odgovore oba igrača na ovo pitanje
        if (answersDetacher != null) {
            answersDetacher.run();
        }
        answersDetacher = matchRepository.listenKzzAnswers(matchId, idx, this::onAnswersChanged);

        questionStartMs = SystemClock.elapsedRealtime();
        startQuestionTimer();
    }

    private void onAnswerClicked(int answerIdx) {
        if (answerGiven || questions == null) {
            return;
        }
        answerGiven = true;
        if (questionTimer != null) {
            questionTimer.cancel();
        }
        long elapsed = SystemClock.elapsedRealtime() - questionStartMs;
        boolean correct = answerIdx == questions.get(currentQuestion).getCorrectIndex();
        if (correct) {
            myCorrectCount++;
        } else {
            myWrongCount++;
        }

        disableAllAnswers();
        setButtonTint(answerButtons[answerIdx],
                correct ? R.color.kzz_answer_correct : R.color.kzz_answer_wrong);
        tvStatus.setText(R.string.kzz_waiting_opponent);

        matchRepository.submitKzzAnswer(matchId, currentQuestion, myUid,
                new KzzAnswer(answerIdx, elapsed, correct));
    }

    private void startQuestionTimer() {
        int totalSeconds = Constants.KZZ_QUESTION_TIME_MS / 1000;
        pbTimer.setMax(totalSeconds);
        pbTimer.setProgress(totalSeconds);
        tvTimer.setText(getString(R.string.kzz_time_left, totalSeconds));

        questionTimer = new CountDownTimer(Constants.KZZ_QUESTION_TIME_MS, 1_000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsLeft = (int) (millisUntilFinished / 1000) + 1;
                pbTimer.setProgress(secondsLeft);
                tvTimer.setText(getString(R.string.kzz_time_left, secondsLeft));
            }

            @Override
            public void onFinish() {
                pbTimer.setProgress(0);
                tvTimer.setText(getString(R.string.kzz_time_left, 0));
                if (!answerGiven) {
                    answerGiven = true;
                    disableAllAnswers();
                    tvStatus.setText(R.string.kzz_waiting_opponent);
                    matchRepository.submitKzzAnswer(matchId, currentQuestion, myUid,
                            new KzzAnswer(-1, Constants.KZZ_QUESTION_TIME_MS, false));
                }
            }
        }.start();
    }

    /** Okida se na svaku promenu odgovora u Realtime Database za tekuće pitanje. */
    private void onAnswersChanged(@NonNull Map<String, KzzAnswer> answers) {
        lastAnswers = answers;
        maybeResolve(answers);
    }

    /**
     * Razrešava pitanje kada su oba odgovora dostupna (ili je protivnik
     * napustio meč) — oba klijenta determinističkim pravilima iz
     * {@link KoZnaZnaLogic} dolaze do identičnog rezultata.
     */
    private void maybeResolve(@NonNull Map<String, KzzAnswer> answers) {
        if (questionResolved || gameEnded) {
            return;
        }
        boolean iAnswered = answers.containsKey(myUid);
        boolean opponentAnswered = answers.containsKey(opponentUid);
        if (!iAnswered) {
            return; // čekamo sopstveni upis
        }
        if (!opponentAnswered && opponentOnline) {
            return; // protivnik još razmišlja
        }
        questionResolved = true;
        if (questionTimer != null) {
            questionTimer.cancel();
        }

        int myDelta = KoZnaZnaLogic.pointsFor(myUid, answers);
        int opponentDelta = KoZnaZnaLogic.pointsFor(opponentUid, answers);
        myScore += myDelta;
        opponentScore += opponentDelta;
        updateScoreLabels();
        showResolutionFeedback(answers, myDelta);

        handler.postDelayed(() -> showQuestion(currentQuestion + 1), FEEDBACK_DELAY_MS);
    }

    private void showResolutionFeedback(Map<String, KzzAnswer> answers, int myDelta) {
        int correctIdx = questions.get(currentQuestion).getCorrectIndex();
        KzzAnswer mine = answers.get(myUid);

        if (mine == null || mine.getAnswerIndex() < 0) {
            setButtonTint(answerButtons[correctIdx], R.color.kzz_answer_missed);
            tvStatus.setText(R.string.kzz_time_up);
        } else if (!mine.isCorrect()) {
            setButtonTint(answerButtons[correctIdx], R.color.kzz_answer_correct);
            tvStatus.setText(R.string.kzz_wrong);
        } else if (myDelta == 0) {
            // Tačan odgovor, ali protivnik je bio brži
            tvStatus.setText(R.string.kzz_too_slow);
        } else {
            tvStatus.setText(R.string.kzz_correct);
        }
    }

    // =========================================================================
    // Kraj igre — prelazak na "Spojnice"
    // =========================================================================

    private void endGame() {
        if (gameEnded) {
            return;
        }
        gameEnded = true;

        matchRepository.setGameResult(matchId, MatchRepository.GAME_KZZ, myUid, myScore);
        userRepository.recordKzzResult(myUid, myCorrectCount, myWrongCount, myScore);

        Toast.makeText(this, getString(R.string.kzz_round_end, myScore), Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, SpojniceActivity.class);
        intent.putExtra(Constants.EXTRA_MATCH_ID, matchId);
        intent.putExtra(Constants.EXTRA_IS_PLAYER_ONE, isPlayerOne);
        intent.putExtra(Constants.EXTRA_OPPONENT_UID, opponentUid);
        intent.putExtra(Constants.EXTRA_OPPONENT_NAME, opponentName);
        intent.putExtra(Constants.EXTRA_MY_SCORE, myScore);
        intent.putExtra(Constants.EXTRA_OPPONENT_SCORE, opponentScore);
        startActivity(intent);
        finish();
    }

    private void confirmGiveUp() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_give_up_title)
                .setMessage(R.string.dialog_give_up_message)
                .setPositiveButton(R.string.dialog_yes, (d, w) -> giveUp())
                .setNegativeButton(R.string.dialog_no, null)
                .show();
    }

    private void giveUp() {
        gameEnded = true;
        matchRepository.leaveMatch(matchId, myUid);
        goToMainActivity();
    }

    /** Predaja partije uvek vodi na glavni ekran (specifikacija: napuštanje = poraz). */
    private void goToMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // =========================================================================
    // Pomoćne metode i lifecycle
    // =========================================================================

    private void updateScoreLabels() {
        tvScore.setText(getString(R.string.kzz_score_label, myScore));
        tvOpponentScore.setText(getString(R.string.kzz_opponent_score_label,
                opponentName != null ? opponentName : "?", opponentScore));
    }

    private void disableAllAnswers() {
        for (MaterialButton btn : answerButtons) {
            btn.setEnabled(false);
        }
    }

    private void setButtonTint(MaterialButton btn, int colorResId) {
        btn.setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(this, colorResId)));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (questionTimer != null) {
            questionTimer.cancel();
        }
        if (answersDetacher != null) {
            answersDetacher.run();
        }
        for (Runnable detacher : listenerDetachers) {
            detacher.run();
        }
        listenerDetachers.clear();
    }
}

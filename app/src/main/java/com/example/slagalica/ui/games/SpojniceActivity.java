package com.example.slagalica.ui.games;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.slagalica.R;
import com.example.slagalica.data.model.SpojniceAttempt;
import com.example.slagalica.data.model.SpojnicePuzzle;
import com.example.slagalica.data.model.User;
import com.example.slagalica.data.repository.MatchRepository;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.logic.games.SpojniceLogic;
import com.example.slagalica.ui.games.KorakPoKorakActivity;
import com.example.slagalica.ui.main.MainActivity;
import com.example.slagalica.ui.widget.ScoreBarView;
import com.example.slagalica.util.AvatarProvider;
import com.example.slagalica.util.Constants;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Igra "Spojnice" — KT2, multiplayer 1 na 1.
 *
 * <p>Dve runde po 30 sekundi; prvu rundu započinje player1, drugu player2.
 * Aktivni igrač prolazi kroz svih pet pojmova (po jedan pokušaj za svaki);
 * preostale nepovezane pojmove zatim povezuje drugi igrač u svojoj fazi od
 * 30 sekundi. Svaka tačna veza nosi 2 boda. Pokušaji i faze se sinhronizuju
 * preko Realtime Database, pa pasivni igrač uživo gleda poteze aktivnog.</p>
 */
public class SpojniceActivity extends AppCompatActivity {

    private static final int ROUND_SWITCH_DELAY_MS = 1_800;

    private final MatchRepository matchRepository = MatchRepository.getInstance();
    private final UserRepository userRepository = UserRepository.getInstance();

    // Views
    private final MaterialButton[] leftButtons  = new MaterialButton[Constants.SPOJNICE_PAIRS];
    private final MaterialButton[] rightButtons = new MaterialButton[Constants.SPOJNICE_PAIRS];
    private TextView    tvRoundLabel;
    private TextView    tvTurnStatus;
    private TextView    tvCriterion;
    private ScoreBarView scoreBar;

    private ColorStateList defaultTint;
    private ColorStateList selectedTint;
    private ColorStateList connectedTint;
    private ColorStateList wrongTint;

    // Meč
    private String matchId;
    private String myUid;
    private String opponentUid;
    private String opponentName;
    private boolean isPlayerOne;
    private boolean isFriendly;
    private boolean opponentOnline = true;
    private int myKzzScore;
    private int opponentKzzScore;
    private int myAsocScore, oppAsocScore;
    private int mySkockoScore, oppSkockoScore;
    private int myMojBrojScore, oppMojBrojScore;

    // Stanje igre
    private List<SpojnicePuzzle> rounds;
    private int currentRound = 0;
    private int currentPhase = SpojniceLogic.PHASE_STARTER;
    private int selectedLeftIdx = -1;
    private boolean roundFinished = false;
    private boolean gameEnded = false;
    private final List<Map<Integer, SpojniceAttempt>> roundAttempts = new ArrayList<>();
    private int myConnectedCount = 0;
    private int myMissedCount = 0;

    private CountDownTimer phaseTimer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Runnable> matchDetachers = new ArrayList<>();
    private Runnable roundStateDetacher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spojnice);

        readExtras();
        initViews();
        setupListeners();
        setupBackHandler();
        joinMatch();
    }

    private void readExtras() {
        matchId          = getIntent().getStringExtra(Constants.EXTRA_MATCH_ID);
        opponentUid      = getIntent().getStringExtra(Constants.EXTRA_OPPONENT_UID);
        opponentName     = getIntent().getStringExtra(Constants.EXTRA_OPPONENT_NAME);
        isPlayerOne      = getIntent().getBooleanExtra(Constants.EXTRA_IS_PLAYER_ONE, false);
        isFriendly       = getIntent().getBooleanExtra(Constants.EXTRA_IS_FRIENDLY, false);
        myKzzScore       = getIntent().getIntExtra(Constants.EXTRA_MY_KZZ, 0);
        opponentKzzScore = getIntent().getIntExtra(Constants.EXTRA_OPP_KZZ, 0);
        myAsocScore      = getIntent().getIntExtra(Constants.EXTRA_MY_ASOCIJACIJE, 0);
        oppAsocScore     = getIntent().getIntExtra(Constants.EXTRA_OPP_ASOCIJACIJE, 0);
        mySkockoScore    = getIntent().getIntExtra(Constants.EXTRA_MY_SKOCKO, 0);
        oppSkockoScore   = getIntent().getIntExtra(Constants.EXTRA_OPP_SKOCKO, 0);
        myMojBrojScore   = getIntent().getIntExtra(Constants.EXTRA_MY_MOJ_BROJ, 0);
        oppMojBrojScore  = getIntent().getIntExtra(Constants.EXTRA_OPP_MOJ_BROJ, 0);
        myUid            = userRepository.getCurrentUid();
    }

    private void initViews() {
        tvRoundLabel    = findViewById(R.id.tvRoundLabel);
        tvTurnStatus    = findViewById(R.id.tvTurnStatus);
        tvCriterion     = findViewById(R.id.tvCriterion);
        scoreBar        = findViewById(R.id.scoreBar);

        int[] leftIds  = {R.id.btnLeft0, R.id.btnLeft1, R.id.btnLeft2, R.id.btnLeft3, R.id.btnLeft4};
        int[] rightIds = {R.id.btnRight0, R.id.btnRight1, R.id.btnRight2, R.id.btnRight3, R.id.btnRight4};
        for (int i = 0; i < Constants.SPOJNICE_PAIRS; i++) {
            leftButtons[i]  = findViewById(leftIds[i]);
            rightButtons[i] = findViewById(rightIds[i]);
        }

        defaultTint   = leftButtons[0].getBackgroundTintList();
        selectedTint  = tint(R.color.spojnice_item_selected);
        connectedTint = tint(R.color.spojnice_item_connected);
        wrongTint     = tint(R.color.spojnice_item_wrong);

        tvCriterion.setText(R.string.spojnice_waiting_content);
        tvTurnStatus.setText("");
        setAllButtonsEnabled(false);
        loadPlayerBar();
    }

    /** Popunjava traku sa rezultatom meča — nadimci, avatari i skor pre ove igre (KZZ + Asocijacije + Skočko + Moj broj). */
    private void loadPlayerBar() {
        scoreBar.setOpponentPlayer(AvatarProvider.getDrawableRes(0), opponentName != null ? opponentName : "?");
        scoreBar.setScores(myKzzScore + myAsocScore + mySkockoScore + myMojBrojScore,
                opponentKzzScore + oppAsocScore + oppSkockoScore + oppMojBrojScore);

        userRepository.getOrCreateUser(myUid, new UserRepository.UserCallback() {
            @Override
            public void onSuccess(@NonNull User user) {
                scoreBar.setMyPlayer(AvatarProvider.getDrawableForStored(user.getAvatarUrl()), user.getUsername());
            }
            @Override
            public void onError(@NonNull String message) { /* zadrži podrazumevani prikaz */ }
        });
        if (opponentUid != null) {
            userRepository.getOrCreateUser(opponentUid, new UserRepository.UserCallback() {
                @Override
                public void onSuccess(@NonNull User user) {
                    scoreBar.setOpponentPlayer(AvatarProvider.getDrawableForStored(user.getAvatarUrl()),
                            opponentName != null ? opponentName : user.getUsername());
                }
                @Override
                public void onError(@NonNull String message) { /* zadrži podrazumevani prikaz */ }
            });
        }
    }

    private void setupListeners() {
        for (int i = 0; i < Constants.SPOJNICE_PAIRS; i++) {
            final int idx = i;
            leftButtons[i].setOnClickListener(v -> onLeftClicked(idx));
            rightButtons[i].setOnClickListener(v -> onRightClicked(idx));
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

        matchDetachers.add(matchRepository.listenOpponentPresence(matchId, opponentUid,
                online -> {
                    boolean wasOnline = opponentOnline;
                    opponentOnline = online;
                    if (wasOnline && !online) {
                        Toast.makeText(this, getString(R.string.spojnice_opponent_left),
                                Toast.LENGTH_SHORT).show();
                        // Ne čekamo odsutnog protivnika (specifikacija 3f)
                        if (rounds != null && !roundFinished && isOpponentActive()) {
                            advancePhase();
                        }
                    }
                }));

        matchDetachers.add(matchRepository.listenSpojniceRounds(matchId,
                new MatchRepository.SpojniceRoundsListener() {
            @Override
            public void onRounds(@NonNull List<SpojnicePuzzle> loaded) {
                rounds = loaded;
                startRound(0);
            }

            @Override
            public void onError(@NonNull String message) {
                Toast.makeText(SpojniceActivity.this, message, Toast.LENGTH_LONG).show();
                finish();
            }
        }));
    }

    // =========================================================================
    // Upravljanje rundama i fazama
    // =========================================================================

    private void startRound(int round) {
        currentRound = round;
        currentPhase = SpojniceLogic.PHASE_STARTER;
        selectedLeftIdx = -1;
        roundFinished = false;
        while (roundAttempts.size() <= round) {
            roundAttempts.add(new HashMap<>());
        }

        SpojnicePuzzle puzzle = rounds.get(round);
        tvRoundLabel.setText(getString(R.string.spojnice_round_label, round + 1));
        tvCriterion.setText(getString(R.string.spojnice_criterion_label, puzzle.getCriterion()));
        for (int i = 0; i < Constants.SPOJNICE_PAIRS; i++) {
            leftButtons[i].setText(puzzle.getLeftItems().get(i));
            rightButtons[i].setText(puzzle.getRightItems().get(i));
        }

        if (roundStateDetacher != null) {
            roundStateDetacher.run();
        }
        roundStateDetacher = matchRepository.listenSpojniceState(matchId, round,
                this::onRoundStateChanged);

        startPhaseTimer();
        renderBoard();
    }

    /** Okida se na svaku promenu faze ili pokušaja u Realtime Database. */
    private void onRoundStateChanged(int phase, @NonNull Map<Integer, SpojniceAttempt> attempts) {
        if (gameEnded) {
            return;
        }
        roundAttempts.set(currentRound, attempts);
        scoreBar.setScores(myKzzScore + myAsocScore + mySkockoScore + myMojBrojScore + mySpojniceScore(),
                opponentKzzScore + oppAsocScore + oppSkockoScore + oppMojBrojScore + opponentSpojniceScore());

        boolean phaseChanged = phase != currentPhase;
        currentPhase = phase;
        if (phaseChanged) {
            selectedLeftIdx = -1;
            if (phase == SpojniceLogic.PHASE_SECOND) {
                startPhaseTimer();
            }
        }

        renderBoard();

        if (phase == SpojniceLogic.PHASE_DONE) {
            onRoundDone();
            return;
        }

        // Ako je protivnik aktivan a napustio je meč, fazu preskačemo odmah
        if (!opponentOnline && isOpponentActive()) {
            advancePhase();
            return;
        }

        // Aktivni igrač proverava da li je njegova faza završena
        if (isMyTurn() && isPhaseComplete(phase, attempts)) {
            advancePhase();
        }
    }

    private boolean isPhaseComplete(int phase, Map<Integer, SpojniceAttempt> attempts) {
        if (SpojniceLogic.allConnected(attempts)) {
            return true;
        }
        if (phase == SpojniceLogic.PHASE_STARTER) {
            // Igrač koji počinje prošao je kroz svih pet pojmova
            return SpojniceLogic.attemptedCount(attempts) >= Constants.SPOJNICE_PAIRS;
        }
        // Druga faza: nema više pojmova dostupnih drugom igraču
        for (int i = 0; i < Constants.SPOJNICE_PAIRS; i++) {
            if (isAvailableInSecondPhase(i, attempts)) {
                return false;
            }
        }
        return true;
    }

    /** Pojam je u drugoj fazi dostupan ako nije povezan i drugi igrač ga još nije pokušao. */
    private boolean isAvailableInSecondPhase(int leftIdx, Map<Integer, SpojniceAttempt> attempts) {
        SpojniceAttempt attempt = attempts.get(leftIdx);
        if (attempt == null) {
            return true; // prvi igrač nije stigao do njega
        }
        return !attempt.isOk() && starterUid().equals(attempt.getUid());
    }

    private void advancePhase() {
        if (roundFinished) {
            return;
        }
        Map<Integer, SpojniceAttempt> attempts = roundAttempts.get(currentRound);
        if (currentPhase == SpojniceLogic.PHASE_STARTER
                && !SpojniceLogic.allConnected(attempts)
                && hasRemainingForSecondPhase(attempts)) {
            matchRepository.setSpojnicePhase(matchId, currentRound, SpojniceLogic.PHASE_SECOND);
        } else {
            matchRepository.setSpojnicePhase(matchId, currentRound, SpojniceLogic.PHASE_DONE);
        }
    }

    private boolean hasRemainingForSecondPhase(Map<Integer, SpojniceAttempt> attempts) {
        for (int i = 0; i < Constants.SPOJNICE_PAIRS; i++) {
            if (isAvailableInSecondPhase(i, attempts)) {
                return true;
            }
        }
        return false;
    }

    private void onRoundDone() {
        if (roundFinished) {
            return;
        }
        roundFinished = true;
        cancelPhaseTimer();
        setAllButtonsEnabled(false);
        tvTurnStatus.setText(getString(R.string.spojnice_round_end, mySpojniceScore()));

        if (currentRound < Constants.SPOJNICE_ROUNDS - 1) {
            handler.postDelayed(() -> startRound(currentRound + 1), ROUND_SWITCH_DELAY_MS);
        } else {
            handler.postDelayed(this::endGame, ROUND_SWITCH_DELAY_MS);
        }
    }

    // =========================================================================
    // Potezi aktivnog igrača
    // =========================================================================

    private void onLeftClicked(int idx) {
        if (!isMyTurn() || roundFinished) {
            return;
        }
        Map<Integer, SpojniceAttempt> attempts = roundAttempts.get(currentRound);
        if (!isLeftAvailable(idx, attempts)) {
            return;
        }
        if (selectedLeftIdx == idx) {
            selectedLeftIdx = -1; // dvoklik poništava selekciju
        } else {
            selectedLeftIdx = idx;
        }
        renderBoard();
    }

    private void onRightClicked(int rightIdx) {
        if (!isMyTurn() || roundFinished || selectedLeftIdx == -1) {
            return;
        }
        Map<Integer, SpojniceAttempt> attempts = roundAttempts.get(currentRound);
        if (isRightConnected(rightIdx, attempts)) {
            return;
        }
        int leftIdx = selectedLeftIdx;
        selectedLeftIdx = -1;

        boolean ok = rounds.get(currentRound).correctRightFor(leftIdx) == rightIdx;
        if (ok) {
            myConnectedCount++;
            Toast.makeText(this, getString(R.string.spojnice_correct), Toast.LENGTH_SHORT).show();
        } else {
            myMissedCount++;
            Toast.makeText(this, getString(R.string.spojnice_wrong), Toast.LENGTH_SHORT).show();
        }
        // Upis u RTDB — obe strane će potez videti kroz onRoundStateChanged
        matchRepository.submitSpojniceAttempt(matchId, currentRound, leftIdx,
                new SpojniceAttempt(myUid, rightIdx, ok));
    }

    // =========================================================================
    // Iscrtavanje table iz sinhronizovanog stanja
    // =========================================================================

    private void renderBoard() {
        Map<Integer, SpojniceAttempt> attempts = roundAttempts.get(currentRound);
        boolean myTurn = isMyTurn();

        for (int i = 0; i < Constants.SPOJNICE_PAIRS; i++) {
            SpojniceAttempt attempt = attempts.get(i);
            if (attempt != null && attempt.isOk()) {
                // Povezan par — zeleno i zaključano
                leftButtons[i].setBackgroundTintList(connectedTint);
                leftButtons[i].setEnabled(false);
            } else if (isLeftAvailable(i, attempts)) {
                leftButtons[i].setBackgroundTintList(
                        i == selectedLeftIdx ? selectedTint : defaultTint);
                leftButtons[i].setEnabled(myTurn);
            } else {
                // Promašen pokušaj — crveno i zaključano za tekuću fazu
                leftButtons[i].setBackgroundTintList(attempt != null ? wrongTint : defaultTint);
                leftButtons[i].setEnabled(false);
            }
        }

        for (int j = 0; j < Constants.SPOJNICE_PAIRS; j++) {
            if (isRightConnected(j, attempts)) {
                rightButtons[j].setBackgroundTintList(connectedTint);
                rightButtons[j].setEnabled(false);
            } else {
                rightButtons[j].setBackgroundTintList(defaultTint);
                rightButtons[j].setEnabled(myTurn);
            }
        }

        if (roundFinished) {
            tvTurnStatus.setText(getString(R.string.spojnice_round_end, mySpojniceScore()));
        } else if (myTurn) {
            tvTurnStatus.setText(R.string.spojnice_your_turn);
        } else {
            tvTurnStatus.setText(getString(R.string.spojnice_opponent_turn,
                    opponentName != null ? opponentName : "?"));
        }
    }

    private boolean isLeftAvailable(int leftIdx, Map<Integer, SpojniceAttempt> attempts) {
        SpojniceAttempt attempt = attempts.get(leftIdx);
        if (currentPhase == SpojniceLogic.PHASE_STARTER) {
            return attempt == null;
        }
        if (currentPhase == SpojniceLogic.PHASE_SECOND) {
            return isAvailableInSecondPhase(leftIdx, attempts);
        }
        return false;
    }

    private boolean isRightConnected(int rightIdx, Map<Integer, SpojniceAttempt> attempts) {
        for (SpojniceAttempt attempt : attempts.values()) {
            if (attempt.isOk() && attempt.getRightIndex() == rightIdx) {
                return true;
            }
        }
        return false;
    }

    // =========================================================================
    // Tajmer faze (30 sekundi po fazi)
    // =========================================================================

    private void startPhaseTimer() {
        cancelPhaseTimer();
        int totalSeconds = Constants.SPOJNICE_ROUND_TIME_MS / 1000;
        scoreBar.setTimeLeft(totalSeconds);

        phaseTimer = new CountDownTimer(Constants.SPOJNICE_ROUND_TIME_MS, 1_000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsLeft = (int) (millisUntilFinished / 1000) + 1;
                scoreBar.setTimeLeft(secondsLeft);
            }

            @Override
            public void onFinish() {
                scoreBar.setTimeLeft(0);
                // Istek vremena prijavljuje aktivni igrač; ako je aktivni igrač
                // napustio meč, fazu zatvara preostali igrač
                if (!roundFinished && (isMyTurn() || !opponentOnline)) {
                    advancePhase();
                }
            }
        }.start();
    }

    private void cancelPhaseTimer() {
        if (phaseTimer != null) {
            phaseTimer.cancel();
            phaseTimer = null;
        }
    }

    // =========================================================================
    // Kraj igre — rezultat meča
    // =========================================================================

    private void endGame() {
        if (gameEnded) {
            return;
        }
        gameEnded = true;

        int myScore = mySpojniceScore();
        int opponentScore = opponentSpojniceScore();

        matchRepository.setGameResult(matchId, MatchRepository.GAME_SPOJNICE, myUid, myScore);
        userRepository.recordSpojniceResult(myUid, myConnectedCount, myMissedCount, myScore);

        Intent intent = new Intent(this, KorakPoKorakActivity.class);
        intent.putExtra(Constants.EXTRA_MATCH_ID, matchId);
        intent.putExtra(Constants.EXTRA_IS_PLAYER_ONE, isPlayerOne);
        intent.putExtra(Constants.EXTRA_OPPONENT_UID, opponentUid);
        intent.putExtra(Constants.EXTRA_OPPONENT_NAME, opponentName);
        intent.putExtra(Constants.EXTRA_MY_KZZ, myKzzScore);
        intent.putExtra(Constants.EXTRA_OPP_KZZ, opponentKzzScore);
        intent.putExtra(Constants.EXTRA_MY_ASOCIJACIJE, myAsocScore);
        intent.putExtra(Constants.EXTRA_OPP_ASOCIJACIJE, oppAsocScore);
        intent.putExtra(Constants.EXTRA_MY_SKOCKO, mySkockoScore);
        intent.putExtra(Constants.EXTRA_OPP_SKOCKO, oppSkockoScore);
        intent.putExtra(Constants.EXTRA_MY_MOJ_BROJ, myMojBrojScore);
        intent.putExtra(Constants.EXTRA_OPP_MOJ_BROJ, oppMojBrojScore);
        intent.putExtra(Constants.EXTRA_MY_SPOJNICE, myScore);
        intent.putExtra(Constants.EXTRA_OPP_SPOJNICE, opponentScore);
        intent.putExtra(Constants.EXTRA_IS_FRIENDLY, isFriendly);
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

    private String starterUid() {
        String player1Uid = isPlayerOne ? myUid : opponentUid;
        String player2Uid = isPlayerOne ? opponentUid : myUid;
        return SpojniceLogic.starterUid(currentRound, player1Uid, player2Uid);
    }

    private boolean isMyTurn() {
        if (roundFinished || currentPhase == SpojniceLogic.PHASE_DONE) {
            return false;
        }
        boolean iAmStarter = myUid.equals(starterUid());
        return (currentPhase == SpojniceLogic.PHASE_STARTER) == iAmStarter;
    }

    private boolean isOpponentActive() {
        if (roundFinished || currentPhase == SpojniceLogic.PHASE_DONE) {
            return false;
        }
        return !isMyTurn();
    }

    private int mySpojniceScore() {
        return totalScoreFor(myUid);
    }

    private int opponentSpojniceScore() {
        return totalScoreFor(opponentUid);
    }

    private int totalScoreFor(String uid) {
        int total = 0;
        for (Map<Integer, SpojniceAttempt> attempts : roundAttempts) {
            total += SpojniceLogic.pointsFor(uid, attempts);
        }
        return total;
    }

    private void setAllButtonsEnabled(boolean enabled) {
        for (int i = 0; i < Constants.SPOJNICE_PAIRS; i++) {
            leftButtons[i].setEnabled(enabled);
            rightButtons[i].setEnabled(enabled);
        }
    }

    private ColorStateList tint(int colorResId) {
        return ColorStateList.valueOf(ContextCompat.getColor(this, colorResId));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        cancelPhaseTimer();
        if (roundStateDetacher != null) {
            roundStateDetacher.run();
        }
        for (Runnable detacher : matchDetachers) {
            detacher.run();
        }
        matchDetachers.clear();
    }
}

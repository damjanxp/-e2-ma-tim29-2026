package com.example.slagalica.ui.games;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.example.slagalica.data.repository.MatchRepository;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.logic.games.SkockoLogic;
import com.example.slagalica.util.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Igra "Skočko" — KT2, multiplayer 1 na 1.
 *
 * <p>2 runde po 50 sekundi. Prvu rundu igra player1 (aktivni), drugu player2.
 * Aktivni igrač generiše tajnu kombinaciju i upisuje je u RTDB, pa ima 6
 * pokušaja da je pogodi. Ako ne pogodi, protivnik dobija jednu šansu od 10s.
 * Oba igrača slušaju stanje runde ({@link MatchRepository#listenSkockoState})
 * i vide iste redove pokušaja i hint-ove uživo.</p>
 *
 * <p>Bodovanje je determinističko: {@link SkockoLogic#mainScore(int)} za
 * aktivnog igrača, {@link SkockoLogic#OPPONENT_SCORE} za protivnika.</p>
 */
public class SkockoActivity extends AppCompatActivity {

    private static final int MAIN_SECONDS   = 50;
    private static final int CHANCE_SECONDS = 10;
    private static final int NUM_ROUNDS     = 2;
    private static final long ROUND_DELAY_MS = 1_800;

    // Drawable IDs za svaki simbol (indeks odgovara SkockoLogic konstantama 0-5)
    private static final int[] SYMBOL_DRAW = {
        R.drawable.ic_symbol_smiley,
        R.drawable.ic_symbol_square,
        R.drawable.ic_symbol_circle,
        R.drawable.ic_symbol_heart,
        R.drawable.ic_symbol_triangle,
        R.drawable.ic_symbol_star
    };

    private static final int HINT_DRAW_RED    = R.drawable.bg_skocko_hint_red;
    private static final int HINT_DRAW_YELLOW = R.drawable.bg_skocko_hint_yellow;
    private static final int HINT_DRAW_EMPTY  = R.drawable.bg_skocko_hint_empty;

    // ─── Views ───────────────────────────────────────────────────────────────
    private TextView     tvRound, tvScore, tvOpponentScore, tvTurnStatus, tvInstruction, tvTimer;
    private ProgressBar  pbTimer;
    private ImageView[][] slotViews;   // [row 0-5][col 0-3]
    private View[][]      hintViews;   // [row 0-5][hint 0-3]
    private ImageView[]  opponentSlots;
    private View[]        opponentHints;
    private ImageView[]  solutionSlots;
    private LinearLayout rowOpponentGuess;
    private final List<View> symbolButtons = new ArrayList<>();
    private View btnClear;

    // ─── Meč ─────────────────────────────────────────────────────────────────
    private final MatchRepository matchRepository = MatchRepository.getInstance();
    private final UserRepository  userRepository  = UserRepository.getInstance();

    private String  matchId;
    private String  myUid;
    private String  opponentUid;
    private String  opponentName;
    private boolean isPlayerOne;
    private boolean opponentOnline = true;

    private int myKzzScore, oppKzzScore;
    private int myAsocScore, oppAsocScore;

    // ─── Stanje igre ─────────────────────────────────────────────────────────
    private int     currentRound       = 0;
    private boolean gameEnded          = false;
    private int     myTotalScore       = 0;
    private int     opponentTotalScore = 0;

    private String  currentPhase   = "";
    private int[]   secret;
    private final List<Integer> currentInput = new ArrayList<>();
    private int     localAttemptsCount   = 0;
    private int     mainCorrectAttemptIdx = -1;
    private boolean opponentAttemptCorrect = false;
    private boolean roundResolved = false;
    private boolean guessGiven    = false;  // aktivni igrač iskoristio sve pokušaje ili pogodio
    private boolean chanceGiven   = false;  // pasivni dao šansu ili istekla

    private final Random random = new Random();

    // ─── Timeri ──────────────────────────────────────────────────────────────
    private CountDownTimer turnTimer;
    private CountDownTimer displayTimer;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Runnable> matchDetachers = new ArrayList<>();
    private Runnable roundStateDetacher;

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_skocko);

        readExtras();
        initViews();
        setupBackHandler();
        joinMatch();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        cancelTimers();
        detachAll();
    }

    // =========================================================================
    // Init
    // =========================================================================

    private void readExtras() {
        matchId       = getIntent().getStringExtra(Constants.EXTRA_MATCH_ID);
        opponentUid   = getIntent().getStringExtra(Constants.EXTRA_OPPONENT_UID);
        opponentName  = getIntent().getStringExtra(Constants.EXTRA_OPPONENT_NAME);
        isPlayerOne   = getIntent().getBooleanExtra(Constants.EXTRA_IS_PLAYER_ONE, false);
        myKzzScore    = getIntent().getIntExtra(Constants.EXTRA_MY_KZZ, 0);
        oppKzzScore   = getIntent().getIntExtra(Constants.EXTRA_OPP_KZZ, 0);
        myAsocScore   = getIntent().getIntExtra(Constants.EXTRA_MY_ASOCIJACIJE, 0);
        oppAsocScore  = getIntent().getIntExtra(Constants.EXTRA_OPP_ASOCIJACIJE, 0);
        myUid         = userRepository.getCurrentUid();
    }

    private void initViews() {
        tvRound          = findViewById(R.id.tvRound);
        tvScore          = findViewById(R.id.tvScore);
        tvOpponentScore  = findViewById(R.id.tvOpponentScore);
        tvTurnStatus     = findViewById(R.id.tvTurnStatus);
        tvInstruction    = findViewById(R.id.tvInstruction);
        tvTimer          = findViewById(R.id.tvTimer);
        pbTimer          = findViewById(R.id.pbTimer);
        rowOpponentGuess = findViewById(R.id.rowOpponentGuess);

        slotViews = new ImageView[][]{
            { iv(R.id.ivRow1Col1), iv(R.id.ivRow1Col2), iv(R.id.ivRow1Col3), iv(R.id.ivRow1Col4) },
            { iv(R.id.ivRow2Col1), iv(R.id.ivRow2Col2), iv(R.id.ivRow2Col3), iv(R.id.ivRow2Col4) },
            { iv(R.id.ivRow3Col1), iv(R.id.ivRow3Col2), iv(R.id.ivRow3Col3), iv(R.id.ivRow3Col4) },
            { iv(R.id.ivRow4Col1), iv(R.id.ivRow4Col2), iv(R.id.ivRow4Col3), iv(R.id.ivRow4Col4) },
            { iv(R.id.ivRow5Col1), iv(R.id.ivRow5Col2), iv(R.id.ivRow5Col3), iv(R.id.ivRow5Col4) },
            { iv(R.id.ivRow6Col1), iv(R.id.ivRow6Col2), iv(R.id.ivRow6Col3), iv(R.id.ivRow6Col4) },
        };

        hintViews = new View[][]{
            { v(R.id.vRow1Hint1), v(R.id.vRow1Hint2), v(R.id.vRow1Hint3), v(R.id.vRow1Hint4) },
            { v(R.id.vRow2Hint1), v(R.id.vRow2Hint2), v(R.id.vRow2Hint3), v(R.id.vRow2Hint4) },
            { v(R.id.vRow3Hint1), v(R.id.vRow3Hint2), v(R.id.vRow3Hint3), v(R.id.vRow3Hint4) },
            { v(R.id.vRow4Hint1), v(R.id.vRow4Hint2), v(R.id.vRow4Hint3), v(R.id.vRow4Hint4) },
            { v(R.id.vRow5Hint1), v(R.id.vRow5Hint2), v(R.id.vRow5Hint3), v(R.id.vRow5Hint4) },
            { v(R.id.vRow6Hint1), v(R.id.vRow6Hint2), v(R.id.vRow6Hint3), v(R.id.vRow6Hint4) },
        };

        opponentSlots = new ImageView[]{
            iv(R.id.ivOpponentCol1), iv(R.id.ivOpponentCol2),
            iv(R.id.ivOpponentCol3), iv(R.id.ivOpponentCol4)
        };
        opponentHints = new View[]{
            v(R.id.vOpponentHint1), v(R.id.vOpponentHint2),
            v(R.id.vOpponentHint3), v(R.id.vOpponentHint4)
        };
        solutionSlots = new ImageView[]{
            iv(R.id.ivSolutionCol1), iv(R.id.ivSolutionCol2),
            iv(R.id.ivSolutionCol3), iv(R.id.ivSolutionCol4)
        };

        int[] symbolBtnIds = {
            R.id.btnSymbolSmiley, R.id.btnSymbolSquare, R.id.btnSymbolCircle,
            R.id.btnSymbolHeart,  R.id.btnSymbolTriangle, R.id.btnSymbolStar
        };
        for (int i = 0; i < symbolBtnIds.length; i++) {
            final int sym = i;
            View btn = findViewById(symbolBtnIds[i]);
            btn.setOnClickListener(v2 -> onSymbolTapped(sym));
            symbolButtons.add(btn);
        }

        btnClear = findViewById(R.id.btnClear);
        btnClear.setOnClickListener(v2 -> onClear());
        findViewById(R.id.btnGiveUp).setOnClickListener(v2 -> confirmGiveUp());

        setInputEnabled(false);
        tvTurnStatus.setText(R.string.skocko_waiting);
    }

    private ImageView iv(int id) { return findViewById(id); }
    private View v(int id) { return findViewById(id); }

    private void setupBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { confirmGiveUp(); }
        });
    }

    // =========================================================================
    // Prisustvo i inicijalizacija meča
    // =========================================================================

    private void joinMatch() {
        if (matchId == null || myUid == null) {
            Toast.makeText(this, "Greška pri učitavanju meča.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        matchRepository.setPresence(matchId, myUid);

        matchDetachers.add(matchRepository.listenOpponentPresence(matchId, opponentUid,
                online -> {
                    boolean wasOnline = opponentOnline;
                    opponentOnline = online;
                    if (wasOnline && !online) {
                        Toast.makeText(this, opponentName + " je napustio igru.",
                                Toast.LENGTH_SHORT).show();
                        if (!roundResolved && isOpponentTurnCurrent()) {
                            advancePhase();
                        }
                    }
                }));

        startRound(0);
    }

    // =========================================================================
    // Upravljanje rundama
    // =========================================================================

    private void startRound(int round) {
        currentRound          = round;
        currentPhase          = "";
        secret                = null;
        currentInput.clear();
        localAttemptsCount    = 0;
        mainCorrectAttemptIdx = -1;
        opponentAttemptCorrect = false;
        roundResolved         = false;
        guessGiven            = false;
        chanceGiven           = false;

        tvRound.setText(getString(R.string.skocko_round, round + 1));
        tvTurnStatus.setText(R.string.skocko_waiting);
        tvInstruction.setText(R.string.skocko_waiting);
        resetBoard();
        updateScoreDisplay();
        setInputEnabled(false);

        if (roundStateDetacher != null) { roundStateDetacher.run(); roundStateDetacher = null; }

        roundStateDetacher = matchRepository.listenSkockoState(
                matchId, round, this::onRoundStateChanged);

        if (isActivePlayerForRound(round)) {
            secret = SkockoLogic.randomSecret(random);
            matchRepository.writeSkockoSecret(matchId, round, secret);
            matchRepository.setSkockoPhase(matchId, round, Constants.SKOCKO_PHASE_MAIN);
        }
    }

    private void resetBoard() {
        for (int r = 0; r < SkockoLogic.MAX_ATTEMPTS; r++) {
            for (int c = 0; c < SkockoLogic.COMBO_LEN; c++) {
                slotViews[r][c].setImageDrawable(null);
                hintViews[r][c].setBackgroundResource(HINT_DRAW_EMPTY);
            }
        }
        for (int c = 0; c < SkockoLogic.COMBO_LEN; c++) {
            opponentSlots[c].setImageDrawable(null);
            opponentHints[c].setBackgroundResource(HINT_DRAW_EMPTY);
            solutionSlots[c].setImageDrawable(null);
        }
        rowOpponentGuess.setAlpha(0.35f);
    }

    // =========================================================================
    // Listener stanja runde (oba igrača ga primaju)
    // =========================================================================

    private void onRoundStateChanged(@Nullable int[] stateSecret, @NonNull String phase,
                                      @NonNull Map<Integer, MatchRepository.SkockoAttemptData> mainAttempts,
                                      @Nullable MatchRepository.SkockoAttemptData opponentAttempt) {
        if (gameEnded || roundResolved) return;
        if (stateSecret != null) secret = stateSecret;

        mainCorrectAttemptIdx = -1;
        for (int i = 0; i < SkockoLogic.MAX_ATTEMPTS; i++) {
            MatchRepository.SkockoAttemptData a = mainAttempts.get(i);
            if (a == null) continue;
            int[] hints = a.hintsArray();
            applyGuessAndHints(slotViews[i], hintViews[i], a.guessArray(), hints);
            if (mainCorrectAttemptIdx < 0 && SkockoLogic.isCorrect(hints)) mainCorrectAttemptIdx = i;
        }
        localAttemptsCount = mainAttempts.size();

        opponentAttemptCorrect = false;
        if (opponentAttempt != null) {
            int[] hints = opponentAttempt.hintsArray();
            applyGuessAndHints(opponentSlots, opponentHints, opponentAttempt.guessArray(), hints);
            opponentAttemptCorrect = SkockoLogic.isCorrect(hints);
        }

        if (Constants.SKOCKO_PHASE_DONE.equals(phase)) {
            onRoundDone();
            return;
        }

        if (!phase.isEmpty() && !phase.equals(currentPhase)) {
            currentPhase = phase;
            cancelTimers();
            if (Constants.SKOCKO_PHASE_MAIN.equals(phase)) {
                renderTurnStatus();
                startMainPhaseTimer();
            } else if (Constants.SKOCKO_PHASE_CHANCE.equals(phase)) {
                rowOpponentGuess.setAlpha(1.0f);
                renderTurnStatus();
                startChancePhaseTimer();
            }
        }

        updateInputEnabledState();

        if (!opponentOnline && isOpponentTurnCurrent()) {
            advancePhase();
        }
    }

    // =========================================================================
    // Faza MAIN — aktivni igrač ima 50s i do 6 pokušaja
    // =========================================================================

    private void startMainPhaseTimer() {
        pbTimer.setMax(MAIN_SECONDS);
        pbTimer.setProgress(MAIN_SECONDS);

        boolean iAmActive = isActivePlayerForRound(currentRound);
        if (iAmActive) {
            turnTimer = new CountDownTimer((long) MAIN_SECONDS * 1000, 1000) {
                @Override
                public void onTick(long ms) {
                    int s = (int) (ms / 1000) + 1;
                    pbTimer.setProgress(Math.min(s, MAIN_SECONDS));
                    tvTimer.setText(getString(R.string.skocko_time_left, s));
                }

                @Override
                public void onFinish() {
                    pbTimer.setProgress(0);
                    tvTimer.setText(getString(R.string.skocko_time_left, 0));
                    if (!guessGiven && !roundResolved) {
                        guessGiven = true;
                        setInputEnabled(false);
                        matchRepository.setSkockoPhase(matchId, currentRound, Constants.SKOCKO_PHASE_CHANCE);
                    }
                }
            }.start();
        } else {
            displayTimer = new CountDownTimer((long) MAIN_SECONDS * 1000, 1000) {
                @Override
                public void onTick(long ms) {
                    int s = (int) (ms / 1000) + 1;
                    pbTimer.setProgress(Math.min(s, MAIN_SECONDS));
                    tvTimer.setText(getString(R.string.skocko_time_left, s));
                }
                @Override
                public void onFinish() {
                    pbTimer.setProgress(0);
                    tvTimer.setText(getString(R.string.skocko_time_left, 0));
                }
            }.start();
        }
    }

    // =========================================================================
    // Faza OPPONENT_CHANCE — pasivni igrač ima 10s za jedan pokušaj
    // =========================================================================

    private void startChancePhaseTimer() {
        pbTimer.setMax(CHANCE_SECONDS);
        pbTimer.setProgress(CHANCE_SECONDS);

        boolean iAmPassive = !isActivePlayerForRound(currentRound);
        if (iAmPassive) {
            turnTimer = new CountDownTimer((long) CHANCE_SECONDS * 1000, 250) {
                @Override
                public void onTick(long ms) {
                    int s = (int) Math.ceil(ms / 1000.0);
                    pbTimer.setProgress(Math.min(s, CHANCE_SECONDS));
                    tvTimer.setText(getString(R.string.skocko_time_left, s));
                }

                @Override
                public void onFinish() {
                    pbTimer.setProgress(0);
                    tvTimer.setText(getString(R.string.skocko_time_left, 0));
                    if (!chanceGiven && !roundResolved) {
                        chanceGiven = true;
                        setInputEnabled(false);
                        matchRepository.setSkockoPhase(matchId, currentRound, Constants.SKOCKO_PHASE_DONE);
                    }
                }
            }.start();
        } else {
            displayTimer = new CountDownTimer((long) CHANCE_SECONDS * 1000, 250) {
                @Override
                public void onTick(long ms) {
                    int s = (int) Math.ceil(ms / 1000.0);
                    pbTimer.setProgress(Math.min(s, CHANCE_SECONDS));
                    tvTimer.setText(getString(R.string.skocko_time_left, s));
                }
                @Override
                public void onFinish() {
                    pbTimer.setProgress(0);
                    tvTimer.setText(getString(R.string.skocko_time_left, 0));
                }
            }.start();
        }
    }

    // =========================================================================
    // Unos igrača
    // =========================================================================

    private void onSymbolTapped(int symbol) {
        if (!isMyTurnToAct()) return;
        if (currentInput.size() >= SkockoLogic.COMBO_LEN) return;

        currentInput.add(symbol);
        refreshActiveRowPreview();

        if (currentInput.size() == SkockoLogic.COMBO_LEN) {
            submitGuess();
        }
    }

    private void onClear() {
        if (!isMyTurnToAct()) return;
        if (currentInput.isEmpty()) return;
        currentInput.remove(currentInput.size() - 1);
        refreshActiveRowPreview();
    }

    private void refreshActiveRowPreview() {
        ImageView[] slots = Constants.SKOCKO_PHASE_CHANCE.equals(currentPhase)
                ? opponentSlots
                : slotViews[Math.min(localAttemptsCount, SkockoLogic.MAX_ATTEMPTS - 1)];
        for (int c = 0; c < SkockoLogic.COMBO_LEN; c++) {
            if (c < currentInput.size()) {
                slots[c].setImageResource(SYMBOL_DRAW[currentInput.get(c)]);
            } else {
                slots[c].setImageDrawable(null);
            }
        }
    }

    private void submitGuess() {
        if (secret == null) return;
        int[] guess = new int[SkockoLogic.COMBO_LEN];
        for (int i = 0; i < SkockoLogic.COMBO_LEN; i++) guess[i] = currentInput.get(i);
        currentInput.clear();

        int[] hints = SkockoLogic.computeHints(guess, secret);
        setInputEnabled(false);

        if (Constants.SKOCKO_PHASE_MAIN.equals(currentPhase)) {
            int attemptIdx = localAttemptsCount;
            matchRepository.submitSkockoMainAttempt(matchId, currentRound, attemptIdx, guess, hints);

            if (SkockoLogic.isCorrect(hints)) {
                guessGiven = true;
                cancelTimers();
                matchRepository.setSkockoPhase(matchId, currentRound, Constants.SKOCKO_PHASE_DONE);
            } else if (attemptIdx + 1 >= SkockoLogic.MAX_ATTEMPTS) {
                guessGiven = true;
                cancelTimers();
                matchRepository.setSkockoPhase(matchId, currentRound, Constants.SKOCKO_PHASE_CHANCE);
            }
        } else if (Constants.SKOCKO_PHASE_CHANCE.equals(currentPhase)) {
            matchRepository.submitSkockoOpponentAttempt(matchId, currentRound, guess, hints);
            chanceGiven = true;
            cancelTimers();
            matchRepository.setSkockoPhase(matchId, currentRound, Constants.SKOCKO_PHASE_DONE);
        }
    }

    // =========================================================================
    // Prelaz faze (ako protivnik napusti)
    // =========================================================================

    private void advancePhase() {
        if (roundResolved) return;
        cancelTimers();
        if (Constants.SKOCKO_PHASE_MAIN.equals(currentPhase)) {
            if (!guessGiven) {
                guessGiven = true;
                matchRepository.setSkockoPhase(matchId, currentRound, Constants.SKOCKO_PHASE_CHANCE);
            }
        } else if (Constants.SKOCKO_PHASE_CHANCE.equals(currentPhase)) {
            if (!chanceGiven) {
                chanceGiven = true;
                matchRepository.setSkockoPhase(matchId, currentRound, Constants.SKOCKO_PHASE_DONE);
            }
        }
    }

    // =========================================================================
    // Kraj runde — deterministično bodovanje
    // =========================================================================

    private void onRoundDone() {
        if (roundResolved) return;
        roundResolved = true;
        cancelTimers();
        setInputEnabled(false);

        boolean iAmActive = isActivePlayerForRound(currentRound);
        if (mainCorrectAttemptIdx >= 0) {
            int pts = SkockoLogic.mainScore(mainCorrectAttemptIdx);
            if (iAmActive) myTotalScore += pts; else opponentTotalScore += pts;
        } else if (opponentAttemptCorrect) {
            int pts = SkockoLogic.OPPONENT_SCORE;
            if (!iAmActive) myTotalScore += pts; else opponentTotalScore += pts;
        }
        updateScoreDisplay();
        revealSolution();
        tvTurnStatus.setText(R.string.skocko_round_done);

        boolean isLast = currentRound >= NUM_ROUNDS - 1;
        handler.postDelayed(() -> {
            if (gameEnded) return;
            if (isLast) endGame(); else startRound(currentRound + 1);
        }, ROUND_DELAY_MS);
    }

    private void revealSolution() {
        if (secret == null) return;
        for (int c = 0; c < SkockoLogic.COMBO_LEN; c++) {
            solutionSlots[c].setImageResource(SYMBOL_DRAW[secret[c]]);
        }
    }

    // =========================================================================
    // Kraj igre — prelazak na MojBrojActivity
    // =========================================================================

    private void endGame() {
        if (gameEnded) return;
        gameEnded = true;

        matchRepository.setGameResult(matchId, Constants.GAME_SKOCKO, myUid, myTotalScore);

        Intent intent = new Intent(this, MojBrojActivity.class);
        intent.putExtra(Constants.EXTRA_MATCH_ID, matchId);
        intent.putExtra(Constants.EXTRA_IS_PLAYER_ONE, isPlayerOne);
        intent.putExtra(Constants.EXTRA_OPPONENT_UID, opponentUid);
        intent.putExtra(Constants.EXTRA_OPPONENT_NAME, opponentName);
        intent.putExtra(Constants.EXTRA_MY_KZZ, myKzzScore);
        intent.putExtra(Constants.EXTRA_OPP_KZZ, oppKzzScore);
        intent.putExtra(Constants.EXTRA_MY_ASOCIJACIJE, myAsocScore);
        intent.putExtra(Constants.EXTRA_OPP_ASOCIJACIJE, oppAsocScore);
        intent.putExtra(Constants.EXTRA_MY_SKOCKO, myTotalScore);
        intent.putExtra(Constants.EXTRA_OPP_SKOCKO, opponentTotalScore);
        startActivity(intent);
        finish();
    }

    // =========================================================================
    // Predaja
    // =========================================================================

    private void confirmGiveUp() {
        new AlertDialog.Builder(this)
                .setTitle("Da li si siguran?")
                .setMessage("Igra će biti izgubljena.")
                .setPositiveButton("Predaj", (d, w) -> {
                    gameEnded = true;
                    matchRepository.leaveMatch(matchId, myUid);
                    finish();
                })
                .setNegativeButton("Nastavi", null)
                .show();
    }

    // =========================================================================
    // UI helpers
    // =========================================================================

    private boolean isActivePlayerForRound(int round) {
        return (isPlayerOne && round == 0) || (!isPlayerOne && round == 1);
    }

    private boolean isMyTurnToAct() {
        if (roundResolved) return false;
        if (Constants.SKOCKO_PHASE_MAIN.equals(currentPhase)) {
            return isActivePlayerForRound(currentRound) && !guessGiven;
        }
        if (Constants.SKOCKO_PHASE_CHANCE.equals(currentPhase)) {
            return !isActivePlayerForRound(currentRound) && !chanceGiven;
        }
        return false;
    }

    /** Da li je protivnik onaj koji trenutno treba da radi akciju u tekućoj fazi. */
    private boolean isOpponentTurnCurrent() {
        if (roundResolved) return false;
        if (Constants.SKOCKO_PHASE_MAIN.equals(currentPhase)) return !isActivePlayerForRound(currentRound);
        if (Constants.SKOCKO_PHASE_CHANCE.equals(currentPhase)) return isActivePlayerForRound(currentRound);
        return false;
    }

    private void updateInputEnabledState() {
        setInputEnabled(isMyTurnToAct());
    }

    private void renderTurnStatus() {
        boolean iAmActive = isActivePlayerForRound(currentRound);
        if (Constants.SKOCKO_PHASE_MAIN.equals(currentPhase)) {
            tvTurnStatus.setText(iAmActive
                    ? getString(R.string.skocko_turn_mine)
                    : getString(R.string.skocko_turn_opponent, opponentName));
            tvInstruction.setText(iAmActive
                    ? getString(R.string.skocko_phase_main, localAttemptsCount + 1)
                    : getString(R.string.skocko_phase_watching));
        } else if (Constants.SKOCKO_PHASE_CHANCE.equals(currentPhase)) {
            boolean iAmPassive = !iAmActive;
            tvTurnStatus.setText(iAmPassive
                    ? getString(R.string.skocko_turn_chance_mine)
                    : getString(R.string.skocko_turn_chance_opponent, opponentName));
            tvInstruction.setText(R.string.skocko_phase_opponent);
        }
    }

    private void applyGuessAndHints(ImageView[] slots, View[] hintV, int[] guess, int[] hints) {
        for (int c = 0; c < SkockoLogic.COMBO_LEN; c++) {
            slots[c].setImageResource(SYMBOL_DRAW[guess[c]]);
        }
        applyHints(hintV, hints);
    }

    private void applyHints(View[] views, int[] hints) {
        for (int i = 0; i < hints.length; i++) {
            int drawable;
            switch (hints[i]) {
                case SkockoLogic.HINT_RED:    drawable = HINT_DRAW_RED;    break;
                case SkockoLogic.HINT_YELLOW: drawable = HINT_DRAW_YELLOW; break;
                default:                      drawable = HINT_DRAW_EMPTY;  break;
            }
            views[i].setBackgroundResource(drawable);
        }
    }

    private void updateScoreDisplay() {
        tvScore.setText(getString(R.string.skocko_score, myTotalScore));
        tvOpponentScore.setText((opponentName != null ? opponentName : "?") + ": " + opponentTotalScore);
    }

    private void setInputEnabled(boolean enabled) {
        for (View btn : symbolButtons) btn.setEnabled(enabled);
        btnClear.setEnabled(enabled);
    }

    private void cancelTimers() {
        if (turnTimer    != null) { turnTimer.cancel();    turnTimer    = null; }
        if (displayTimer != null) { displayTimer.cancel(); displayTimer = null; }
    }

    private void detachAll() {
        if (roundStateDetacher != null) { roundStateDetacher.run(); roundStateDetacher = null; }
        for (Runnable d : matchDetachers) d.run();
        matchDetachers.clear();
    }
}

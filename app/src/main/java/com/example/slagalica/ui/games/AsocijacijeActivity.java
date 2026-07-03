package com.example.slagalica.ui.games;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.slagalica.R;
import com.example.slagalica.data.model.AsocijacijePuzzle;
import com.example.slagalica.data.model.User;
import com.example.slagalica.data.repository.GameContentRepository;
import com.example.slagalica.data.repository.MatchRepository;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.logic.games.AsocijacijeLogic;
import com.example.slagalica.ui.widget.ScoreBarView;
import com.example.slagalica.util.AvatarProvider;
import com.example.slagalica.util.Constants;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

/**
 * Igra "Asocijacije" — KT2, multiplayer 1v1.
 *
 * Oba igrača igraju ISTU slagalicu po rundi zajedno — vidе live šta se dešava.
 * Igrač koji ima potez otkriva polja i pogađa; pogrešna pogodba predaje potez.
 * Runda 0: player1 ide prvi. Runda 1: player2 ide prvi.
 */
public class AsocijacijeActivity extends AppCompatActivity {

    private static final int TURN_SECONDS   = 120;
    private static final int NUM_ROUNDS     = 2;
    private static final int NUM_COLS       = AsocijacijeLogic.NUM_COLS;
    private static final int NUM_ROWS       = AsocijacijeLogic.NUM_ROWS;
    private static final int ROUND_DELAY_MS = 2_000;
    private static final String PHASE_DONE  = "DONE";

    private static final String[] COL_LETTERS = {"A", "B", "C", "D"};

    // ── Views ─────────────────────────────────────────────────────────────────
    private TextView        tvRound, tvTurnStatus;
    private ScoreBarView    scoreBar;
    private MaterialButton[][] clueButtons;
    private MaterialButton[]   colButtons;
    private MaterialButton  finalButton;
    private MaterialButton  btnGiveUp;

    // ── Match ─────────────────────────────────────────────────────────────────
    private final MatchRepository matchRepository = MatchRepository.getInstance();
    private final UserRepository  userRepository  = UserRepository.getInstance();
    private final GameContentRepository contentRepository = GameContentRepository.getInstance();
    private String  matchId, myUid, opponentUid, opponentName;
    private boolean isPlayerOne;
    private boolean opponentOnline = true;
    private int     myKzzScore, oppKzzScore;

    // ── Game state ────────────────────────────────────────────────────────────
    private AsocijacijePuzzle puzzle;
    private int  currentRound       = 0;
    private int  myTotalScore       = 0;
    private int  opponentTotalScore = 0;
    private int  myRoundScore       = 0;
    private boolean roundDone       = false;
    private boolean gameEnded       = false;

    // ── Live RTDB state ───────────────────────────────────────────────────────
    private String  currentTurn     = null;
    private boolean localMustReveal = false;
    private MatchRepository.AsocijacijeRoundState latestState = null;

    // ── Infra ─────────────────────────────────────────────────────────────────
    private CountDownTimer        timer;
    private final Handler         handler        = new Handler(Looper.getMainLooper());
    private final List<Runnable>  matchDetachers = new ArrayList<>();
    private Runnable              puzzleDetacher;
    private Runnable              roundStateDetacher;

    // ── Colors ────────────────────────────────────────────────────────────────
    // Otvoreno/rešeno od mene = plavo, od protivnika = crveno; polja koja ostanu
    // skrivena ali se automatski otkriju kad se kolona/finale pogodi = zeleno.
    // Svi tonovi su puni i upareni sa belim tekstom (vidi setTint()) tako da rade
    // i u tamnoj temi.
    private int colorHidden, colorMine, colorTheirs, colorAutoRevealed, colorWhiteText;

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_asocijacije);
        readExtras();
        initViews();
        resolveColors();
        setupBackHandler();
        joinMatch();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        cancelTimer();
        detachAll();
    }

    // =========================================================================
    // Init
    // =========================================================================

    private void readExtras() {
        matchId      = getIntent().getStringExtra(Constants.EXTRA_MATCH_ID);
        opponentUid  = getIntent().getStringExtra(Constants.EXTRA_OPPONENT_UID);
        opponentName = getIntent().getStringExtra(Constants.EXTRA_OPPONENT_NAME);
        isPlayerOne  = getIntent().getBooleanExtra(Constants.EXTRA_IS_PLAYER_ONE, false);
        myKzzScore   = getIntent().getIntExtra(Constants.EXTRA_MY_SCORE, 0);
        oppKzzScore  = getIntent().getIntExtra(Constants.EXTRA_OPPONENT_SCORE, 0);
        myUid        = userRepository.getCurrentUid();
    }

    private void initViews() {
        tvRound         = findViewById(R.id.tvRound);
        tvTurnStatus    = findViewById(R.id.tvTurnStatus);
        scoreBar        = findViewById(R.id.scoreBar);
        btnGiveUp       = findViewById(R.id.btnGiveUp);

        clueButtons = new MaterialButton[][]{
            { findViewById(R.id.btnA1), findViewById(R.id.btnA2),
              findViewById(R.id.btnA3), findViewById(R.id.btnA4) },
            { findViewById(R.id.btnB1), findViewById(R.id.btnB2),
              findViewById(R.id.btnB3), findViewById(R.id.btnB4) },
            { findViewById(R.id.btnC1), findViewById(R.id.btnC2),
              findViewById(R.id.btnC3), findViewById(R.id.btnC4) },
            { findViewById(R.id.btnD1), findViewById(R.id.btnD2),
              findViewById(R.id.btnD3), findViewById(R.id.btnD4) }
        };
        colButtons = new MaterialButton[]{
            findViewById(R.id.btnA), findViewById(R.id.btnB),
            findViewById(R.id.btnC), findViewById(R.id.btnD)
        };
        finalButton = findViewById(R.id.btnCenter);

        tvTurnStatus.setText("Učitavanje...");
        btnGiveUp.setOnClickListener(v -> confirmGiveUp());
        loadPlayerBar();
    }

    /** Popunjava traku sa rezultatom meča — nadimci, avatari i skor pre ove igre (samo KZZ). */
    private void loadPlayerBar() {
        scoreBar.setOpponentPlayer(AvatarProvider.getDrawableRes(0), opponentName != null ? opponentName : "?");
        scoreBar.setScores(myKzzScore, oppKzzScore);

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

    private void resolveColors() {
        colorHidden       = ContextCompat.getColor(this, R.color.asoc_cell_hidden);
        colorMine         = ContextCompat.getColor(this, R.color.asoc_cell_revealed_me);
        colorTheirs       = ContextCompat.getColor(this, R.color.asoc_cell_revealed_opponent);
        colorAutoRevealed = ContextCompat.getColor(this, R.color.asoc_cell_auto_revealed);
        colorWhiteText    = ContextCompat.getColor(this, R.color.white);
    }

    private void setupBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { confirmGiveUp(); }
        });
    }

    // =========================================================================
    // Match joining
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
                        // If it's opponent's turn and round is not done, force end
                        if (!roundDone && currentTurn != null && currentTurn.equals(opponentUid)) {
                            matchRepository.endAsocijacijeRound(matchId, currentRound);
                        }
                    }
                }));

        if (isPlayerOne) {
            // Asinhrono učitavanje iz Firestore-a — startRound(0) ne čeka ovaj poziv,
            // isto kao ranije kad je writeAsocijacijePuzzles bio "fire and forget";
            // oba igrača svejedno čekaju sadržaj kroz listenAsocijacijePuzzle.
            contentRepository.loadAsocijacijePuzzles(NUM_ROUNDS,
                    new GameContentRepository.AsocijacijeCallback() {
                        @Override
                        public void onSuccess(@NonNull List<AsocijacijePuzzle> puzzles) {
                            if (puzzles.size() < NUM_ROUNDS) {
                                Toast.makeText(AsocijacijeActivity.this,
                                        "Nema dovoljno asocijacija u bazi.", Toast.LENGTH_LONG).show();
                                return;
                            }
                            matchRepository.writeAsocijacijePuzzles(
                                    matchId, puzzles.get(0), puzzles.get(1), null);
                        }

                        @Override
                        public void onError(@NonNull String message) {
                            Toast.makeText(AsocijacijeActivity.this, message, Toast.LENGTH_LONG).show();
                        }
                    });
        }
        startRound(0);
    }

    // =========================================================================
    // Round management
    // =========================================================================

    private void startRound(int round) {
        currentRound    = round;
        puzzle          = null;
        roundDone       = false;
        myRoundScore    = 0;
        currentTurn     = null;
        localMustReveal = false;
        latestState     = null;

        tvRound.setText(getString(R.string.asoc_round, round + 1));
        tvTurnStatus.setText("Učitavanje...");
        clearBoard();
        cancelTimer();

        if (puzzleDetacher != null) { puzzleDetacher.run(); puzzleDetacher = null; }
        if (roundStateDetacher != null) { roundStateDetacher.run(); roundStateDetacher = null; }

        // First mover writes the initial round state
        if (isFirstMoverForRound(round)) {
            matchRepository.initAsocijacijeRound(matchId, round, myUid);
        }

        // Load the puzzle (one-shot listener)
        puzzleDetacher = matchRepository.listenAsocijacijePuzzle(matchId, round,
                new MatchRepository.AsocijacijePuzzleListener() {
            @Override
            public void onPuzzle(@NonNull AsocijacijePuzzle p) {
                if (gameEnded || roundDone) return;
                puzzle = p;
                bindBoard();
                startTimer();
                // Apply any state already received before puzzle arrived
                if (latestState != null) applyRoundState(latestState);
            }
            @Override
            public void onError(@NonNull String msg) {
                Toast.makeText(AsocijacijeActivity.this, msg, Toast.LENGTH_LONG).show();
                finish();
            }
        });

        // Both players listen to the live round state (persistent)
        roundStateDetacher = matchRepository.listenAsocijacijeRoundState(matchId, round,
                state -> {
                    if (gameEnded) return;
                    latestState     = state;
                    currentTurn     = state.turn;
                    localMustReveal = state.mustReveal;

                    if (puzzle != null) applyRoundState(state);

                    if (PHASE_DONE.equals(state.phase) && !roundDone) {
                        onRoundDone(state);
                    }
                });
    }

    private void onRoundDone(@NonNull MatchRepository.AsocijacijeRoundState finalState) {
        roundDone = true;
        cancelTimer();

        myTotalScore += myRoundScore;
        Integer oppScore = finalState.scores.get(opponentUid);
        opponentTotalScore += (oppScore != null ? oppScore : 0);

        boolean isLast = (currentRound >= NUM_ROUNDS - 1);
        handler.postDelayed(() -> {
            if (gameEnded) return;
            if (isLast) endGame();
            else startRound(currentRound + 1);
        }, ROUND_DELAY_MS);
    }

    // =========================================================================
    // Board rendering — driven entirely by RTDB state
    // =========================================================================

    private void bindBoard() {
        for (int col = 0; col < NUM_COLS; col++) {
            for (int row = 0; row < NUM_ROWS; row++) {
                final int c = col, r = row;
                MaterialButton btn = clueButtons[col][row];
                btn.setText("?");
                setTint(btn, colorHidden);
                btn.setOnClickListener(v -> onClueTapped(c, r));
            }
            MaterialButton colBtn = colButtons[col];
            colBtn.setText(COL_LETTERS[col] + " ?");
            setTint(colBtn, colorHidden);
            final int fc = col;
            colBtn.setOnClickListener(v -> onColSolutionTapped(fc));
        }
        finalButton.setText(getString(R.string.asoc_final_label));
        setTint(finalButton, colorHidden);
        finalButton.setOnClickListener(v -> onFinalTapped());
    }

    private void clearBoard() {
        for (int col = 0; col < NUM_COLS; col++) {
            for (int row = 0; row < NUM_ROWS; row++) {
                clueButtons[col][row].setText("?");
                setTint(clueButtons[col][row], colorHidden);
                clueButtons[col][row].setOnClickListener(null);
            }
            colButtons[col].setText(COL_LETTERS[col] + " ?");
            setTint(colButtons[col], colorHidden);
            colButtons[col].setOnClickListener(null);
        }
        finalButton.setText(getString(R.string.asoc_final_label));
        setTint(finalButton, colorHidden);
        finalButton.setOnClickListener(null);
    }

    private void applyRoundState(@NonNull MatchRepository.AsocijacijeRoundState state) {
        if (puzzle == null) return;

        for (int col = 0; col < NUM_COLS; col++) {
            boolean colAutoRevealed = state.colSolvedBy[col] != null || state.finalSolved;
            // Clue cells
            for (int row = 0; row < NUM_ROWS; row++) {
                MaterialButton btn = clueButtons[col][row];
                if (state.revealed[col][row]) {
                    btn.setText(puzzle.getClue(col, row));
                    boolean mine = myUid.equals(state.revealedBy[col][row]);
                    setTint(btn, mine ? colorMine : colorTheirs);
                } else if (colAutoRevealed) {
                    // Kolona (ili cela tabla, ako je finale pogođeno) je rešena —
                    // ostala skrivena polja se prikazuju zeleno, čisto vizuelno.
                    // NE menja latestState.revealed, pa ne utiče na bodovanje.
                    btn.setText(puzzle.getClue(col, row));
                    setTint(btn, colorAutoRevealed);
                } else {
                    btn.setText("?");
                    setTint(btn, colorHidden);
                }
            }
            // Column solution button
            MaterialButton colBtn = colButtons[col];
            if (state.colSolvedBy[col] != null) {
                colBtn.setText(puzzle.getColSolution(col));
                boolean mine = myUid.equals(state.colSolvedBy[col]);
                setTint(colBtn, mine ? colorMine : colorTheirs);
            } else if (state.finalSolved) {
                // Kolona nikad nije pogođena, ali je finale rešeno — otkrij i nju,
                // čisto vizuelno (isto pravilo kao za preostala skrivena polja).
                colBtn.setText(puzzle.getColSolution(col));
                setTint(colBtn, colorAutoRevealed);
            } else {
                colBtn.setText(COL_LETTERS[col] + " ?");
                setTint(colBtn, colorHidden);
            }
        }

        // Final button
        if (state.finalSolved) {
            finalButton.setText(puzzle.getFinalSolution());
            boolean mine = myUid.equals(state.finalSolvedBy);
            setTint(finalButton, mine ? colorMine : colorTheirs);
        } else {
            finalButton.setText(getString(R.string.asoc_final_label));
            setTint(finalButton, colorHidden);
        }

        // Turn status
        if (PHASE_DONE.equals(state.phase)) {
            tvTurnStatus.setText("Runda završena.");
        } else if (isMyTurn()) {
            tvTurnStatus.setText(localMustReveal ? "Tvoj red — prvo otkrij polje!" : "Tvoj red!");
        } else {
            tvTurnStatus.setText((opponentName != null ? opponentName : "?") + " je na potezu...");
        }
    }

    // =========================================================================
    // Player interactions
    // =========================================================================

    private void onClueTapped(int col, int row) {
        if (roundDone || puzzle == null) return;
        if (latestState != null) {
            if (latestState.revealed[col][row]) return;
            // Polje je već prikazano (zeleno) jer je kolona ili finale pogođeno —
            // nije stvarno "otkriveno" (radi bodovanja), ali se ne sme ponovo dirati.
            if (latestState.colSolvedBy[col] != null || latestState.finalSolved) return;
        }
        if (!isMyTurn()) {
            Snackbar.make(finalButton, "Nije tvoj red!", Snackbar.LENGTH_SHORT).show();
            return;
        }
        if (!localMustReveal) {
            // Već je otvoreno tačno jedno polje ovog poteza — sada mora da pogađa.
            Snackbar.make(finalButton, R.string.asoc_already_revealed, Snackbar.LENGTH_SHORT).show();
            return;
        }
        matchRepository.revealAsocijacijeCell(matchId, currentRound, col, row, myUid, localMustReveal);
    }

    private void onColSolutionTapped(int col) {
        if (roundDone || puzzle == null) return;
        if (latestState != null) {
            if (latestState.colSolvedBy[col] != null) return;
            // Kolona je već prikazana (zeleno) jer je finale pogođeno — ne sme
            // se ponovo "rešavati".
            if (latestState.finalSolved) return;
        }
        if (!isMyTurn()) {
            Snackbar.make(finalButton, "Nije tvoj red!", Snackbar.LENGTH_SHORT).show();
            return;
        }
        if (localMustReveal) {
            Snackbar.make(finalButton, R.string.asoc_must_reveal_first, Snackbar.LENGTH_SHORT).show();
            return;
        }
        showGuessDialog(getString(R.string.asoc_guess_col_title, COL_LETTERS[col]), answer -> {
            if (roundDone) return;
            String correct = puzzle.getColSolution(col).trim().toUpperCase();
            if (answer.trim().toUpperCase().equals(correct)) {
                int pts = AsocijacijeLogic.columnScore(countRevealedInState(col));
                myRoundScore += pts;
                matchRepository.solveAsocijacijeColumn(matchId, currentRound, col, myUid, pts, myRoundScore);
                Snackbar.make(finalButton,
                    getString(R.string.asoc_correct_col, pts, COL_LETTERS[col]),
                    Snackbar.LENGTH_SHORT).show();
            } else {
                Snackbar.make(finalButton, R.string.asoc_wrong, Snackbar.LENGTH_SHORT).show();
                matchRepository.passAsocijacijeTurn(matchId, currentRound, opponentUid);
            }
        });
    }

    private void onFinalTapped() {
        if (roundDone || puzzle == null) return;
        if (latestState != null && latestState.finalSolved) return;
        if (!isMyTurn()) {
            Snackbar.make(finalButton, "Nije tvoj red!", Snackbar.LENGTH_SHORT).show();
            return;
        }
        if (localMustReveal) {
            Snackbar.make(finalButton, R.string.asoc_must_reveal_first, Snackbar.LENGTH_SHORT).show();
            return;
        }
        showGuessDialog(getString(R.string.asoc_guess_final_title), answer -> {
            if (roundDone) return;
            String correct = puzzle.getFinalSolution().trim().toUpperCase();
            if (answer.trim().toUpperCase().equals(correct)) {
                int pts = AsocijacijeLogic.finalScore(revealedCountsFromState(), colSolvedFromState());
                myRoundScore += pts;
                matchRepository.solveAsocijacijeFinal(matchId, currentRound, myUid, pts, myRoundScore);
                Snackbar.make(finalButton,
                    getString(R.string.asoc_correct_final, pts),
                    Snackbar.LENGTH_SHORT).show();
            } else {
                Snackbar.make(finalButton, R.string.asoc_wrong, Snackbar.LENGTH_SHORT).show();
                matchRepository.passAsocijacijeTurn(matchId, currentRound, opponentUid);
            }
        });
    }

    // =========================================================================
    // End game
    // =========================================================================

    private void endGame() {
        if (gameEnded) return;
        gameEnded = true;
        matchRepository.setGameResult(matchId, Constants.GAME_ASOCIJACIJE, myUid, myTotalScore);

        Intent intent = new Intent(this, SkockoActivity.class);
        intent.putExtra(Constants.EXTRA_MATCH_ID, matchId);
        intent.putExtra(Constants.EXTRA_IS_PLAYER_ONE, isPlayerOne);
        intent.putExtra(Constants.EXTRA_OPPONENT_UID, opponentUid);
        intent.putExtra(Constants.EXTRA_OPPONENT_NAME, opponentName);
        intent.putExtra(Constants.EXTRA_MY_KZZ, myKzzScore);
        intent.putExtra(Constants.EXTRA_OPP_KZZ, oppKzzScore);
        intent.putExtra(Constants.EXTRA_MY_ASOCIJACIJE, myTotalScore);
        intent.putExtra(Constants.EXTRA_OPP_ASOCIJACIJE, opponentTotalScore);
        startActivity(intent);
        finish();
    }

    // =========================================================================
    // Give up
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
    // Timer
    // =========================================================================

    private void startTimer() {
        scoreBar.setTimeLeft(TURN_SECONDS);

        timer = new CountDownTimer((long) TURN_SECONDS * 1000, 1000) {
            @Override
            public void onTick(long ms) {
                int s = (int) (ms / 1000) + 1;
                scoreBar.setTimeLeft(s);
            }
            @Override
            public void onFinish() {
                scoreBar.setTimeLeft(0);
                if (!roundDone) {
                    Snackbar.make(finalButton, R.string.asoc_time_up, Snackbar.LENGTH_SHORT).show();
                    matchRepository.endAsocijacijeRound(matchId, currentRound);
                }
            }
        }.start();
    }

    private void cancelTimer() {
        if (timer != null) { timer.cancel(); timer = null; }
    }

    // =========================================================================
    // UI helpers
    // =========================================================================

    /** Puni pozadinu i tera belu boju teksta — čitljivo bez obzira na svetlu/tamnu temu. */
    private void setTint(MaterialButton btn, int color) {
        btn.setBackgroundTintList(ColorStateList.valueOf(color));
        btn.setTextColor(colorWhiteText);
    }

    // =========================================================================
    // Guess dialog
    // =========================================================================

    interface AnswerCallback { void onAnswer(String answer); }

    private void showGuessDialog(String title, AnswerCallback cb) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setHint(getString(R.string.asoc_guess_hint));
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setPadding(48, 24, 48, 8);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton(android.R.string.ok, (d, w) -> {
                String ans = input.getText() != null ? input.getText().toString() : "";
                cb.onAnswer(ans);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .create();

        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String ans = input.getText() != null ? input.getText().toString() : "";
                dialog.dismiss();
                cb.onAnswer(ans);
                return true;
            }
            return false;
        });
        dialog.show();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private boolean isMyTurn() {
        return myUid != null && myUid.equals(currentTurn);
    }

    private boolean isFirstMoverForRound(int round) {
        return (isPlayerOne && round == 0) || (!isPlayerOne && round == 1);
    }

    private int countRevealedInState(int col) {
        if (latestState == null) return 0;
        int count = 0;
        for (int r = 0; r < NUM_ROWS; r++) if (latestState.revealed[col][r]) count++;
        return count;
    }

    private int[] revealedCountsFromState() {
        int[] counts = new int[NUM_COLS];
        if (latestState != null)
            for (int c = 0; c < NUM_COLS; c++) counts[c] = countRevealedInState(c);
        return counts;
    }

    private boolean[] colSolvedFromState() {
        boolean[] solved = new boolean[NUM_COLS];
        if (latestState != null)
            for (int c = 0; c < NUM_COLS; c++) solved[c] = (latestState.colSolvedBy[c] != null);
        return solved;
    }

    private void detachAll() {
        if (puzzleDetacher != null) { puzzleDetacher.run(); puzzleDetacher = null; }
        if (roundStateDetacher != null) { roundStateDetacher.run(); roundStateDetacher = null; }
        for (Runnable d : matchDetachers) d.run();
        matchDetachers.clear();
    }
}

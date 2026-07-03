package com.example.slagalica.ui.games;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.data.model.Korak;
import com.example.slagalica.data.model.KorakState;
import com.example.slagalica.data.model.User;
import com.example.slagalica.data.repository.GameContentRepository;
import com.example.slagalica.data.repository.MatchRepository;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.logic.games.KorakPoKorakLogic;
import com.example.slagalica.ui.main.MainActivity;
import com.example.slagalica.ui.match.MatchResultActivity;
import com.example.slagalica.ui.widget.ScoreBarView;
import com.example.slagalica.util.AvatarProvider;
import com.example.slagalica.util.Constants;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Igra "Korak po korak" — KT2, multiplayer 1 na 1.
 *
 * <p>2 runde po 70 sekundi (7 hintova × 10s). Prvu rundu igra player1 (aktivni),
 * drugu player2. Player1 učitava oba zadatka iz Firestore-a i upisuje ih u RTDB.
 * Oba igrača slušaju stanje runde ({@link MatchRepository#listenKorakState}) i
 * prikazuju hintove onako kako ih aktivni igrač otkriva.</p>
 *
 * <p>Bodovanje je determinističko:
 * aktivni pogodak → {@link KorakPoKorakLogic#bodoviZaPogodakUKoraku(int)},
 * protivnička šansa → {@link KorakPoKorakLogic#bodoviZaPreuzimanje()}.</p>
 */
public class KorakPoKorakActivity extends AppCompatActivity {

    private static final int MAX_ROUNDS        = 2;
    private static final int ACTIVE_DURATION_MS = 70_000;
    private static final int CHANCE_DURATION_MS = 10_000;
    private static final int ROUND_DELAY_MS     = 1_800;

    // ─── Views ───────────────────────────────────────────────────────────────
    private TextView         tvRoundLabel;
    private TextView         tvTurnStatus;
    private ScoreBarView     scoreBar;
    private RecyclerView     rvKoraci;
    private TextInputLayout  tlGuess;
    private TextInputEditText etGuess;
    private MaterialButton   btnGuess;
    private MaterialButton   btnGiveUp;

    // ─── Meč ─────────────────────────────────────────────────────────────────
    private final MatchRepository        matchRepository  = MatchRepository.getInstance();
    private final UserRepository         userRepository   = UserRepository.getInstance();
    private final GameContentRepository  contentRepo      = GameContentRepository.getInstance();

    private String  matchId;
    private String  myUid;
    private String  opponentUid;
    private String  opponentName;
    private boolean isPlayerOne;
    private boolean isFriendly;
    private boolean opponentOnline = true;

    private int myKzzScore, oppKzzScore;
    private int myAsocScore, oppAsocScore;
    private int mySkockoScore, oppSkockoScore;
    private int myMojBrojScore, oppMojBrojScore;
    private int mySpojniceScore, oppSpojniceScore;

    // ─── Stanje igre ─────────────────────────────────────────────────────────
    private int     currentRound   = 0;
    private boolean gameEnded      = false;
    private int     myTotalScore   = 0;
    private int     opponentTotalScore = 0;

    // Stanje runde
    private String  currentPhase   = "";
    private int     localHintsOpened = 0;
    private boolean roundResolved  = false;
    private boolean guessGiven     = false;    // aktivni igrač dao pogodak ili isteklo
    private boolean chanceGiven    = false;    // pasivni dao šansu ili istekla

    // Zadaci (player1 učitava oba, oba igrača primaju iz RTDB)
    private String       currentResenje;
    private List<String> currentKoraci;
    private KorakAdapter adapter;

    // ─── Timeri ──────────────────────────────────────────────────────────────
    private CountDownTimer activeTimer;
    private CountDownTimer chanceTimer;
    private CountDownTimer displayTimer;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Runnable> matchDetachers = new ArrayList<>();
    private Runnable roundStateDetacher;
    private Runnable zadatakDetacher;

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_korak_po_korak);

        readExtras();
        initViews();
        setupListeners();
        setupBackHandler();
        joinMatch();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        otkaziSveTimere();
        detachAll();
    }

    // =========================================================================
    // Init
    // =========================================================================

    private void readExtras() {
        matchId          = getIntent().getStringExtra(Constants.EXTRA_MATCH_ID);
        opponentUid      = getIntent().getStringExtra(Constants.EXTRA_OPPONENT_UID);
        opponentName     = getIntent().getStringExtra(Constants.EXTRA_OPPONENT_NAME);
        isPlayerOne      = getIntent().getBooleanExtra(Constants.EXTRA_IS_PLAYER_ONE, false);
        isFriendly       = getIntent().getBooleanExtra(Constants.EXTRA_IS_FRIENDLY, false);
        myKzzScore      = getIntent().getIntExtra(Constants.EXTRA_MY_KZZ, 0);
        oppKzzScore     = getIntent().getIntExtra(Constants.EXTRA_OPP_KZZ, 0);
        myAsocScore     = getIntent().getIntExtra(Constants.EXTRA_MY_ASOCIJACIJE, 0);
        oppAsocScore    = getIntent().getIntExtra(Constants.EXTRA_OPP_ASOCIJACIJE, 0);
        mySkockoScore   = getIntent().getIntExtra(Constants.EXTRA_MY_SKOCKO, 0);
        oppSkockoScore  = getIntent().getIntExtra(Constants.EXTRA_OPP_SKOCKO, 0);
        myMojBrojScore  = getIntent().getIntExtra(Constants.EXTRA_MY_MOJ_BROJ, 0);
        oppMojBrojScore = getIntent().getIntExtra(Constants.EXTRA_OPP_MOJ_BROJ, 0);
        mySpojniceScore  = getIntent().getIntExtra(Constants.EXTRA_MY_SPOJNICE, 0);
        oppSpojniceScore = getIntent().getIntExtra(Constants.EXTRA_OPP_SPOJNICE, 0);
        myUid            = userRepository.getCurrentUid();
    }

    private void initViews() {
        tvRoundLabel    = findViewById(R.id.tvRoundLabel);
        tvTurnStatus    = findViewById(R.id.tvTurnStatus);
        scoreBar        = findViewById(R.id.scoreBar);
        rvKoraci        = findViewById(R.id.rvKoraci);
        tlGuess         = findViewById(R.id.tlGuess);
        etGuess         = findViewById(R.id.etGuess);
        btnGuess        = findViewById(R.id.btnGuess);
        btnGiveUp       = findViewById(R.id.btnGiveUp);

        rvKoraci.setLayoutManager(new LinearLayoutManager(this));
        setInputEnabled(false);
        tvTurnStatus.setText("Učitavanje zadatka...");
        loadPlayerBar();
    }

    /** Popunjava traku sa rezultatom meča — nadimci, avatari i skor pre ove igre (sve prethodne igre). */
    private void loadPlayerBar() {
        scoreBar.setOpponentPlayer(AvatarProvider.getDrawableRes(0), opponentName != null ? opponentName : "?");
        scoreBar.setScores(
                myKzzScore + myAsocScore + mySkockoScore + myMojBrojScore + mySpojniceScore,
                oppKzzScore + oppAsocScore + oppSkockoScore + oppMojBrojScore + oppSpojniceScore);

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
        btnGuess.setOnClickListener(v -> onGuessClicked());
        btnGiveUp.setOnClickListener(v -> confirmGiveUp());
    }

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
                        // Preskoči čekanje na protivnika
                        if (!roundResolved && isOpponentActiveCurrent()) {
                            advancePhase();
                        } else if (!isPlayerOne && currentResenje == null) {
                            // Player1 je napustio partiju pre nego što je stigao da
                            // upiše zadatke u RTDB — bez ovoga bi čekanje trajalo
                            // unedogled. Preuzimamo upis (spec. 3f: čekanje na
                            // igrača koji je napustio partiju svesti na minimum).
                            ucitajIUpisiZadatke();
                        }
                    }
                }));

        if (isPlayerOne) {
            // Player1 učitava oba zadatka i upisuje ih u RTDB
            ucitajIUpisiZadatke();
        } else {
            // Player2 čeka zadatak u RTDB
            startRound(0);
        }
    }

    /** Player1 učitava 2 zadatka iz Firestorea i upisuje ih u RTDB, pa pokreće rundu 0. */
    private void ucitajIUpisiZadatke() {
        tvTurnStatus.setText("Učitavanje zadataka...");
        contentRepo.getRandomKorakPoKorak(new GameContentRepository.KorakLoadCallback() {
            @Override
            public void onSuccess(com.example.slagalica.data.model.KorakPoKorakZadatak z0) {
                matchRepository.writeKorakZadatak(matchId, 0, z0.getResenje(), z0.getKoraci(), null);
                // Učitaj zadatak za rundu 1 (drugi, po mogućnosti različit)
                contentRepo.getRandomKorakPoKorak(new GameContentRepository.KorakLoadCallback() {
                    @Override
                    public void onSuccess(com.example.slagalica.data.model.KorakPoKorakZadatak z1) {
                        matchRepository.writeKorakZadatak(matchId, 1, z1.getResenje(), z1.getKoraci(), null);
                        startRound(0);
                    }
                    @Override
                    public void onError(String message) {
                        // Fallback: koristi isti zadatak
                        matchRepository.writeKorakZadatak(matchId, 1, z0.getResenje(), z0.getKoraci(), null);
                        startRound(0);
                    }
                });
            }
            @Override
            public void onError(String message) {
                Toast.makeText(KorakPoKorakActivity.this, message, Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    // =========================================================================
    // Upravljanje rundama
    // =========================================================================

    private void startRound(int round) {
        currentRound    = round;
        currentPhase    = "";
        localHintsOpened = 0;
        roundResolved   = false;
        guessGiven      = false;
        chanceGiven     = false;
        currentResenje  = null;
        currentKoraci   = null;

        tvRoundLabel.setText("Runda " + (round + 1) + "/" + MAX_ROUNDS);
        scoreBar.setTimeText("–");
        tvTurnStatus.setText("Čeka se zadatak...");
        etGuess.setText("");
        setInputEnabled(false);

        // Reset adaptera sa praznim koracima
        initAdapter(null);

        if (roundStateDetacher != null) { roundStateDetacher.run(); roundStateDetacher = null; }
        if (zadatakDetacher    != null) { zadatakDetacher.run();    zadatakDetacher    = null; }

        // Oba igrača čekaju zadatak (player1 ga je već upisao ili će upisati odmah)
        zadatakDetacher = matchRepository.listenKorakZadatak(matchId, round,
                new MatchRepository.KorakZadatakListener() {
            @Override
            public void onZadatak(@NonNull String resenje, @NonNull List<String> koraci) {
                if (gameEnded) return;
                currentResenje = resenje;
                currentKoraci  = koraci;
                initAdapter(koraci);

                // Pokreni listener stanja runde
                roundStateDetacher = matchRepository.listenKorakState(
                        matchId, round, KorakPoKorakActivity.this::onRoundStateChanged);

                // Aktivni igrač postavlja inicijalnu fazu
                if (isActivePlayerForRound(round)) {
                    matchRepository.setKorakOpenedHints(matchId, round, 0);
                    matchRepository.setKorakPhase(matchId, round, Constants.KORAK_PHASE_ACTIVE);
                }
            }
            @Override
            public void onError(@NonNull String message) {
                Toast.makeText(KorakPoKorakActivity.this, message, Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    // =========================================================================
    // Listener stanja runde (oba igrača ga primaju)
    // =========================================================================

    private void onRoundStateChanged(@NonNull String phase, int openedHints,
                                     @Nullable MatchRepository.KorakGuessData activeGuess,
                                     @Nullable MatchRepository.KorakGuessData opponentGuess) {
        if (gameEnded || roundResolved) return;

        // Otvori hintove do trenutnog broja (catch-up za pasivnog igrača)
        while (localHintsOpened < openedHints && adapter != null) {
            adapter.otvoriSledeci();
            localHintsOpened++;
        }

        // Proveri da li je runda završena
        if (Constants.KORAK_PHASE_DONE.equals(phase)) {
            if (!roundResolved) {
                onRoundDone(activeGuess, opponentGuess);
            }
            return;
        }

        // Promjena faze (ACTIVE → OPPONENT_CHANCE)
        if (!phase.equals(currentPhase)) {
            currentPhase = phase;
            otkaziSveTimere();

            if (Constants.KORAK_PHASE_ACTIVE.equals(phase)) {
                renderTurnStatus();
                startActivePhaseTurn();
            } else if (Constants.KORAK_PHASE_CHANCE.equals(phase)) {
                renderTurnStatus();
                startChancePhaseTurn();
            }
        }

        // Ako je protivnik napustio a on je aktivan — preskoči
        if (!opponentOnline && isOpponentActiveCurrent()) {
            advancePhase();
        }
    }

    // =========================================================================
    // Faza ACTIVE — aktivni igrač igra 70s
    // =========================================================================

    private void startActivePhaseTurn() {
        boolean iAmActive = isActivePlayerForRound(currentRound);

        if (iAmActive) {
            // Aktivni igrač: otvara hintove i kontroliše timer
            activeTimer = new CountDownTimer(ACTIVE_DURATION_MS, 10_000) {
                int hintsOpened = localHintsOpened;

                @Override
                public void onTick(long ms) {
                    if (hintsOpened < 7 && adapter != null) {
                        adapter.otvoriSledeci();
                        hintsOpened++;
                        localHintsOpened = hintsOpened;
                        matchRepository.setKorakOpenedHints(matchId, currentRound, hintsOpened);
                    }
                    int s = (int) Math.ceil(ms / 1000.0);
                    scoreBar.setTimeLeft(Math.min(s, 70));
                }

                @Override
                public void onFinish() {
                    scoreBar.setTimeLeft(0);
                    if (!guessGiven && !roundResolved) {
                        guessGiven = true;
                        matchRepository.setKorakPhase(matchId, currentRound, Constants.KORAK_PHASE_CHANCE);
                    }
                }
            }.start();
            setInputEnabled(true);
        } else {
            // Pasivni igrač: display timer (približno)
            long estimatedMs = Math.max((7 - localHintsOpened) * 10_000L, 0);
            displayTimer = new CountDownTimer(estimatedMs + 5_000, 1_000) {
                @Override
                public void onTick(long ms) {
                    int s = (int) Math.ceil(ms / 1000.0);
                    scoreBar.setTimeLeft(Math.min(s, 70));
                }
                @Override public void onFinish() { scoreBar.setTimeText("…"); }
            }.start();
            setInputEnabled(false);
        }
    }

    // =========================================================================
    // Faza OPPONENT_CHANCE — pasivni igrač ima 10 sekundi
    // =========================================================================

    private void startChancePhaseTurn() {
        boolean iAmPassive = !isActivePlayerForRound(currentRound); // pasivni postaje aktivan za unos

        if (iAmPassive) {
            setInputEnabled(true);
            chanceTimer = new CountDownTimer(CHANCE_DURATION_MS, 250) {
                @Override
                public void onTick(long ms) {
                    int s = (int) Math.ceil(ms / 1000.0);
                    scoreBar.setTimeLeft(s);
                }
                @Override
                public void onFinish() {
                    scoreBar.setTimeLeft(0);
                    if (!chanceGiven && !roundResolved) {
                        chanceGiven = true;
                        setInputEnabled(false);
                        matchRepository.submitKorakOpponentGuess(matchId, currentRound, myUid, false);
                        matchRepository.setKorakPhase(matchId, currentRound, Constants.KORAK_PHASE_DONE);
                    }
                }
            }.start();
        } else {
            // Originalni aktivni igrač: samo display
            setInputEnabled(false);
            displayTimer = new CountDownTimer(CHANCE_DURATION_MS, 250) {
                @Override
                public void onTick(long ms) {
                    int s = (int) Math.ceil(ms / 1000.0);
                    scoreBar.setTimeLeft(s);
                }
                @Override public void onFinish() { scoreBar.setTimeLeft(0); }
            }.start();
        }
    }

    // =========================================================================
    // Provera odgovora
    // =========================================================================

    private void onGuessClicked() {
        String input = etGuess.getText() != null ? etGuess.getText().toString().trim() : "";
        if (input.isEmpty() || currentResenje == null) return;

        tlGuess.setError(null);
        boolean correct = KorakPoKorakLogic.tacanOdgovor(input, currentResenje);

        if (Constants.KORAK_PHASE_ACTIVE.equals(currentPhase)) {
            // Aktivni igrač pogađa
            if (guessGiven) return;
            if (correct) {
                guessGiven = true;
                setInputEnabled(false);
                otkaziSveTimere();
                matchRepository.submitKorakActiveGuess(matchId, currentRound, myUid, true, localHintsOpened - 1);
                matchRepository.setKorakPhase(matchId, currentRound, Constants.KORAK_PHASE_DONE);
            } else {
                Toast.makeText(this, "Netačan odgovor, pokušaj ponovo!", Toast.LENGTH_SHORT).show();
                etGuess.selectAll();
            }
        } else if (Constants.KORAK_PHASE_CHANCE.equals(currentPhase)) {
            // Pasivni igrač u šansi
            if (chanceGiven) return;
            chanceGiven = true;
            otkaziSveTimere();
            setInputEnabled(false);
            matchRepository.submitKorakOpponentGuess(matchId, currentRound, myUid, correct);
            matchRepository.setKorakPhase(matchId, currentRound, Constants.KORAK_PHASE_DONE);
            if (!correct) {
                Toast.makeText(this, "Netačan odgovor.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // =========================================================================
    // Prelaz faze (ako protivnik napusti)
    // =========================================================================

    private void advancePhase() {
        if (roundResolved) return;
        otkaziSveTimere();
        if (Constants.KORAK_PHASE_ACTIVE.equals(currentPhase)) {
            if (!guessGiven) {
                guessGiven = true;
                matchRepository.setKorakPhase(matchId, currentRound, Constants.KORAK_PHASE_CHANCE);
            }
        } else if (Constants.KORAK_PHASE_CHANCE.equals(currentPhase)) {
            if (!chanceGiven) {
                chanceGiven = true;
                matchRepository.setKorakPhase(matchId, currentRound, Constants.KORAK_PHASE_DONE);
            }
        }
    }

    // =========================================================================
    // Kraj runde — deterministično bodovanje
    // =========================================================================

    private void onRoundDone(@Nullable MatchRepository.KorakGuessData activeGuess,
                              @Nullable MatchRepository.KorakGuessData opponentGuess) {
        if (roundResolved) return;
        roundResolved = true;
        otkaziSveTimere();
        setInputEnabled(false);

        boolean iAmActiveThisRound = isActivePlayerForRound(currentRound);

        if (activeGuess != null && activeGuess.correct) {
            int pts = KorakPoKorakLogic.bodoviZaPogodakUKoraku(
                    Math.max(0, Math.min(6, activeGuess.korakIndex)));
            if (iAmActiveThisRound) myTotalScore += pts;
            else opponentTotalScore += pts;
        } else if (opponentGuess != null && opponentGuess.correct) {
            int pts = KorakPoKorakLogic.bodoviZaPreuzimanje();
            if (!iAmActiveThisRound) myTotalScore += pts;
            else opponentTotalScore += pts;
        }

        boolean isLast = (currentRound >= MAX_ROUNDS - 1);
        handler.postDelayed(() -> {
            if (isLast) endGame();
            else startRound(currentRound + 1);
        }, ROUND_DELAY_MS);
    }

    // =========================================================================
    // Kraj igre — prelazak na MatchResultActivity
    // =========================================================================

    private void endGame() {
        if (gameEnded) return;
        gameEnded = true;

        matchRepository.setGameResult(matchId, Constants.GAME_KORAK, myUid, myTotalScore);

        Intent intent = new Intent(this, MatchResultActivity.class);
        intent.putExtra(Constants.EXTRA_MATCH_ID, matchId);
        intent.putExtra(Constants.EXTRA_OPPONENT_UID, opponentUid);
        intent.putExtra(Constants.EXTRA_OPPONENT_NAME, opponentName);
        intent.putExtra(Constants.EXTRA_MY_KZZ, myKzzScore);
        intent.putExtra(Constants.EXTRA_OPP_KZZ, oppKzzScore);
        intent.putExtra(Constants.EXTRA_MY_ASOCIJACIJE, myAsocScore);
        intent.putExtra(Constants.EXTRA_OPP_ASOCIJACIJE, oppAsocScore);
        intent.putExtra(Constants.EXTRA_MY_SKOCKO, mySkockoScore);
        intent.putExtra(Constants.EXTRA_OPP_SKOCKO, oppSkockoScore);
        intent.putExtra(Constants.EXTRA_MY_MOJ_BROJ, myMojBrojScore);
        intent.putExtra(Constants.EXTRA_OPP_MOJ_BROJ, oppMojBrojScore);
        intent.putExtra(Constants.EXTRA_MY_SPOJNICE, mySpojniceScore);
        intent.putExtra(Constants.EXTRA_OPP_SPOJNICE, oppSpojniceScore);
        intent.putExtra(Constants.EXTRA_MY_KORAK, myTotalScore);
        intent.putExtra(Constants.EXTRA_OPP_KORAK, opponentTotalScore);
        intent.putExtra(Constants.EXTRA_IS_FRIENDLY, isFriendly);
        startActivity(intent);
        finish();
    }

    // =========================================================================
    // Predaja
    // =========================================================================

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
    // UI helpers
    // =========================================================================

    private boolean isActivePlayerForRound(int round) {
        return (isPlayerOne && round == 0) || (!isPlayerOne && round == 1);
    }

    /** Da li je protivnik onaj koji trenutno treba da radi akciju u tekućoj fazi. */
    private boolean isOpponentActiveCurrent() {
        if (roundResolved) return false;
        if (Constants.KORAK_PHASE_ACTIVE.equals(currentPhase)) return !isActivePlayerForRound(currentRound);
        if (Constants.KORAK_PHASE_CHANCE.equals(currentPhase)) return isActivePlayerForRound(currentRound);
        return false;
    }

    private void renderTurnStatus() {
        boolean iAmActive = isActivePlayerForRound(currentRound);
        if (Constants.KORAK_PHASE_ACTIVE.equals(currentPhase)) {
            tvTurnStatus.setText(iAmActive ? "Ti pogađaš!" : opponentName + " pogađa...");
        } else if (Constants.KORAK_PHASE_CHANCE.equals(currentPhase)) {
            boolean iAmInChance = !iAmActive; // pasivni dobija šansu
            tvTurnStatus.setText(iAmInChance ? "Tvoja šansa! (10s)" : "Šansa za " + opponentName + "...");
        }
    }

    private void setInputEnabled(boolean enabled) {
        etGuess.setEnabled(enabled);
        btnGuess.setEnabled(enabled);
        if (!enabled) tlGuess.setError(null);
    }

    private void initAdapter(@Nullable List<String> koraci) {
        List<Korak> list = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            String hint = (koraci != null && i < koraci.size()) ? koraci.get(i) : "?";
            list.add(new Korak(i + 1, hint, KorakState.ZAKLJUCAN));
        }
        adapter = new KorakAdapter(list);
        rvKoraci.setAdapter(adapter);
    }

    private void otkaziSveTimere() {
        if (activeTimer  != null) { activeTimer.cancel();  activeTimer  = null; }
        if (chanceTimer  != null) { chanceTimer.cancel();  chanceTimer  = null; }
        if (displayTimer != null) { displayTimer.cancel(); displayTimer = null; }
    }

    private void detachAll() {
        if (roundStateDetacher != null) { roundStateDetacher.run(); roundStateDetacher = null; }
        if (zadatakDetacher    != null) { zadatakDetacher.run();    zadatakDetacher    = null; }
        for (Runnable d : matchDetachers) d.run();
        matchDetachers.clear();
    }
}

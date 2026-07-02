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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.example.slagalica.data.model.User;
import com.example.slagalica.data.repository.MatchRepository;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.logic.games.MojBrojLogic;
import com.example.slagalica.ui.games.SpojniceActivity;
import com.example.slagalica.ui.main.MainActivity;
import com.example.slagalica.ui.widget.ScoreBarView;
import com.example.slagalica.util.AvatarProvider;
import com.example.slagalica.util.Constants;
import com.example.slagalica.util.ShakeDetector;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Igra "Moj broj" — KT2, multiplayer 1 na 1.
 *
 * <p>2 runde po 60 sekundi. Prvu rundu generiše player1 (starter),
 * drugu rundu generiše player2. Starter pritiskanjem STOP dugmeta generiše traženi
 * broj i 6 dostupnih brojeva, upisuje ih u Realtime Database. Non-starter čeka na
 * te podatke, a čim stignu — oba igrača počinju unos istovremeno.</p>
 *
 * <p>Bodovanje je determinističko: oba klijenta nad istim RTDB podacima
 * pozivaju {@link MojBrojLogic#izracunajBodovanje} i dolaze do identičnog
 * rezultata.</p>
 */
public class MojBrojActivity extends AppCompatActivity {

    private static final int MAX_ROUNDS        = 2;
    private static final int ROUND_DURATION_MS = 60_000;
    private static final int AUTO_STOP_MS      = 5_000;
    private static final int TICK_MS           = 250;

    // ─── Token model (isti kao u solo verziji) ────────────────────────────────
    private static final class Token {
        final String value;
        final int numIndex;
        Token(String v, int idx) { value = v; numIndex = idx; }
        boolean isNumber() { return numIndex >= 0; }
    }
    private enum LastType { EMPTY, NUMBER_OR_CLOSE, OP_OR_OPEN }

    // ─── Views ───────────────────────────────────────────────────────────────
    private TextView        tvRoundLabel;
    private TextView        tvStatus;
    private TextView        tvTrazeniBroj;
    private TextView        tvIzraz;
    private ScoreBarView    scoreBar;
    private MaterialButton  btnStopPotvrdi;
    private MaterialButton  btnDel;
    private MaterialButton  btnGiveUp;
    private MaterialButton  btnLeftParen, btnRightParen;
    private MaterialButton  btnPlus, btnMinus, btnMult, btnDiv;
    private MaterialButton[] btnBrojevi;

    // ─── Meč ─────────────────────────────────────────────────────────────────
    private final MatchRepository matchRepository = MatchRepository.getInstance();
    private final UserRepository userRepository   = UserRepository.getInstance();

    private String  matchId;
    private String  myUid;
    private String  opponentUid;
    private String  opponentName;
    private boolean isPlayerOne;
    private boolean opponentOnline = true;

    // Bodovi iz prethodnih igara (prenose se kroz lanac)
    private int myKzzScore, oppKzzScore;
    private int myAsocScore, oppAsocScore;
    private int mySkockoScore, oppSkockoScore;

    // ─── Stanje igre ─────────────────────────────────────────────────────────
    private int     currentRound         = 0;
    private boolean amIStarter           = false;
    private int     faza                 = 0; // 0=stop1, 1=stop2, 2=igra, 3=čekanje
    private boolean myExpressionSubmitted = false;
    private boolean roundResolved         = false;
    private boolean gameEnded             = false;

    private int  myTotalScore       = 0;
    private int  opponentTotalScore = 0;
    private int  trazeniBroj;
    private int[] dostupniBrojevi;

    private Map<String, MatchRepository.MojBrojExpression> lastExpressions;

    // ─── Token unos ──────────────────────────────────────────────────────────
    private final List<Token> tokens  = new ArrayList<>();
    private final boolean[]   used    = new boolean[6];
    private LastType          lastType = LastType.EMPTY;

    // ─── Timeri ──────────────────────────────────────────────────────────────
    private ShakeDetector   shakeDetector;
    private CountDownTimer  rundaTimer;
    private CountDownTimer  autoStopTimer;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Runnable> matchDetachers = new ArrayList<>();
    private Runnable roundDataDetacher;
    private Runnable expressionsDetacher;

    private final Random random = new Random();

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moj_broj);

        readExtras();
        initViews();
        setupListeners();
        setupBackHandler();
        joinMatch();
    }

    @Override protected void onResume()  { super.onResume();  if (shakeDetector != null) shakeDetector.start(); }
    @Override protected void onPause()   { super.onPause();   if (shakeDetector != null) shakeDetector.stop();  }
    @Override protected void onDestroy() {
        super.onDestroy();
        otkaziTimere();
        if (shakeDetector != null) shakeDetector.stop();
        handler.removeCallbacksAndMessages(null);
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
        myKzzScore    = getIntent().getIntExtra(Constants.EXTRA_MY_KZZ, 0);
        oppKzzScore   = getIntent().getIntExtra(Constants.EXTRA_OPP_KZZ, 0);
        myAsocScore   = getIntent().getIntExtra(Constants.EXTRA_MY_ASOCIJACIJE, 0);
        oppAsocScore  = getIntent().getIntExtra(Constants.EXTRA_OPP_ASOCIJACIJE, 0);
        mySkockoScore = getIntent().getIntExtra(Constants.EXTRA_MY_SKOCKO, 0);
        oppSkockoScore = getIntent().getIntExtra(Constants.EXTRA_OPP_SKOCKO, 0);
        myUid        = userRepository.getCurrentUid();
    }

    private void initViews() {
        tvRoundLabel    = findViewById(R.id.tvRoundLabel);
        tvStatus        = findViewById(R.id.tvStatus);
        tvTrazeniBroj   = findViewById(R.id.tvTrazeniBroj);
        tvIzraz         = findViewById(R.id.tvIzraz);
        scoreBar        = findViewById(R.id.scoreBar);
        btnStopPotvrdi  = findViewById(R.id.btnStopPotvrdi);
        btnDel          = findViewById(R.id.btnDel);
        btnGiveUp       = findViewById(R.id.btnGiveUp);
        btnLeftParen    = findViewById(R.id.btnLeftParen);
        btnRightParen   = findViewById(R.id.btnRightParen);
        btnPlus         = findViewById(R.id.btnPlus);
        btnMinus        = findViewById(R.id.btnMinus);
        btnMult         = findViewById(R.id.btnMult);
        btnDiv          = findViewById(R.id.btnDiv);

        btnBrojevi = new MaterialButton[]{
            findViewById(R.id.btnNum0), findViewById(R.id.btnNum1),
            findViewById(R.id.btnNum2), findViewById(R.id.btnNum3),
            findViewById(R.id.btnNum4), findViewById(R.id.btnNum5)
        };

        tvStatus.setText("Povezivanje na meč...");
        loadPlayerBar();
    }

    /** Popunjava traku sa rezultatom meča — nadimci, avatari i skor pre ove igre (KZZ + Asocijacije + Skočko). */
    private void loadPlayerBar() {
        scoreBar.setOpponentPlayer(AvatarProvider.getDrawableRes(0), opponentName != null ? opponentName : "?");
        scoreBar.setScores(myKzzScore + myAsocScore + mySkockoScore,
                oppKzzScore + oppAsocScore + oppSkockoScore);

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
        btnStopPotvrdi.setOnClickListener(v -> onStopPotvrdiClicked());
        btnDel.setOnClickListener(v -> onDel());
        btnGiveUp.setOnClickListener(v -> confirmGiveUp());

        for (int i = 0; i < btnBrojevi.length; i++) {
            final int idx = i;
            btnBrojevi[i].setOnClickListener(v -> onNumPressed(idx));
        }
        btnLeftParen.setOnClickListener(v  -> onOpPressed("("));
        btnRightParen.setOnClickListener(v -> onOpPressed(")"));
        btnPlus.setOnClickListener(v       -> onOpPressed("+"));
        btnMinus.setOnClickListener(v      -> onOpPressed("-"));
        btnMult.setOnClickListener(v       -> onOpPressed("×"));
        btnDiv.setOnClickListener(v        -> onOpPressed("÷"));
    }

    private void setupBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { confirmGiveUp(); }
        });
    }

    // =========================================================================
    // Prisustvo
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
                        if (faza >= 2 && !myExpressionSubmitted) {
                            // Protivnik napustio tokom igre — submit i razresi
                            onStopPotvrdiClicked();
                        } else if (faza >= 2 && myExpressionSubmitted) {
                            if (lastExpressions != null) onExpressionsChanged(lastExpressions);
                            else onExpressionsChanged(new HashMap<>());
                        } else if (!amIStarter && faza < 2) {
                            // Starter je napustio partiju pre nego što je generisao
                            // rundu (traženi broj + 6 brojeva) — bez ovoga bi čekanje
                            // trajalo unedogled. Preuzimamo generisanje (spec. 3f:
                            // čekanje na igrača koji je napustio partiju svesti na minimum).
                            takeOverRoundGeneration();
                        }
                    }
                }));

        pokreniRundu(0);
    }

    // =========================================================================
    // Tok runde
    // =========================================================================

    private void pokreniRundu(int round) {
        currentRound = round;
        amIStarter = (isPlayerOne && round == 0) || (!isPlayerOne && round == 1);
        faza = 0;
        myExpressionSubmitted = false;
        roundResolved = false;
        lastExpressions = null;

        tokens.clear();
        Arrays.fill(used, false);
        lastType = LastType.EMPTY;

        tvRoundLabel.setText("Runda " + (round + 1) + "/" + MAX_ROUNDS);
        tvTrazeniBroj.setText("Traženi: ?");
        tvIzraz.setText("");
        scoreBar.setTimeText("–");
        btnStopPotvrdi.setText("STOP");
        for (MaterialButton btn : btnBrojevi) btn.setText("?");
        setGamepadEnabled(false);
        azurirajIzraz();

        if (amIStarter) {
            tvStatus.setText("Ti generišeš! Pritisni STOP.");
            btnStopPotvrdi.setEnabled(true);
            shakeDetector = new ShakeDetector(this, () -> { if (faza < 2) onStopPotvrdiClicked(); });
            shakeDetector.start();
            pokreniAutoStop();
        } else {
            tvStatus.setText("Čeka se protivnik da generiše brojeve...");
            btnStopPotvrdi.setEnabled(false);
            listenForRoundData(round);
        }

        if (expressionsDetacher != null) { expressionsDetacher.run(); expressionsDetacher = null; }
    }

    private void listenForRoundData(int round) {
        if (roundDataDetacher != null) { roundDataDetacher.run(); roundDataDetacher = null; }
        roundDataDetacher = matchRepository.listenMojBrojRound(matchId, round,
            new MatchRepository.MojBrojRoundListener() {
                @Override
                public void onRound(int target, int[] numbers) {
                    if (gameEnded || roundResolved) return;
                    trazeniBroj = target;
                    dostupniBrojevi = numbers;
                    onBrojeviPrimljeni();
                }
                @Override
                public void onError(@NonNull String message) {
                    Toast.makeText(MojBrojActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
    }

    /**
     * Preuzima generisanje runde (traženi broj + 6 dostupnih brojeva) kada je
     * starter napustio partiju pre nego što je stigao da ih upiše u RTDB.
     * Već zakačen {@link #listenForRoundData} slušalac normalno prihvata
     * upisane podatke i nastavlja tok igre kao da ih je napisao starter.
     */
    private void takeOverRoundGeneration() {
        if (faza >= 2 || gameEnded || roundResolved) {
            return;
        }
        trazeniBroj = 100 + random.nextInt(900);
        dostupniBrojevi = generisi6Brojeva();
        matchRepository.writeMojBrojRound(matchId, currentRound, trazeniBroj, dostupniBrojevi, null);
    }

    // =========================================================================
    // STOP / POTVRDI logika
    // =========================================================================

    private void onStopPotvrdiClicked() {
        if (faza == 0) {
            otkaziAutoStop();
            trazeniBroj = 100 + random.nextInt(900);
            tvTrazeniBroj.setText("Traženi: " + trazeniBroj);
            tvStatus.setText("Pritisni STOP za 6 brojeva.");
            faza = 1;
            pokreniAutoStop();

        } else if (faza == 1) {
            otkaziAutoStop();
            dostupniBrojevi = generisi6Brojeva();
            btnStopPotvrdi.setEnabled(false);
            tvStatus.setText("Pisanje u bazu...");
            matchRepository.writeMojBrojRound(matchId, currentRound, trazeniBroj, dostupniBrojevi,
                new MatchRepository.SimpleCallback() {
                    @Override public void onSuccess() { onBrojeviPrimljeni(); }
                    @Override public void onError(@NonNull String msg) {
                        Toast.makeText(MojBrojActivity.this, msg, Toast.LENGTH_SHORT).show();
                        btnStopPotvrdi.setEnabled(true);
                    }
                });

        } else if (faza == 2) {
            // POTVRDI
            otkaziTimere();
            submitMojIzraz();

        } else if (faza == 3) {
            // već submitted, ignorisi
        }
    }

    private void onBrojeviPrimljeni() {
        if (gameEnded || roundResolved) return;
        faza = 2;
        tvTrazeniBroj.setText("Traženi: " + trazeniBroj);
        for (int i = 0; i < 6; i++) btnBrojevi[i].setText(String.valueOf(dostupniBrojevi[i]));
        tvStatus.setText("Unesi matematički izraz!");
        btnStopPotvrdi.setText("POTVRDI");
        btnStopPotvrdi.setEnabled(true);
        setGamepadEnabled(true);
        scoreBar.setTimeLeft(60);
        pokreniTimer60s();

        expressionsDetacher = matchRepository.listenMojBrojExpressions(
                matchId, currentRound, this::onExpressionsChanged);
    }

    private void pokreniAutoStop() {
        otkaziAutoStop();
        autoStopTimer = new CountDownTimer(AUTO_STOP_MS, AUTO_STOP_MS) {
            @Override public void onTick(long ms) {}
            @Override public void onFinish() { onStopPotvrdiClicked(); }
        }.start();
    }

    private void pokreniTimer60s() {
        otkaziRunduTimer();
        rundaTimer = new CountDownTimer(ROUND_DURATION_MS, TICK_MS) {
            @Override
            public void onTick(long ms) {
                int s = (int) Math.ceil(ms / 1000.0);
                scoreBar.setTimeLeft(s);
            }
            @Override
            public void onFinish() {
                scoreBar.setTimeLeft(0);
                if (!myExpressionSubmitted) submitMojIzraz();
            }
        }.start();
    }

    // =========================================================================
    // Token — unos
    // =========================================================================

    private void onNumPressed(int idx) {
        if (faza != 2 || used[idx] || lastType == LastType.NUMBER_OR_CLOSE) return;
        used[idx] = true;
        tokens.add(new Token(String.valueOf(dostupniBrojevi[idx]), idx));
        lastType = LastType.NUMBER_OR_CLOSE;
        azurirajIzraz();
    }

    private void onOpPressed(String op) {
        if (faza != 2) return;
        switch (op) {
            case "(":
                if (lastType == LastType.NUMBER_OR_CLOSE) return;
                tokens.add(new Token("(", -1));
                lastType = LastType.OP_OR_OPEN;
                break;
            case ")":
                if (lastType != LastType.NUMBER_OR_CLOSE) return;
                tokens.add(new Token(")", -1));
                lastType = LastType.NUMBER_OR_CLOSE;
                break;
            default:
                if (lastType != LastType.NUMBER_OR_CLOSE) return;
                tokens.add(new Token(op, -1));
                lastType = LastType.OP_OR_OPEN;
                break;
        }
        azurirajIzraz();
    }

    private void onDel() {
        if (tokens.isEmpty()) return;
        Token last = tokens.remove(tokens.size() - 1);
        if (last.isNumber()) used[last.numIndex] = false;
        if (tokens.isEmpty()) {
            lastType = LastType.EMPTY;
        } else {
            String prev = tokens.get(tokens.size() - 1).value;
            lastType = (prev.equals("(") || prev.equals("+") || prev.equals("-")
                    || prev.equals("×") || prev.equals("÷"))
                    ? LastType.OP_OR_OPEN
                    : LastType.NUMBER_OR_CLOSE;
        }
        azurirajIzraz();
    }

    private void azurirajIzraz() {
        if (tokens.isEmpty()) {
            tvIzraz.setText("");
        } else {
            StringBuilder sb = new StringBuilder();
            for (Token t : tokens) sb.append(t.value);
            tvIzraz.setText(sb.toString());
        }
        azurirajStanjeDugmadi();
    }

    private void setGamepadEnabled(boolean enabled) {
        if (!enabled) {
            for (MaterialButton btn : btnBrojevi) btn.setEnabled(false);
            btnLeftParen.setEnabled(false); btnRightParen.setEnabled(false);
            btnPlus.setEnabled(false); btnMinus.setEnabled(false);
            btnMult.setEnabled(false); btnDiv.setEnabled(false);
            btnDel.setEnabled(false);
        } else {
            azurirajStanjeDugmadi();
        }
    }

    private void azurirajStanjeDugmadi() {
        boolean canNum = (lastType != LastType.NUMBER_OR_CLOSE);
        boolean canOp  = (lastType == LastType.NUMBER_OR_CLOSE);
        for (int i = 0; i < 6; i++) btnBrojevi[i].setEnabled(canNum && !used[i]);
        btnLeftParen.setEnabled(canNum);
        btnRightParen.setEnabled(canOp);
        btnPlus.setEnabled(canOp);
        btnMinus.setEnabled(canOp);
        btnMult.setEnabled(canOp);
        btnDiv.setEnabled(canOp);
        btnDel.setEnabled(!tokens.isEmpty());
    }

    // =========================================================================
    // Submit izraza i razrešavanje runde
    // =========================================================================

    private void submitMojIzraz() {
        if (myExpressionSubmitted || gameEnded) return;
        myExpressionSubmitted = true;
        faza = 3;
        setGamepadEnabled(false);
        btnStopPotvrdi.setEnabled(false);
        tvStatus.setText("Čeka se protivnik...");

        StringBuilder sb = new StringBuilder();
        for (Token t : tokens) sb.append(t.value);
        String expr = sb.toString();

        int izracunato = 0;
        boolean isValid = false;
        if (!expr.isEmpty() && dostupniBrojevi != null) {
            MojBrojLogic.IzrazRezultat r = MojBrojLogic.evaluate(expr, dostupniBrojevi);
            if (r.validan) {
                izracunato = (int) Math.round(r.rezultat);
                isValid = true;
            }
        }
        matchRepository.submitMojBrojExpression(matchId, currentRound, myUid, expr, izracunato, isValid);

        // Ako protivnik već nije online ili već submittovao, razresi odmah
        if (lastExpressions != null) onExpressionsChanged(lastExpressions);
        else if (!opponentOnline) onExpressionsChanged(new HashMap<>());
    }

    private void onExpressionsChanged(@NonNull Map<String, MatchRepository.MojBrojExpression> expressions) {
        lastExpressions = expressions;
        if (!myExpressionSubmitted) return;

        MatchRepository.MojBrojExpression oppExpr = expressions.get(opponentUid);
        boolean opponentDone = (oppExpr != null && oppExpr.submitted) || !opponentOnline;
        if (!opponentDone) return;

        maybeResolveRound(expressions);
    }

    private void maybeResolveRound(@NonNull Map<String, MatchRepository.MojBrojExpression> expressions) {
        if (roundResolved || gameEnded) return;
        roundResolved = true;
        otkaziTimere();

        MatchRepository.MojBrojExpression myExpr  = expressions.get(myUid);
        MatchRepository.MojBrojExpression oppExpr = expressions.get(opponentUid);

        MojBrojLogic.IzrazRezultat myResult  = exprToRezultat(myExpr);
        MojBrojLogic.IzrazRezultat oppResult = exprToRezultat(oppExpr);

        // r1 = player1, r2 = player2 (izracunajBodovanje je definisano tako)
        MojBrojLogic.IzrazRezultat r1 = isPlayerOne ? myResult : oppResult;
        MojBrojLogic.IzrazRezultat r2 = isPlayerOne ? oppResult : myResult;

        // Runda 0 je "runda player1-a", runda 1 je "runda player2-a"
        boolean prvaRundaJePlayerOne = (currentRound == 0);

        int[] bodovi = MojBrojLogic.izracunajBodovanje(trazeniBroj, r1, r2, prvaRundaJePlayerOne);
        int myDelta  = isPlayerOne ? bodovi[0] : bodovi[1];
        int oppDelta = isPlayerOne ? bodovi[1] : bodovi[0];

        myTotalScore       += myDelta;
        opponentTotalScore += oppDelta;
        scoreBar.setScores(myKzzScore + myAsocScore + mySkockoScore + myTotalScore,
                oppKzzScore + oppAsocScore + oppSkockoScore + opponentTotalScore);

        boolean isLast = (currentRound >= MAX_ROUNDS - 1);
        String myRes  = myResult.validan  ? String.valueOf((int) Math.round(myResult.rezultat))  : "–";
        String oppRes = oppResult.validan ? String.valueOf((int) Math.round(oppResult.rezultat)) : "–";

        // Prikaži rezultat u statusnom tekstu i nastavi automatski nakon 5s.
        // Automatski prelaz sprečava desinhronizaciju — oba igrača kreću u sledeću
        // rundu u isto vreme bez čekanja na korisnički klik.
        tvStatus.setText("Traženi: " + trazeniBroj
                + " | Ti: " + myRes
                + " | " + (opponentName != null ? opponentName : "?") + ": " + oppRes
                + " | +" + myDelta + " bodova");
        scoreBar.setTimeLeft(5);

        new CountDownTimer(5_000, 1_000) {
            @Override
            public void onTick(long ms) {
                int s = (int) Math.ceil(ms / 1000.0);
                scoreBar.setTimeLeft(s);
            }
            @Override
            public void onFinish() {
                scoreBar.setTimeLeft(0);
                if (!gameEnded) {
                    if (isLast) endGame();
                    else pokreniRundu(currentRound + 1);
                }
            }
        }.start();
    }

    private MojBrojLogic.IzrazRezultat exprToRezultat(MatchRepository.MojBrojExpression expr) {
        MojBrojLogic.IzrazRezultat r = new MojBrojLogic.IzrazRezultat();
        if (expr == null || !expr.submitted || !expr.isValid) {
            r.validan  = false;
            r.rezultat = 0;
        } else {
            r.validan  = true;
            r.rezultat = expr.result;
        }
        return r;
    }

    // =========================================================================
    // Kraj igre — prelazak na Korak po korak
    // =========================================================================

    private void endGame() {
        if (gameEnded) return;
        gameEnded = true;

        matchRepository.setGameResult(matchId, Constants.GAME_MOJ_BROJ, myUid, myTotalScore);

        Intent intent = new Intent(this, SpojniceActivity.class);
        intent.putExtra(Constants.EXTRA_MATCH_ID, matchId);
        intent.putExtra(Constants.EXTRA_IS_PLAYER_ONE, isPlayerOne);
        intent.putExtra(Constants.EXTRA_OPPONENT_UID, opponentUid);
        intent.putExtra(Constants.EXTRA_OPPONENT_NAME, opponentName);
        intent.putExtra(Constants.EXTRA_MY_KZZ, myKzzScore);
        intent.putExtra(Constants.EXTRA_OPP_KZZ, oppKzzScore);
        intent.putExtra(Constants.EXTRA_MY_ASOCIJACIJE, myAsocScore);
        intent.putExtra(Constants.EXTRA_OPP_ASOCIJACIJE, oppAsocScore);
        intent.putExtra(Constants.EXTRA_MY_SKOCKO, mySkockoScore);
        intent.putExtra(Constants.EXTRA_OPP_SKOCKO, oppSkockoScore);
        intent.putExtra(Constants.EXTRA_MY_MOJ_BROJ, myTotalScore);
        intent.putExtra(Constants.EXTRA_OPP_MOJ_BROJ, opponentTotalScore);
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
    // Generisanje 6 brojeva (pozicije 0-3 = jednocifreni, 4 = mali, 5 = veliki)
    // =========================================================================

    private int[] generisi6Brojeva() {
        int[] maliSet   = {10, 15, 20};
        int[] velikiSet = {25, 50, 75, 100};
        List<Integer> jed = new ArrayList<>();
        for (int i = 0; i < 4; i++) jed.add(1 + random.nextInt(9));
        Collections.shuffle(jed, random);
        int[] result = new int[6];
        for (int i = 0; i < 4; i++) result[i] = jed.get(i);
        result[4] = maliSet[random.nextInt(maliSet.length)];
        result[5] = velikiSet[random.nextInt(velikiSet.length)];
        return result;
    }

    // =========================================================================
    // UI helpers
    // =========================================================================

    private void otkaziAutoStop()  { if (autoStopTimer != null) { autoStopTimer.cancel(); autoStopTimer = null; } }
    private void otkaziRunduTimer() { if (rundaTimer    != null) { rundaTimer.cancel();    rundaTimer    = null; } }
    private void otkaziTimere()    { otkaziAutoStop(); otkaziRunduTimer(); }

    private void detachAll() {
        if (roundDataDetacher   != null) { roundDataDetacher.run();   roundDataDetacher   = null; }
        if (expressionsDetacher != null) { expressionsDetacher.run(); expressionsDetacher = null; }
        for (Runnable d : matchDetachers) d.run();
        matchDetachers.clear();
    }
}

package com.example.slagalica.ui.match;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.animation.OvershootInterpolator;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.example.slagalica.data.model.NotificationType;
import com.example.slagalica.data.model.User;
import com.example.slagalica.data.repository.DailyChallengeRepository;
import com.example.slagalica.data.repository.MatchRepository;
import com.example.slagalica.data.repository.TournamentRepository;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.logic.match.MatchRewardCalculator;
import com.example.slagalica.ui.main.MainActivity;
import com.example.slagalica.ui.tournament.TournamentBracketActivity;
import com.example.slagalica.util.Constants;
import com.example.slagalica.util.LeagueIconProvider;
import com.example.slagalica.util.NotificationPoster;
import com.google.android.material.button.MaterialButton;

import android.widget.TextView;
import android.widget.Toast;

/**
 * Ekran sa rezultatom završenog meča (KT2).
 *
 * <p>Prikazuje bodove po igrama i ukupan rezultat, te ishod iz ugla prijavljenog
 * igrača. Za obične mečeve beleži statistiku i dodeljuje zvezde/žetone.</p>
 *
 * <p>Za <b>turnirske</b> mečeve (meč čvor nosi {@code tournamentId} + {@code stage})
 * umesto standardnih nagrada primenjuje turnirsku šemu, prijavljuje pobednika u
 * bracket ({@link TournamentRepository}) i vraća igrača na
 * {@link TournamentBracketActivity} umesto na početni ekran. Neodlučen ishod se
 * razrešava determinističkim "coin flip"-om po {@code matchId} da bi oba klijenta
 * došla do istog pobednika.</p>
 */
public class MatchResultActivity extends AppCompatActivity {

    public static final String EXTRA_MY_KZZ        = Constants.EXTRA_MY_KZZ;
    public static final String EXTRA_OPP_KZZ       = Constants.EXTRA_OPP_KZZ;
    public static final String EXTRA_MY_SPOJNICE   = Constants.EXTRA_MY_SPOJNICE;
    public static final String EXTRA_OPP_SPOJNICE  = Constants.EXTRA_OPP_SPOJNICE;

    private final UserRepository userRepository = UserRepository.getInstance();
    private final MatchRepository matchRepository = MatchRepository.getInstance();
    private final TournamentRepository tournamentRepository = TournamentRepository.getInstance();
    private final DailyChallengeRepository dailyChallengeRepository = DailyChallengeRepository.getInstance();

    private int myTotal;
    private int oppTotal;
    private boolean isFriendly;

    private String matchId;
    private String opponentUid;

    /** Postavljeno kada je ovo turnirski meč — inače ostaje {@code null}. */
    @Nullable private String tournamentId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_match_result);

        int myKzz       = getIntent().getIntExtra(EXTRA_MY_KZZ, 0);
        int oppKzz      = getIntent().getIntExtra(EXTRA_OPP_KZZ, 0);
        int myAsoc      = getIntent().getIntExtra(Constants.EXTRA_MY_ASOCIJACIJE, 0);
        int oppAsoc     = getIntent().getIntExtra(Constants.EXTRA_OPP_ASOCIJACIJE, 0);
        int mySkocko    = getIntent().getIntExtra(Constants.EXTRA_MY_SKOCKO, 0);
        int oppSkocko   = getIntent().getIntExtra(Constants.EXTRA_OPP_SKOCKO, 0);
        int myMojBroj   = getIntent().getIntExtra(Constants.EXTRA_MY_MOJ_BROJ, 0);
        int oppMojBroj  = getIntent().getIntExtra(Constants.EXTRA_OPP_MOJ_BROJ, 0);
        int mySpojnice  = getIntent().getIntExtra(EXTRA_MY_SPOJNICE, 0);
        int oppSpojnice = getIntent().getIntExtra(EXTRA_OPP_SPOJNICE, 0);
        int myKorak     = getIntent().getIntExtra(Constants.EXTRA_MY_KORAK, 0);
        int oppKorak    = getIntent().getIntExtra(Constants.EXTRA_OPP_KORAK, 0);
        String opponentName = getIntent().getStringExtra(Constants.EXTRA_OPPONENT_NAME);

        matchId     = getIntent().getStringExtra(Constants.EXTRA_MATCH_ID);
        opponentUid = getIntent().getStringExtra(Constants.EXTRA_OPPONENT_UID);
        isFriendly  = getIntent().getBooleanExtra(Constants.EXTRA_IS_FRIENDLY, false);

        myTotal  = myKzz + myAsoc + mySkocko + myMojBroj + mySpojnice + myKorak;
        oppTotal = oppKzz + oppAsoc + oppSkocko + oppMojBroj + oppSpojnice + oppKorak;

        bindResult(myKzz, oppKzz, myAsoc, oppAsoc, mySkocko, oppSkocko,
                myMojBroj, oppMojBroj, mySpojnice, oppSpojnice,
                myKorak, oppKorak, myTotal, oppTotal, opponentName);

        animateOutcome(myTotal >= oppTotal);

        // Ako imamo matchId, prvo proverimo je li ovo turnirski meč; tek onda
        // odlučujemo o nagradama i navigaciji.
        if (matchId != null) {
            matchRepository.getMatchInfo(matchId, new MatchRepository.MatchInfoListener() {
                @Override
                public void onInfo(@Nullable String tid, @Nullable String stage,
                                   @Nullable String player1Uid, @Nullable String player2Uid) {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    if (tid != null && stage != null) {
                        tournamentId = tid;
                        handleTournamentMatch(tid, stage, player1Uid, player2Uid);
                    } else {
                        handleNormalMatch();
                    }
                    setupNavigation();
                }

                @Override
                public void onError(@NonNull String message) {
                    // Ako ne uspemo da pročitamo meč, ponašamo se kao obična partija.
                    handleNormalMatch();
                    setupNavigation();
                }
            });
        } else {
            handleNormalMatch();
            setupNavigation();
        }
    }

    // =========================================================================
    // Obična partija (nepromenjeno ponašanje)
    // =========================================================================

    private void handleNormalMatch() {
        if (isFriendly) {
            TextView tvReward = findViewById(R.id.tvReward);
            tvReward.setText(R.string.result_reward_friendly);

            String uid = userRepository.getCurrentUid();
            if (uid != null) {
                dailyChallengeRepository.completeChallenge(uid, Constants.DAILY_CHALLENGE_PLAY_FRIENDLY_MATCH,
                        new DailyChallengeRepository.CompleteListener() {
                            @Override
                            public void onResult(boolean newlyCompleted, boolean bonusAwarded) {
                                showDailyChallengeToast(newlyCompleted, bonusAwarded);
                            }

                            @Override
                            public void onError(@NonNull String message) {
                                // Tiho — dnevni izazovi nisu kritični za tok rezultata partije.
                            }
                        });
            }
        } else {
            recordStatistics(myTotal, oppTotal);
            applyRewards(myTotal, oppTotal);
        }
    }

    /** Dodeljuje zvezde/žetone po pravilima i prikazuje ih ispod rezultata. */
    private void applyRewards(int myTotal, int oppTotal) {
        String uid = userRepository.getCurrentUid();
        if (uid == null) {
            return;
        }
        boolean won = myTotal > oppTotal;
        if (won) {
            dailyChallengeRepository.completeChallenge(uid, Constants.DAILY_CHALLENGE_WIN_MATCH,
                    new DailyChallengeRepository.CompleteListener() {
                        @Override
                        public void onResult(boolean newlyCompleted, boolean bonusAwarded) {
                            showDailyChallengeToast(newlyCompleted, bonusAwarded);
                        }

                        @Override
                        public void onError(@NonNull String message) {
                            // Tiho — dnevni izazovi nisu kritični za tok rezultata partije.
                        }
                    });
        }
        TextView tvReward = findViewById(R.id.tvReward);

        // Ligu pre partije čitamo unapred da bismo posle mogli da uporedimo sa
        // ligom posle nagrada i prikažemo obaveštenje o promeni (Student 2 — Lige).
        userRepository.getOrCreateUser(uid, new UserRepository.UserCallback() {
            @Override
            public void onSuccess(User beforeUser) {
                int leagueBefore = beforeUser.getCurrentLeague();
                applyRewardsAfterLeagueRead(uid, won, myTotal, tvReward, leagueBefore);
            }

            @Override
            public void onError(String message) {
                applyRewardsAfterLeagueRead(uid, won, myTotal, tvReward, -1);
            }
        });
    }

    private void applyRewardsAfterLeagueRead(String uid, boolean won, int myTotal,
                                             TextView tvReward, int leagueBefore) {
        userRepository.applyMatchRewards(uid, won, myTotal, new UserRepository.UserCallback() {
            @Override
            public void onSuccess(User user) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                int delta = won
                        ? MatchRewardCalculator.starsForWinner(myTotal)
                        : MatchRewardCalculator.starsDeltaForLoser(myTotal);
                String head = delta >= 0
                        ? getString(R.string.result_reward_gain, delta)
                        : getString(R.string.result_reward_loss, -delta);
                String totals = getString(R.string.result_reward_totals,
                        user.getTotalStars(), user.getTokens());
                tvReward.setText(head + "\n" + totals);

                if (leagueBefore >= 0 && user.getCurrentLeague() != leagueBefore) {
                    showLeagueChangeDialog(leagueBefore, user.getCurrentLeague());
                }
            }

            @Override
            public void onError(String message) {
                // Tiho — ako upis nagrada ne uspe, rezultat je i dalje prikazan.
            }
        });
    }

    /** Prikazuje dijalog i notifikaciju kada partija promeni ligu igrača (napredak ili pad). */
    private void showLeagueChangeDialog(int leagueBefore, int leagueAfter) {
        String[] names = getResources().getStringArray(R.array.leagues_array);
        String newLeagueName = leagueAfter >= 0 && leagueAfter < names.length
                ? names[leagueAfter] : String.valueOf(leagueAfter);
        boolean promoted = leagueAfter > leagueBefore;

        String title = getString(promoted ? R.string.league_up_title : R.string.league_down_title);
        String message = getString(promoted ? R.string.league_up_message : R.string.league_down_message,
                newLeagueName);

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setIcon(LeagueIconProvider.getDrawableRes(leagueAfter))
                .setPositiveButton(R.string.dialog_ok_got_it, null)
                .show();

        NotificationPoster.post(this, NotificationType.LEAGUE, title, message);
    }

    /** Prikazuje kratak Toast kada je dnevni izazov (i eventualno bonus) upravo osvojen. */
    private void showDailyChallengeToast(boolean newlyCompleted, boolean bonusAwarded) {
        if (!newlyCompleted || isFinishing() || isDestroyed()) {
            return;
        }
        String msg = getString(R.string.daily_challenge_toast_completed,
                Constants.DAILY_CHALLENGE_REWARD_STARS);
        if (bonusAwarded) {
            msg += "\n" + getString(R.string.daily_challenge_toast_bonus,
                    Constants.DAILY_CHALLENGE_ALL_BONUS_STARS, Constants.DAILY_CHALLENGE_ALL_BONUS_TOKENS);
        }
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    // =========================================================================
    // Turnirski meč
    // =========================================================================

    private void handleTournamentMatch(@NonNull String tid, @NonNull String stage,
                                       @Nullable String player1Uid, @Nullable String player2Uid) {
        String myUid = userRepository.getCurrentUid();
        if (myUid == null) {
            return;
        }
        boolean won = myTotal > oppTotal;
        String winnerUid = resolveWinnerUid(myUid, won, player1Uid, player2Uid);

        // Prijavljujemo pobednika u bracket (idempotentno — prvi upis pobeđuje).
        tournamentRepository.reportWinner(tid, stage, winnerUid);

        // Statistika profila se beleži i za turnir.
        userRepository.recordMatchResult(myUid, winnerUid.equals(myUid));

        // Turnirske nagrade — samo pobednik, po šemi za polufinale/finale.
        if (winnerUid.equals(myUid)) {
            // Pobeda u BILO KOM meču turnira (polufinale ili finale) ispunjava
            // dnevni izazov "Pobedi u turniru".
            dailyChallengeRepository.completeChallenge(myUid,
                    Constants.DAILY_CHALLENGE_WIN_TOURNAMENT_MATCH,
                    new DailyChallengeRepository.CompleteListener() {
                        @Override
                        public void onResult(boolean newlyCompleted, boolean bonusAwarded) {
                            showDailyChallengeToast(newlyCompleted, bonusAwarded);
                        }

                        @Override
                        public void onError(@NonNull String message) {
                            // Tiho — dnevni izazovi nisu kritični za tok rezultata partije.
                        }
                    });

            boolean isFinal = Constants.TOURNAMENT_SLOT_FINAL.equals(stage);
            int stars = isFinal
                    ? MatchRewardCalculator.starsForTournamentFinalWinner(myTotal)
                    : MatchRewardCalculator.starsForTournamentSemifinalWinner(myTotal);
            int tokens = isFinal
                    ? MatchRewardCalculator.TOURNAMENT_FINAL_TOKENS
                    : MatchRewardCalculator.TOURNAMENT_SEMIFINAL_TOKENS;
            userRepository.creditChallengeReward(myUid, stars, tokens, null);
            // Turnirska nagrada je čist dobitak (nema uloga koji se vraća), pa
            // ceo iznos ide na rang listu (nedeljni/mesečni brojač zvezda).
            userRepository.addLeaderboardStars(myUid, stars, null);

            TextView tvReward = findViewById(R.id.tvReward);
            tvReward.setText(getString(R.string.result_reward_gain, stars)
                    + "\n" + getString(R.string.tournament_reward_tokens, tokens));
        } else {
            TextView tvReward = findViewById(R.id.tvReward);
            tvReward.setText(R.string.tournament_reward_eliminated);
        }
    }

    /**
     * Određuje pobednika meča. Neodlučen ishod se razrešava determinističkim
     * "coin flip"-om po {@code matchId} tako da oba klijenta dobiju istog
     * pobednika bez obzira ko prvi prijavi rezultat.
     */
    @NonNull
    private String resolveWinnerUid(@NonNull String myUid, boolean won,
                                    @Nullable String player1Uid, @Nullable String player2Uid) {
        if (myTotal > oppTotal) {
            return myUid;
        }
        if (myTotal < oppTotal) {
            return opponentUid != null ? opponentUid : myUid;
        }
        // Nerešeno → coin flip. Padni na sopstveni uid ako uid-evi para nisu poznati.
        if (player1Uid == null || player2Uid == null) {
            return myUid;
        }
        boolean firstWins = matchId != null && (matchId.hashCode() & 1) == 0;
        return firstWins ? player1Uid : player2Uid;
    }

    // =========================================================================
    // Prikaz i animacija
    // =========================================================================

    private void bindResult(int myKzz, int oppKzz, int myAsoc, int oppAsoc,
                            int mySkocko, int oppSkocko, int myMojBroj, int oppMojBroj,
                            int mySpojnice, int oppSpojnice, int myKorak, int oppKorak,
                            int myTotal, int oppTotal, String opponentName) {
        TextView tvOutcome      = findViewById(R.id.tvOutcome);
        TextView tvPlayers      = findViewById(R.id.tvPlayers);
        TextView tvKzzScore     = findViewById(R.id.tvKzzScore);
        TextView tvAsocScore    = findViewById(R.id.tvAsocScore);
        TextView tvSkockoScore  = findViewById(R.id.tvSkockoScore);
        TextView tvMojBrojScore = findViewById(R.id.tvMojBrojScore);
        TextView tvSpojniceScore = findViewById(R.id.tvSpojniceScore);
        TextView tvKorakScore   = findViewById(R.id.tvKorakScore);
        TextView tvTotalScore   = findViewById(R.id.tvTotalScore);

        if (myTotal > oppTotal) {
            tvOutcome.setText(R.string.result_win);
        } else if (myTotal < oppTotal) {
            tvOutcome.setText(R.string.result_lose);
        } else {
            tvOutcome.setText(R.string.result_draw);
        }

        tvPlayers.setText(getString(R.string.result_players, getString(R.string.result_you),
                opponentName != null ? opponentName : "?"));

        tvKzzScore.setText(getString(R.string.result_score_row, myKzz, oppKzz));
        tvAsocScore.setText(getString(R.string.result_score_row, myAsoc, oppAsoc));
        tvSkockoScore.setText(getString(R.string.result_score_row, mySkocko, oppSkocko));
        tvMojBrojScore.setText(getString(R.string.result_score_row, myMojBroj, oppMojBroj));
        tvSpojniceScore.setText(getString(R.string.result_score_row, mySpojnice, oppSpojnice));
        tvKorakScore.setText(getString(R.string.result_score_row, myKorak, oppKorak));
        tvTotalScore.setText(getString(R.string.result_score_row, myTotal, oppTotal));
    }

    /** Pop-in animacija ishoda (pobeda malo poskoči, poraz se blago pojavi). */
    private void animateOutcome(boolean won) {
        TextView tvOutcome = findViewById(R.id.tvOutcome);
        tvOutcome.setAlpha(0f);
        float startScale = won ? 0.3f : 0.7f;
        tvOutcome.setScaleX(startScale);
        tvOutcome.setScaleY(startScale);

        ObjectAnimator alpha = ObjectAnimator.ofFloat(tvOutcome, "alpha", 0f, 1f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(tvOutcome, "scaleX", startScale, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(tvOutcome, "scaleY", startScale, 1f);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(alpha, scaleX, scaleY);
        set.setInterpolator(won ? new OvershootInterpolator(3f) : new OvershootInterpolator(0.8f));
        set.setDuration(won ? 650 : 450);
        set.start();
    }

    private void recordStatistics(int myTotal, int oppTotal) {
        String uid = userRepository.getCurrentUid();
        if (uid != null) {
            userRepository.recordMatchResult(uid, myTotal > oppTotal);
        }
    }

    // =========================================================================
    // Navigacija
    // =========================================================================

    private void setupNavigation() {
        MaterialButton btnHome = findViewById(R.id.btnHome);
        if (tournamentId != null) {
            btnHome.setText(R.string.tournament_btn_back_to_bracket);
        }
        btnHome.setOnClickListener(v -> navigateOnward());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                navigateOnward();
            }
        });
    }

    /** Turnir → nazad na bracket; obična partija → početni ekran. */
    private void navigateOnward() {
        Intent intent;
        if (tournamentId != null) {
            intent = new Intent(this, TournamentBracketActivity.class);
            intent.putExtra(Constants.EXTRA_TOURNAMENT_ID, tournamentId);
        } else {
            intent = new Intent(this, MainActivity.class);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

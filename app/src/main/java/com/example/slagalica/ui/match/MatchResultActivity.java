package com.example.slagalica.ui.match;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.example.slagalica.data.model.NotificationType;
import com.example.slagalica.data.model.User;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.logic.match.MatchRewardCalculator;
import com.example.slagalica.ui.main.MainActivity;
import com.example.slagalica.util.Constants;
import com.example.slagalica.util.NotificationPoster;
import com.google.android.material.button.MaterialButton;

import android.widget.TextView;

/**
 * Ekran sa rezultatom završenog meča (KT2).
 *
 * <p>Prikazuje bodove po igrama ("Ko zna zna" i "Spojnice") i ukupan rezultat,
 * te ishod iz ugla prijavljenog igrača. Beleži odigranu partiju (i pobedu) u
 * statistiku korisnika kroz {@link UserRepository#recordMatchResult}.</p>
 *
 * <p>Napomena: dodela/oduzimanje zvezda, tokeni i liga su deo funkcionalnosti
 * "3. Igranje partija" i "6. Lige", koje po raspodeli rade druge kolege; ovde
 * se beleži samo ono što ulazi u statistiku profila (Student 2).</p>
 */
public class MatchResultActivity extends AppCompatActivity {

    public static final String EXTRA_MY_KZZ        = Constants.EXTRA_MY_KZZ;
    public static final String EXTRA_OPP_KZZ       = Constants.EXTRA_OPP_KZZ;
    public static final String EXTRA_MY_SPOJNICE   = Constants.EXTRA_MY_SPOJNICE;
    public static final String EXTRA_OPP_SPOJNICE  = Constants.EXTRA_OPP_SPOJNICE;

    private final UserRepository userRepository = UserRepository.getInstance();

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

        int myTotal  = myKzz + myAsoc + mySkocko + myMojBroj + mySpojnice + myKorak;
        int oppTotal = oppKzz + oppAsoc + oppSkocko + oppMojBroj + oppSpojnice + oppKorak;
        boolean isFriendly = getIntent().getBooleanExtra(Constants.EXTRA_IS_FRIENDLY, false);

        bindResult(myKzz, oppKzz, myAsoc, oppAsoc, mySkocko, oppSkocko,
                myMojBroj, oppMojBroj, mySpojnice, oppSpojnice,
                myKorak, oppKorak, myTotal, oppTotal, opponentName);

        if (isFriendly) {
            // Prijateljska partija: bez statistike, zvezda i žetona.
            TextView tvReward = findViewById(R.id.tvReward);
            tvReward.setText(R.string.result_reward_friendly);
        } else {
            recordStatistics(myTotal, oppTotal);
            applyRewards(myTotal, oppTotal);
        }
        setupNavigation();
    }

    /** Dodeljuje zvezde/žetone po pravilima i prikazuje ih ispod rezultata. */
    private void applyRewards(int myTotal, int oppTotal) {
        String uid = userRepository.getCurrentUid();
        if (uid == null) {
            return;
        }
        boolean won = myTotal > oppTotal;
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
                .setPositiveButton(R.string.dialog_ok_got_it, null)
                .show();

        NotificationPoster.post(this, NotificationType.LEAGUE, title, message);
    }

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

    private void recordStatistics(int myTotal, int oppTotal) {
        String uid = userRepository.getCurrentUid();
        if (uid != null) {
            userRepository.recordMatchResult(uid, myTotal > oppTotal);
        }
    }

    private void setupNavigation() {
        MaterialButton btnHome = findViewById(R.id.btnHome);
        btnHome.setOnClickListener(v -> goHome());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                goHome();
            }
        });
    }

    private void goHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

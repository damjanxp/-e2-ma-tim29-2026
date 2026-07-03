package com.example.slagalica.ui.dailychallenge;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.data.model.DailyChallengeState;
import com.example.slagalica.data.repository.DailyChallengeRepository;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.util.Constants;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

/**
 * Ekran dnevnih izazova — lista izazova (žuto = u toku, zeleno = završeno),
 * status bonusa za sve izazove, i debug dugmad za reset/završavanje.
 *
 * <p><b>Kako dodati nov dnevni izazov (bez izmena ove klase):</b></p>
 * <ol>
 *   <li>Dodaj novi ID string u {@link Constants#DAILY_CHALLENGE_IDS}
 *       (u {@code util/Constants.java}).</li>
 *   <li>Dodaj naslov/opis u {@code strings.xml} i registruj ih u
 *       {@link DailyChallengeCatalog#entryFor}.</li>
 *   <li>Pozovi {@code DailyChallengeRepository.getInstance().completeChallenge(
 *       uid, TVOJ_NOVI_ID, ...)} na mestu gde se taj izazov ispunjava (npr. u
 *       aktivnosti odgovarajuće igre — po uzoru na pozive u
 *       {@code MatchResultActivity}).</li>
 * </ol>
 * <p>Lista, reset i "Završi sve" dugmad, kao i provera bonusa za sve izazove,
 * rade automatski jer iteriraju {@link Constants#DAILY_CHALLENGE_IDS} — nema
 * dodatnih izmena u ovom ekranu niti u repozitorijumu.</p>
 */
public class DailyChallengesActivity extends AppCompatActivity {

    private final UserRepository userRepository = UserRepository.getInstance();
    private final DailyChallengeRepository dailyChallengeRepository =
            DailyChallengeRepository.getInstance();

    private TextView tvBonusStatus;
    private MaterialButton btnReset;
    private MaterialButton btnCompleteAll;

    private DailyChallengeAdapter adapter;
    private String myUid;
    private Runnable stateDetacher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daily_challenges);

        initViews();
        setupToolbar();
        setupDebugButtons();
        loadUserThenListen();
    }

    private void initViews() {
        RecyclerView rvChallenges = findViewById(R.id.rvDailyChallenges);
        tvBonusStatus = findViewById(R.id.tvDailyBonusStatus);
        btnReset = findViewById(R.id.btnDailyReset);
        btnCompleteAll = findViewById(R.id.btnDailyCompleteAll);

        adapter = new DailyChallengeAdapter();
        rvChallenges.setLayoutManager(new LinearLayoutManager(this));
        rvChallenges.setAdapter(adapter);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    private void setupDebugButtons() {
        btnReset.setOnClickListener(v -> onResetClicked());
        btnCompleteAll.setOnClickListener(v -> onCompleteAllClicked());
    }

    private void loadUserThenListen() {
        userRepository.ensureSignedIn(new UserRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                myUid = userRepository.getCurrentUid();
                if (myUid == null) {
                    showErrorAndExit(getString(R.string.profile_error_load));
                    return;
                }
                stateDetacher = dailyChallengeRepository.listenState(myUid,
                        DailyChallengesActivity.this::renderState);
            }

            @Override
            public void onError(@NonNull String message) {
                showErrorAndExit(message);
            }
        });
    }

    private void renderState(@NonNull DailyChallengeState state) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        adapter.setState(state);

        boolean allDone = state.allCompleted(Constants.DAILY_CHALLENGE_IDS);
        tvBonusStatus.setText(allDone
                ? getString(R.string.daily_challenges_bonus_complete,
                        Constants.DAILY_CHALLENGE_ALL_BONUS_STARS, Constants.DAILY_CHALLENGE_ALL_BONUS_TOKENS)
                : getString(R.string.daily_challenges_bonus_incomplete,
                        Constants.DAILY_CHALLENGE_ALL_BONUS_STARS, Constants.DAILY_CHALLENGE_ALL_BONUS_TOKENS));
    }

    // =========================================================================
    // Debug dugmad
    // =========================================================================

    private void onResetClicked() {
        if (myUid == null) {
            return;
        }
        btnReset.setEnabled(false);
        dailyChallengeRepository.resetAllDebug(myUid, new DailyChallengeRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                if (isFinishing() || isDestroyed()) return;
                btnReset.setEnabled(true);
            }

            @Override
            public void onError(@NonNull String message) {
                if (isFinishing() || isDestroyed()) return;
                btnReset.setEnabled(true);
                Toast.makeText(DailyChallengesActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void onCompleteAllClicked() {
        if (myUid == null) {
            return;
        }
        btnCompleteAll.setEnabled(false);
        dailyChallengeRepository.completeAllDebug(myUid, new DailyChallengeRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                if (isFinishing() || isDestroyed()) return;
                btnCompleteAll.setEnabled(true);
            }

            @Override
            public void onError(@NonNull String message) {
                if (isFinishing() || isDestroyed()) return;
                btnCompleteAll.setEnabled(true);
                Toast.makeText(DailyChallengesActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showErrorAndExit(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (stateDetacher != null) {
            stateDetacher.run();
        }
    }
}

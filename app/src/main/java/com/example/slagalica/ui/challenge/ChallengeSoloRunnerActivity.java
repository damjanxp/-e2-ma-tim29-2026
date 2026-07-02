package com.example.slagalica.ui.challenge;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.example.slagalica.data.repository.ChallengeRepository;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.util.Constants;

/**
 * Orkestrira solo prolazak kroz svih šest igara za jedan izazov: pokreće
 * svaku igru redom u solo modu, sabira bodove i na kraju šalje ukupan
 * rezultat kroz {@link ChallengeRepository#submitScore}.
 *
 * <p>Sve igre imaju posebne solo verzije (bez čekanja na protivnika i bez
 * RTDB meča) jer su njihove standardne aktivnosti live 1v1 multiplayer.</p>
 */
public class ChallengeSoloRunnerActivity extends AppCompatActivity {

    private static final Class<?>[] GAME_ORDER = {
            ChallengeSoloKzzActivity.class,
            ChallengeSoloSpojniceActivity.class,
            ChallengeSoloAsocijacijeActivity.class,
            ChallengeSoloSkockoActivity.class,
            ChallengeSoloKorakActivity.class,
            ChallengeSoloMojBrojActivity.class,
    };

    private final UserRepository userRepository = UserRepository.getInstance();
    private final ChallengeRepository challengeRepository = ChallengeRepository.getInstance();

    private String challengeId;
    private int gameIndex = 0;
    private int totalScore = 0;

    private final ActivityResultLauncher<Intent> gameLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                int score = 0;
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    score = result.getData().getIntExtra(Constants.EXTRA_MY_SCORE, 0);
                }
                totalScore += score;
                gameIndex++;
                playNextOrFinish();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge_solo_runner);

        challengeId = getIntent().getStringExtra(Constants.EXTRA_CHALLENGE_ID);
        if (challengeId == null) {
            finish();
            return;
        }

        if (savedInstanceState == null) {
            playNextOrFinish();
        }
    }

    private void playNextOrFinish() {
        if (gameIndex >= GAME_ORDER.length) {
            submitScoreAndGoToResult();
            return;
        }
        Intent intent = new Intent(this, GAME_ORDER[gameIndex]);
        gameLauncher.launch(intent);
    }

    private void submitScoreAndGoToResult() {
        String uid = userRepository.getCurrentUid();
        if (uid == null) {
            goToResult();
            return;
        }
        challengeRepository.submitScore(challengeId, uid, totalScore,
                new ChallengeRepository.SimpleCallback() {
                    @Override
                    public void onSuccess() {
                        goToResult();
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(ChallengeSoloRunnerActivity.this, message, Toast.LENGTH_SHORT).show();
                        goToResult();
                    }
                });
    }

    private void goToResult() {
        Intent intent = new Intent(this, ChallengeResultActivity.class);
        intent.putExtra(Constants.EXTRA_CHALLENGE_ID, challengeId);
        startActivity(intent);
        finish();
    }
}

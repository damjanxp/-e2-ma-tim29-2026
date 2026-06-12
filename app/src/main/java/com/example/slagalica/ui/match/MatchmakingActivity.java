package com.example.slagalica.ui.match;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.example.slagalica.data.model.KzzQuestion;
import com.example.slagalica.data.model.SpojnicePuzzle;
import com.example.slagalica.data.model.User;
import com.example.slagalica.data.repository.GameContentRepository;
import com.example.slagalica.data.repository.MatchRepository;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.ui.games.KoZnaZnaActivity;
import com.example.slagalica.util.Constants;
import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * Ekran za uparivanje dva igrača (KT2).
 * Igrač klikom na "Igraj" ulazi u red za čekanje u Realtime Database;
 * kada se pronađe protivnik, kreator meča (player1) upisuje sadržaj igara
 * iz Firestore-a u meč i obojici se pokreće prva igra — "Ko zna zna".
 */
public class MatchmakingActivity extends AppCompatActivity {

    private final UserRepository userRepository = UserRepository.getInstance();
    private final MatchRepository matchRepository = MatchRepository.getInstance();
    private final GameContentRepository contentRepository = GameContentRepository.getInstance();

    private TextView tvStatus;
    private MaterialButton btnCancel;

    private String myUid;
    private boolean searching = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_matchmaking);

        tvStatus = findViewById(R.id.tvStatus);
        btnCancel = findViewById(R.id.btnCancel);
        btnCancel.setOnClickListener(v -> cancelAndExit());

        startSearch();
    }

    private void startSearch() {
        userRepository.ensureSignedIn(new UserRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                myUid = userRepository.getCurrentUid();
                if (myUid == null) {
                    showErrorAndExit(getString(R.string.profile_error_load));
                    return;
                }
                userRepository.getOrCreateUser(myUid, new UserRepository.UserCallback() {
                    @Override
                    public void onSuccess(@NonNull User user) {
                        joinQueue(user.getUsername());
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        showErrorAndExit(message);
                    }
                });
            }

            @Override
            public void onError(@NonNull String message) {
                showErrorAndExit(message);
            }
        });
    }

    private void joinQueue(String username) {
        searching = true;
        tvStatus.setText(R.string.matchmaking_searching);
        matchRepository.joinQueue(myUid, username, new MatchRepository.MatchmakingListener() {
            @Override
            public void onMatched(@NonNull String matchId, boolean isPlayerOne,
                                  @NonNull String opponentUid, @NonNull String opponentName) {
                searching = false;
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                tvStatus.setText(getString(R.string.matchmaking_found, opponentName));
                if (isPlayerOne) {
                    prepareMatchContent(matchId, opponentUid, opponentName);
                } else {
                    launchGame(matchId, false, opponentUid, opponentName);
                }
            }

            @Override
            public void onError(@NonNull String message) {
                searching = false;
                showErrorAndExit(message);
            }
        });
    }

    /** Kreator meča učitava sadržaj igara iz Firestore-a i upisuje ga u meč. */
    private void prepareMatchContent(String matchId, String opponentUid, String opponentName) {
        tvStatus.setText(R.string.matchmaking_preparing);
        contentRepository.loadKzzQuestions(Constants.KZZ_QUESTION_COUNT,
                new GameContentRepository.KzzCallback() {
            @Override
            public void onSuccess(@NonNull List<KzzQuestion> questions) {
                contentRepository.loadSpojnicePuzzles(Constants.SPOJNICE_ROUNDS,
                        new GameContentRepository.SpojniceCallback() {
                    @Override
                    public void onSuccess(@NonNull List<SpojnicePuzzle> puzzles) {
                        matchRepository.writeMatchContent(matchId, questions, puzzles,
                                new MatchRepository.SimpleCallback() {
                            @Override
                            public void onSuccess() {
                                launchGame(matchId, true, opponentUid, opponentName);
                            }

                            @Override
                            public void onError(@NonNull String message) {
                                showErrorAndExit(message);
                            }
                        });
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        showErrorAndExit(message);
                    }
                });
            }

            @Override
            public void onError(@NonNull String message) {
                showErrorAndExit(message);
            }
        });
    }

    private void launchGame(String matchId, boolean isPlayerOne,
                            String opponentUid, String opponentName) {
        Intent intent = new Intent(this, KoZnaZnaActivity.class);
        intent.putExtra(Constants.EXTRA_MATCH_ID, matchId);
        intent.putExtra(Constants.EXTRA_IS_PLAYER_ONE, isPlayerOne);
        intent.putExtra(Constants.EXTRA_OPPONENT_UID, opponentUid);
        intent.putExtra(Constants.EXTRA_OPPONENT_NAME, opponentName);
        startActivity(intent);
        finish();
    }

    private void cancelAndExit() {
        if (searching && myUid != null) {
            matchRepository.cancelQueue(myUid);
        }
        finish();
    }

    private void showErrorAndExit(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (searching && myUid != null) {
            matchRepository.cancelQueue(myUid);
        }
    }
}

package com.example.slagalica.ui.challenge;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.example.slagalica.data.model.Challenge;
import com.example.slagalica.data.model.ChallengeParticipant;
import com.example.slagalica.data.model.User;
import com.example.slagalica.data.repository.ChallengeRepository;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.util.Constants;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.Map;
import java.util.TreeMap;

/**
 * Lobi jednog izazova — prikazuje učesnike, omogućava pridruživanje i,
 * za domaćina, pokretanje izazova. Kad status pređe u "playing", automatski
 * pokreće solo rundu za trenutnog učesnika; kad pređe u "finished" (ili je
 * učesnik već završio svoju rundu), prelazi na ekran rezultata.
 */
public class ChallengeLobbyActivity extends AppCompatActivity {

    private final UserRepository userRepository = UserRepository.getInstance();
    private final ChallengeRepository challengeRepository = ChallengeRepository.getInstance();

    private TextView tvHostName;
    private TextView tvStake;
    private TextView tvStatus;
    private TextView tvParticipantsTitle;
    private LinearLayout llParticipants;
    private MaterialButton btnJoin;
    private MaterialButton btnStart;

    @Nullable private Runnable stopListening;
    @Nullable private User currentUser;
    @Nullable private String challengeId;

    private boolean soloLaunched = false;
    private boolean navigatedAway = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge_lobby);

        challengeId = getIntent().getStringExtra(Constants.EXTRA_CHALLENGE_ID);
        if (challengeId == null) {
            finish();
            return;
        }

        initViews();
        setupToolbar();
        loadUserAndStart();
    }

    private void initViews() {
        tvHostName = findViewById(R.id.tvHostName);
        tvStake = findViewById(R.id.tvStake);
        tvStatus = findViewById(R.id.tvStatus);
        tvParticipantsTitle = findViewById(R.id.tvParticipantsTitle);
        llParticipants = findViewById(R.id.llParticipants);
        btnJoin = findViewById(R.id.btnJoin);
        btnStart = findViewById(R.id.btnStart);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.challenge_lobby_title);
        }
    }

    private void loadUserAndStart() {
        userRepository.ensureSignedIn(new UserRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                String uid = userRepository.getCurrentUid();
                if (uid == null) {
                    return;
                }
                userRepository.getOrCreateUser(uid, new UserRepository.UserCallback() {
                    @Override
                    public void onSuccess(User user) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        currentUser = user;
                        attachChallengeListener();
                        setupButtons();
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(ChallengeLobbyActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ChallengeLobbyActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void attachChallengeListener() {
        if (challengeId == null) {
            return;
        }
        stopListening = challengeRepository.listenChallenge(challengeId, this::onChallenge);
    }

    private void onChallenge(@NonNull Challenge challenge) {
        if (isFinishing() || isDestroyed() || navigatedAway) {
            return;
        }
        renderChallenge(challenge);

        if ("finished".equals(challenge.getStatus())) {
            goToResult();
            return;
        }

        String myUid = currentUser != null ? currentUser.getUid() : null;
        Map<String, ChallengeParticipant> participants = challenge.getParticipants();
        ChallengeParticipant me = (myUid != null && participants != null)
                ? participants.get(myUid) : null;

        if (me == null) {
            return; // gledalac koji se još nije pridružio — samo prikaz stanja
        }
        if (me.isFinished()) {
            goToResult(); // već sam odigrao/la svoju rundu, čekam ostale
            return;
        }
        if ("playing".equals(challenge.getStatus()) && !soloLaunched) {
            soloLaunched = true;
            startSoloRun();
        }
    }

    private void renderChallenge(@NonNull Challenge challenge) {
        tvHostName.setText(getString(R.string.challenge_participant_host,
                challenge.getHostName() != null ? challenge.getHostName() : ""));
        tvStake.setText(getString(R.string.challenge_lobby_stake,
                challenge.getStakeStars(), challenge.getStakeTokens()));

        int statusRes;
        if ("playing".equals(challenge.getStatus())) {
            statusRes = R.string.challenge_status_playing;
        } else if ("finished".equals(challenge.getStatus())) {
            statusRes = R.string.challenge_status_finished;
        } else {
            statusRes = R.string.challenge_status_open;
        }
        tvStatus.setText(statusRes);

        Map<String, ChallengeParticipant> participants = challenge.getParticipants();
        int count = participants != null ? participants.size() : 0;
        tvParticipantsTitle.setText(getString(R.string.challenge_lobby_participants_title, count, 4));

        renderParticipants(challenge, participants);
        updateButtonVisibility(challenge, participants);
    }

    private void renderParticipants(@NonNull Challenge challenge,
                                    @Nullable Map<String, ChallengeParticipant> participants) {
        llParticipants.removeAllViews();
        if (participants == null) {
            return;
        }
        // TreeMap za stabilan (abecedan po uid) redosled prikaza između osvežavanja.
        for (ChallengeParticipant participant : new TreeMap<>(participants).values()) {
            View row = LayoutInflater.from(this)
                    .inflate(R.layout.item_challenge_participant, llParticipants, false);
            TextView tvName = row.findViewById(R.id.tvParticipantName);
            TextView tvStatusText = row.findViewById(R.id.tvParticipantStatus);

            String name = participant.getName() != null ? participant.getName() : "";
            boolean isHost = participant.getUid() != null
                    && participant.getUid().equals(challenge.getHostUid());
            boolean isMe = currentUser != null && participant.getUid() != null
                    && participant.getUid().equals(currentUser.getUid());
            if (isMe) {
                name = getString(R.string.challenge_participant_you, name);
            } else if (isHost) {
                name = getString(R.string.challenge_participant_host, name);
            }
            tvName.setText(name);
            tvStatusText.setText(participant.isFinished()
                    ? getString(R.string.challenge_participant_score_format, participant.getScore())
                    : "");

            llParticipants.addView(row);
        }
    }

    private void updateButtonVisibility(@NonNull Challenge challenge,
                                        @Nullable Map<String, ChallengeParticipant> participants) {
        if (currentUser == null) {
            btnJoin.setVisibility(View.GONE);
            btnStart.setVisibility(View.GONE);
            return;
        }
        boolean isOpen = "open".equals(challenge.getStatus());
        boolean isHost = currentUser.getUid().equals(challenge.getHostUid());
        boolean alreadyJoined = participants != null && participants.containsKey(currentUser.getUid());
        boolean isFull = participants != null && participants.size() >= 4;

        btnJoin.setVisibility(isOpen && !alreadyJoined && !isFull ? View.VISIBLE : View.GONE);
        btnStart.setVisibility(isOpen && isHost ? View.VISIBLE : View.GONE);
    }

    private void setupButtons() {
        btnJoin.setOnClickListener(v -> {
            if (currentUser == null || challengeId == null) {
                return;
            }
            btnJoin.setEnabled(false);
            challengeRepository.joinChallenge(challengeId, currentUser,
                    new ChallengeRepository.SimpleCallback() {
                        @Override
                        public void onSuccess() {
                            btnJoin.setEnabled(true);
                        }

                        @Override
                        public void onError(@NonNull String message) {
                            btnJoin.setEnabled(true);
                            Toast.makeText(ChallengeLobbyActivity.this, message, Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        btnStart.setOnClickListener(v -> {
            if (challengeId == null) {
                return;
            }
            btnStart.setEnabled(false);
            challengeRepository.startChallenge(challengeId, new ChallengeRepository.SimpleCallback() {
                @Override
                public void onSuccess() {
                    // status prelazi na "playing" — onChallenge() automatski pokreće solo rundu
                }

                @Override
                public void onError(@NonNull String message) {
                    btnStart.setEnabled(true);
                    Toast.makeText(ChallengeLobbyActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void startSoloRun() {
        Intent intent = new Intent(this, ChallengeSoloRunnerActivity.class);
        intent.putExtra(Constants.EXTRA_CHALLENGE_ID, challengeId);
        startActivity(intent);
        navigatedAway = true;
        finish();
    }

    private void goToResult() {
        if (navigatedAway) {
            return;
        }
        navigatedAway = true;
        Intent intent = new Intent(this, ChallengeResultActivity.class);
        intent.putExtra(Constants.EXTRA_CHALLENGE_ID, challengeId);
        startActivity(intent);
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (stopListening != null) {
            stopListening.run();
            stopListening = null;
        }
    }
}

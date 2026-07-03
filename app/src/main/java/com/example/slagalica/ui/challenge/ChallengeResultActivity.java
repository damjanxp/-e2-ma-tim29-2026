package com.example.slagalica.ui.challenge;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.example.slagalica.data.model.Challenge;
import com.example.slagalica.data.model.ChallengeParticipant;
import com.example.slagalica.data.repository.ChallengeRepository;
import com.example.slagalica.logic.match.ChallengePayout;
import com.example.slagalica.util.Constants;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Prikazuje stanje/rezultat jednog izazova. Dok status nije "finished",
 * prikazuje koliko je učesnika završilo solo rundu; kad izazov završi,
 * rangira učesnike i prikazuje ko je šta dobio (75% pobedniku, ulog nazad
 * drugoplasiranom) — vidi {@link ChallengePayout}.
 */
public class ChallengeResultActivity extends AppCompatActivity {

    private final ChallengeRepository challengeRepository = ChallengeRepository.getInstance();

    private TextView tvStatus;
    private LinearLayout llResults;
    private MaterialButton btnBackToList;

    @Nullable private Runnable stopListening;
    @Nullable private String challengeId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge_result);

        challengeId = getIntent().getStringExtra(Constants.EXTRA_CHALLENGE_ID);
        if (challengeId == null) {
            finish();
            return;
        }

        initViews();
        setupToolbar();
        btnBackToList.setOnClickListener(v -> finish());

        stopListening = challengeRepository.listenChallenge(challengeId, this::onChallenge);
    }

    private void initViews() {
        tvStatus = findViewById(R.id.tvStatus);
        llResults = findViewById(R.id.llResults);
        btnBackToList = findViewById(R.id.btnBackToList);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.challenge_result_title);
        }
    }

    private void onChallenge(@NonNull Challenge challenge) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        Map<String, ChallengeParticipant> participants = challenge.getParticipants();
        if (participants == null) {
            participants = new HashMap<>();
        }

        boolean finished = "finished".equals(challenge.getStatus());
        int finishedCount = 0;
        for (ChallengeParticipant p : participants.values()) {
            if (p.isFinished()) finishedCount++;
        }
        tvStatus.setText(finished
                ? getString(R.string.challenge_result_status_finished)
                : getString(R.string.challenge_result_status_waiting, finishedCount, participants.size()));

        // Bezbednosna mreža: raspodela nagrada se obično pokreće sa uređaja
        // poslednjeg učesnika koji završi (vidi ChallengeRepository#submitScore).
        // Ako taj klijent u međuvremenu prekine vezu pre nego što finalizacija
        // stigne da se izvrši, izazov bi ostao zaglavljen bez dodele nagrada.
        // Zato svaki klijent koji ovde primeti "svi završili, a status nije
        // finished" i sam pokuša finalizaciju — poziv je idempotentan.
        if (!finished && !participants.isEmpty() && finishedCount == participants.size()
                && challengeId != null) {
            challengeRepository.finalizeChallenge(challengeId);
        }

        List<ChallengeParticipant> ranked = new ArrayList<>(participants.values());
        ranked.sort((a, b) -> {
            if (b.getScore() != a.getScore()) {
                return Integer.compare(b.getScore(), a.getScore());
            }
            return Long.compare(a.getFinishedAt(), b.getFinishedAt());
        });

        Map<String, ChallengePayout.Payout> payouts = null;
        if (finished) {
            Map<String, Integer> scores = new HashMap<>();
            Map<String, Long> finishedAt = new HashMap<>();
            for (ChallengeParticipant p : participants.values()) {
                scores.put(p.getUid(), p.getScore());
                finishedAt.put(p.getUid(), p.getFinishedAt());
            }
            int potStars = challenge.getStakeStars() * participants.size();
            int potTokens = challenge.getStakeTokens() * participants.size();
            payouts = ChallengePayout.compute(scores, finishedAt, potStars, potTokens,
                    challenge.getStakeStars(), challenge.getStakeTokens());
        }

        renderResults(ranked, payouts, finished);
    }

    private void renderResults(@NonNull List<ChallengeParticipant> ranked,
                               @Nullable Map<String, ChallengePayout.Payout> payouts,
                               boolean finished) {
        llResults.removeAllViews();
        for (int i = 0; i < ranked.size(); i++) {
            ChallengeParticipant participant = ranked.get(i);
            View row = LayoutInflater.from(this)
                    .inflate(R.layout.item_challenge_result, llResults, false);

            TextView tvWinnerBadge = row.findViewById(R.id.tvResultWinnerBadge);
            TextView tvNameScore = row.findViewById(R.id.tvResultNameScore);
            TextView tvReward = row.findViewById(R.id.tvResultReward);

            String name = participant.getName() != null ? participant.getName() : "";
            tvNameScore.setText(getString(R.string.challenge_result_score_format,
                    name, participant.getScore()));

            boolean isWinner = finished && i == 0;
            tvWinnerBadge.setVisibility(isWinner ? View.VISIBLE : View.GONE);

            if (!finished) {
                tvReward.setVisibility(View.GONE);
            } else {
                tvReward.setVisibility(View.VISIBLE);
                ChallengePayout.Payout payout = payouts != null ? payouts.get(participant.getUid()) : null;
                if (payout == null || (payout.getStars() == 0 && payout.getTokens() == 0)) {
                    tvReward.setText(R.string.challenge_result_reward_none);
                } else if (isWinner) {
                    tvReward.setText(getString(R.string.challenge_result_reward_winner,
                            payout.getStars(), payout.getTokens()));
                } else {
                    tvReward.setText(getString(R.string.challenge_result_reward_refund,
                            payout.getStars(), payout.getTokens()));
                }
            }

            llResults.addView(row);
        }
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

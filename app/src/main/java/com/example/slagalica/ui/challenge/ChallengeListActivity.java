package com.example.slagalica.ui.challenge;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.data.model.Challenge;
import com.example.slagalica.data.model.User;
import com.example.slagalica.data.repository.ChallengeRepository;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.util.Constants;
import com.example.slagalica.util.RegionKey;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;

import java.util.List;

/**
 * Lista otvorenih izazova u regionu trenutnog korisnika, sa mogućnošću
 * kreiranja novog izazova.
 */
public class ChallengeListActivity extends AppCompatActivity {

    private final UserRepository userRepository = UserRepository.getInstance();
    private final ChallengeRepository challengeRepository = ChallengeRepository.getInstance();

    private RecyclerView rvChallenges;
    private TextView tvChallengesEmpty;
    private MaterialButton btnNewChallenge;

    private ChallengeAdapter adapter;
    @Nullable private Runnable stopListening;

    @Nullable private User currentUser;
    @Nullable private String regionKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge_list);

        initViews();
        setupToolbar();
        setupRecycler();
        btnNewChallenge.setOnClickListener(v -> showCreateChallengeDialog());
        btnNewChallenge.setEnabled(false);

        loadUserAndStart();
    }

    private void initViews() {
        rvChallenges = findViewById(R.id.rvChallenges);
        tvChallengesEmpty = findViewById(R.id.tvChallengesEmpty);
        btnNewChallenge = findViewById(R.id.btnNewChallenge);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.challenge_list_title);
        }
    }

    private void setupRecycler() {
        adapter = new ChallengeAdapter(this::openLobby);
        rvChallenges.setLayoutManager(new LinearLayoutManager(this));
        rvChallenges.setAdapter(adapter);
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
                        regionKey = RegionKey.toKey(user.getRegion());
                        btnNewChallenge.setEnabled(true);
                        attachChallengesListener();
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(ChallengeListActivity.this,
                                R.string.challenge_error_load_region, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ChallengeListActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void attachChallengesListener() {
        if (regionKey == null) {
            return;
        }
        stopListening = challengeRepository.listenOpenChallenges(regionKey,
                new ChallengeRepository.OpenChallengesListener() {
                    @Override
                    public void onChallenges(@NonNull List<Challenge> challenges) {
                        adapter.setItems(challenges);
                        boolean empty = challenges.isEmpty();
                        tvChallengesEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                        rvChallenges.setVisibility(empty ? View.GONE : View.VISIBLE);
                    }
                });
    }

    private void openLobby(@NonNull Challenge challenge) {
        Intent intent = new Intent(this, ChallengeLobbyActivity.class);
        intent.putExtra(Constants.EXTRA_CHALLENGE_ID, challenge.getId());
        startActivity(intent);
    }

    private void showCreateChallengeDialog() {
        if (currentUser == null) {
            return;
        }
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_create_challenge, null);
        TextView tvStarsLabel = view.findViewById(R.id.tvStakeStarsLabel);
        TextView tvTokensLabel = view.findViewById(R.id.tvStakeTokensLabel);
        Slider sliderStars = view.findViewById(R.id.sliderStakeStars);
        Slider sliderTokens = view.findViewById(R.id.sliderStakeTokens);

        tvStarsLabel.setText(getString(R.string.challenge_dialog_stars_label,
                (int) sliderStars.getValue()));
        tvTokensLabel.setText(getString(R.string.challenge_dialog_tokens_label,
                (int) sliderTokens.getValue()));

        sliderStars.addOnChangeListener((slider, value, fromUser) ->
                tvStarsLabel.setText(getString(R.string.challenge_dialog_stars_label, (int) value)));
        sliderTokens.addOnChangeListener((slider, value, fromUser) ->
                tvTokensLabel.setText(getString(R.string.challenge_dialog_tokens_label, (int) value)));

        new AlertDialog.Builder(this)
                .setTitle(R.string.challenge_dialog_title)
                .setView(view)
                .setPositiveButton(R.string.challenge_btn_create, (dialog, which) -> {
                    int stakeStars = (int) sliderStars.getValue();
                    int stakeTokens = (int) sliderTokens.getValue();
                    createChallenge(stakeStars, stakeTokens);
                })
                .setNegativeButton(R.string.challenge_btn_cancel, null)
                .show();
    }

    private void createChallenge(int stakeStars, int stakeTokens) {
        if (currentUser == null) {
            return;
        }
        btnNewChallenge.setEnabled(false);
        challengeRepository.createChallenge(currentUser, stakeStars, stakeTokens,
                new ChallengeRepository.SimpleCallback() {
                    @Override
                    public void onSuccess() {
                        btnNewChallenge.setEnabled(true);
                        Toast.makeText(ChallengeListActivity.this,
                                R.string.challenge_created_success, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        btnNewChallenge.setEnabled(true);
                        Toast.makeText(ChallengeListActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
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

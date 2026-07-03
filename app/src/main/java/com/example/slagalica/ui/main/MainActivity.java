package com.example.slagalica.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.example.slagalica.data.model.User;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.ui.auth.LoginActivity;
import com.example.slagalica.ui.challenge.ChallengeListActivity;
import com.example.slagalica.ui.chat.ChatActivity;
import com.example.slagalica.ui.dailychallenge.DailyChallengesActivity;
import com.example.slagalica.ui.games.GameMenuActivity;
import com.example.slagalica.ui.notifications.NotificationsActivity;
import com.example.slagalica.ui.profile.ProfileActivity;
import com.example.slagalica.ui.ranking.RankingActivity;
import com.example.slagalica.ui.tournament.TournamentLobbyListActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

/**
 * Glavni ekran aplikacije — navigaciona dugmad i statusni čipovi.
 *
 * <p>Statusna traka (žetoni, zvezde, liga) se u KT2 puni iz Firestore profila i
 * osvežava u {@code onResume()} kako bi posle partije prikazala aktuelno stanje
 * (Student 2).</p>
 */
public class MainActivity extends AppCompatActivity {

    private final UserRepository userRepository = UserRepository.getInstance();

    private Chip chipTokens;
    private Chip chipStars;
    private Chip chipLeague;

    private MaterialButton btnProfile;
    private MaterialButton btnPlay;
    private MaterialButton btnLeaderboard;
    private MaterialButton btnFriends;
    private MaterialButton btnRegion;
    private MaterialButton btnNotifications;
    private MaterialButton btnChat;
    private MaterialButton btnChallenge;
    private MaterialButton btnTournament;
    private MaterialButton btnDailyChallenges;
    private MaterialButton btnLogout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupToolbar();
        setupListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadStatusBar();
    }

    private void initViews() {
        chipTokens = findViewById(R.id.chipTokens);
        chipStars = findViewById(R.id.chipStars);
        chipLeague = findViewById(R.id.chipLeague);

        btnProfile = findViewById(R.id.btnProfile);
        btnPlay = findViewById(R.id.btnPlay);
        btnLeaderboard = findViewById(R.id.btnLeaderboard);
        btnFriends = findViewById(R.id.btnFriends);
        btnRegion = findViewById(R.id.btnRegion);
        btnNotifications = findViewById(R.id.btnNotifications);
        btnChat = findViewById(R.id.btnChat);
        btnChallenge = findViewById(R.id.btnChallenge);
        btnTournament = findViewById(R.id.btnTournament);
        btnDailyChallenges = findViewById(R.id.btnDailyChallenges);
        btnLogout = findViewById(R.id.btnLogout);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    private void setupListeners() {
        btnProfile.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));
        btnPlay.setOnClickListener(v ->
                startActivity(new Intent(this, GameMenuActivity.class)));
        btnLeaderboard.setOnClickListener(v ->
                startActivity(new Intent(this, RankingActivity.class)));
        btnFriends.setOnClickListener(v ->
                showComingSoon(getString(R.string.main_btn_friends)));
        btnRegion.setOnClickListener(v ->
                showComingSoon(getString(R.string.main_btn_region)));
        btnNotifications.setOnClickListener(v ->
                startActivity(new Intent(this, NotificationsActivity.class)));
        btnChat.setOnClickListener(v ->
                startActivity(new Intent(this, ChatActivity.class)));
        btnChallenge.setOnClickListener(v ->
                startActivity(new Intent(this, ChallengeListActivity.class)));
        btnTournament.setOnClickListener(v ->
                startActivity(new Intent(this, TournamentLobbyListActivity.class)));
        btnDailyChallenges.setOnClickListener(v ->
                startActivity(new Intent(this, DailyChallengesActivity.class)));
        btnLogout.setOnClickListener(v -> onLogoutClicked());
    }

    /** Učitava aktuelne žetone, zvezde i ligu iz Firestore profila. */
    private void loadStatusBar() {
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
                        chipTokens.setText(getString(R.string.main_chip_tokens, user.getTokens()));
                        chipStars.setText(getString(R.string.main_chip_stars, user.getTotalStars()));
                        chipLeague.setText(getString(R.string.main_chip_league, user.getCurrentLeague()));

                        // Dnevna dodela žetona (tiho; osvežava se pri sledećem ulasku na ekran).
                        userRepository.grantDailyTokensIfDue(uid, new UserRepository.SimpleCallback() {
                            @Override
                            public void onSuccess() { /* bez UI-ja */ }

                            @Override
                            public void onError(String message) { /* bez UI-ja */ }
                        });
                    }

                    @Override
                    public void onError(String message) {
                        // statusna traka ostaje na podrazumevanim vrednostima
                    }
                });
            }

            @Override
            public void onError(String message) {
                // bez konekcije statusna traka ostaje na podrazumevanim vrednostima
            }
        });
    }

    /** Prikazuje Toast poruku "Otvaranje X (uskoro)" za nedovršene funkcionalnosti. */
    private void showComingSoon(String sectionName) {
        Toast.makeText(this,
                getString(R.string.main_coming_soon, sectionName),
                Toast.LENGTH_SHORT).show();
    }

    /** Odjavljuje korisnika i vraća na ekran za prijavu. */
    private void onLogoutClicked() {
        userRepository.signOut();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

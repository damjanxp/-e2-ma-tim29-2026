package com.example.slagalica.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.example.slagalica.ui.auth.LoginActivity;
import com.example.slagalica.ui.games.GameMenuActivity;
import com.example.slagalica.ui.notifications.NotificationsActivity;
import com.example.slagalica.ui.profile.ProfileActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

/**
 * Glavni ekran aplikacije — prikazuje navigaciona dugmad i statusne čipove.
 * KT1: sve akcije su mock, bez Firebase poziva.
 */
public class MainActivity extends AppCompatActivity {

    // KT1: hardkodovane vrednosti — u KT2 se pune iz Firestore korisničkog profila
    private static final int MOCK_TOKENS = 5;
    private static final int MOCK_STARS = 0;
    private static final int MOCK_LEAGUE = 0;

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
    private MaterialButton btnLogout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupToolbar();
        setupChips();
        setupListeners();
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
        btnLogout = findViewById(R.id.btnLogout);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    private void setupChips() {
        chipTokens.setText(getString(R.string.main_chip_tokens, MOCK_TOKENS));
        chipStars.setText(getString(R.string.main_chip_stars, MOCK_STARS));
        chipLeague.setText(getString(R.string.main_chip_league, MOCK_LEAGUE));
    }

    private void setupListeners() {
        btnProfile.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));
        btnPlay.setOnClickListener(v ->
                startActivity(new Intent(this, GameMenuActivity.class)));
        btnLeaderboard.setOnClickListener(v ->
                showComingSoon(getString(R.string.main_btn_leaderboard)));
        btnFriends.setOnClickListener(v ->
                showComingSoon(getString(R.string.main_btn_friends)));
        btnRegion.setOnClickListener(v ->
                showComingSoon(getString(R.string.main_btn_region)));
        btnNotifications.setOnClickListener(v ->
                startActivity(new Intent(this, NotificationsActivity.class)));
        btnChat.setOnClickListener(v ->
                showComingSoon(getString(R.string.main_btn_chat)));
        btnLogout.setOnClickListener(v -> onLogoutClicked());
    }

    /** Prikazuje Toast poruku "Otvaranje X (uskoro)" za nedovršene funkcionalnosti. */
    private void showComingSoon(String sectionName) {
        Toast.makeText(this,
                getString(R.string.main_coming_soon, sectionName),
                Toast.LENGTH_SHORT).show();
    }

    /** Odjavljuje korisnika i vraća na ekran za prijavu. */
    private void onLogoutClicked() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}


package com.example.slagalica.ui.profile;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.example.slagalica.ui.auth.LoginActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

public class ProfileActivity extends AppCompatActivity {

    private static final String MOCK_USERNAME  = "marko123";
    private static final String MOCK_EMAIL     = "marko@example.com";
    private static final int    MOCK_TOKENS    = 5;
    private static final int    MOCK_STARS     = 42;
    private static final String MOCK_LEAGUE    = "Nulta liga";
    private static final String MOCK_REGION    = "Beograd";

    // Mock statistika
    private static final int   MOCK_TOTAL_GAMES    = 15;
    private static final int   MOCK_WIN_RATE       = 60;   // %
    private static final int   MOCK_KZZ_HIT        = 73;   // %
    private static final int   MOCK_KZZ_CORRECT    = 55;
    private static final int   MOCK_KZZ_WRONG      = 20;
    private static final int   MOCK_MOJ_BROJ       = 45;   // %
    private static final float MOCK_KORAK_AVG      = 3.2f; // prosečan korak
    private static final int   MOCK_ASOC_SOLVED    = 58;   // %
    private static final int   MOCK_SKOCKO_TOP2    = 35;   // % u 1-2 pokušaju
    private static final int   MOCK_SPOJNICE_HIT   = 78;   // %

    // Views
    private Chip          chipTokens;
    private Chip          chipStars;
    private Chip          chipLeague;
    private Chip          chipRegion;
    private TextView      tvUsername;
    private TextView      tvEmail;
    private TextView      tvTotalGames;
    private TextView      tvWinRate;
    private ProgressBar   pbWinRate;
    private TextView      tvStatKzz;
    private TextView      tvStatKzzDetail;
    private ProgressBar   pbKzz;
    private TextView      tvStatMojBroj;
    private ProgressBar   pbMojBroj;
    private TextView      tvStatKorak;
    private TextView      tvStatAsoc;
    private TextView      tvStatSkocko;
    private ProgressBar   pbSkocko;
    private TextView      tvStatSpojnice;
    private ProgressBar   pbSpojnice;
    private MaterialButton btnChangeAvatar;
    private MaterialButton btnLogout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        initViews();
        setupToolbar();
        bindMockData();
        setupListeners();
    }

    private void initViews() {
        chipTokens      = findViewById(R.id.chipTokens);
        chipStars       = findViewById(R.id.chipStars);
        chipLeague      = findViewById(R.id.chipLeague);
        chipRegion      = findViewById(R.id.chipRegion);
        tvUsername      = findViewById(R.id.tvUsername);
        tvEmail         = findViewById(R.id.tvEmail);
        tvTotalGames    = findViewById(R.id.tvTotalGames);
        tvWinRate       = findViewById(R.id.tvWinRate);
        pbWinRate       = findViewById(R.id.pbWinRate);
        tvStatKzz       = findViewById(R.id.tvStatKzz);
        tvStatKzzDetail = findViewById(R.id.tvStatKzzDetail);
        pbKzz           = findViewById(R.id.pbKzz);
        tvStatMojBroj   = findViewById(R.id.tvStatMojBroj);
        pbMojBroj       = findViewById(R.id.pbMojBroj);
        tvStatKorak     = findViewById(R.id.tvStatKorak);
        tvStatAsoc      = findViewById(R.id.tvStatAsoc);
        tvStatSkocko    = findViewById(R.id.tvStatSkocko);
        pbSkocko        = findViewById(R.id.pbSkocko);
        tvStatSpojnice  = findViewById(R.id.tvStatSpojnice);
        pbSpojnice      = findViewById(R.id.pbSpojnice);
        btnChangeAvatar = findViewById(R.id.btnChangeAvatar);
        btnLogout       = findViewById(R.id.btnLogout);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void bindMockData() {
        tvUsername.setText(MOCK_USERNAME);
        tvEmail.setText(MOCK_EMAIL);

        chipTokens.setText(getString(R.string.profile_label_tokens, MOCK_TOKENS));
        chipStars.setText(getString(R.string.profile_label_stars, MOCK_STARS));
        chipLeague.setText(getString(R.string.profile_label_league, MOCK_LEAGUE));
        chipRegion.setText(getString(R.string.profile_label_region, MOCK_REGION));

        // Opšta statistika
        tvTotalGames.setText(getString(R.string.profile_total_games, MOCK_TOTAL_GAMES));
        tvWinRate.setText(getString(R.string.profile_win_rate, MOCK_WIN_RATE, 100 - MOCK_WIN_RATE));
        pbWinRate.setProgress(MOCK_WIN_RATE);

        // Ko zna zna
        tvStatKzz.setText(getString(R.string.profile_stat_kzz, MOCK_KZZ_HIT));
        tvStatKzzDetail.setText(getString(R.string.profile_stat_kzz_detail, MOCK_KZZ_CORRECT, MOCK_KZZ_WRONG));
        pbKzz.setProgress(MOCK_KZZ_HIT);

        // Moj broj
        tvStatMojBroj.setText(getString(R.string.profile_stat_moj_broj, MOCK_MOJ_BROJ));
        pbMojBroj.setProgress(MOCK_MOJ_BROJ);

        // Korak po korak
        tvStatKorak.setText(getString(R.string.profile_stat_korak, MOCK_KORAK_AVG));

        // Asocijacije
        tvStatAsoc.setText(getString(R.string.profile_stat_asoc, MOCK_ASOC_SOLVED, 100 - MOCK_ASOC_SOLVED));

        // Skočko
        tvStatSkocko.setText(getString(R.string.profile_stat_skocko, MOCK_SKOCKO_TOP2));
        pbSkocko.setProgress(MOCK_SKOCKO_TOP2);

        // Spojnice
        tvStatSpojnice.setText(getString(R.string.profile_stat_spojnice, MOCK_SPOJNICE_HIT));
        pbSpojnice.setProgress(MOCK_SPOJNICE_HIT);
    }

    private void setupListeners() {
        btnChangeAvatar.setOnClickListener(v ->
                Toast.makeText(this,
                        getString(R.string.profile_toast_avatar_soon),
                        Toast.LENGTH_SHORT).show());

        btnLogout.setOnClickListener(v -> onLogoutClicked());
    }

    private void onLogoutClicked() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

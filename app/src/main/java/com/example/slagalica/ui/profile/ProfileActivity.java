package com.example.slagalica.ui.profile;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.example.slagalica.data.model.User;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.ui.auth.LoginActivity;
import com.example.slagalica.util.AvatarProvider;
import com.example.slagalica.util.QrCodeGenerator;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.imageview.ShapeableImageView;

/**
 * Ekran profila registrovanog korisnika (KT2).
 * Podaci se čitaju iz Firestore kolekcije "users": korisničko ime, email,
 * avatar sa okvirom, broj tokena, broj zvezda, liga, region, QR kod za poziv
 * prijatelja, statistika igara i opcija odjave.
 */
public class ProfileActivity extends AppCompatActivity {

    private static final int QR_SIZE_PX = 400;

    private final UserRepository userRepository = UserRepository.getInstance();

    // Views
    private ShapeableImageView ivAvatar;
    private ImageView      ivQrCode;
    private Chip           chipTokens;
    private Chip           chipStars;
    private Chip           chipLeague;
    private Chip           chipRegion;
    private TextView       tvUsername;
    private TextView       tvEmail;
    private TextView       tvTotalGames;
    private TextView       tvWinRate;
    private ProgressBar    pbWinRate;
    private TextView       tvStatKzz;
    private TextView       tvStatKzzDetail;
    private TextView       tvStatKzzAvg;
    private ProgressBar    pbKzz;
    private TextView       tvStatMojBroj;
    private ProgressBar    pbMojBroj;
    private TextView       tvStatKorak;
    private TextView       tvStatAsoc;
    private TextView       tvStatSkocko;
    private ProgressBar    pbSkocko;
    private TextView       tvStatSpojnice;
    private TextView       tvStatSpojniceAvg;
    private ProgressBar    pbSpojnice;
    private MaterialButton btnChangeAvatar;
    private MaterialButton btnLogout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        initViews();
        setupToolbar();
        setupListeners();
        loadProfile();
    }

    private void initViews() {
        ivAvatar        = findViewById(R.id.ivAvatar);
        ivQrCode        = findViewById(R.id.ivQrCode);
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
        tvStatKzzAvg    = findViewById(R.id.tvStatKzzAvg);
        pbKzz           = findViewById(R.id.pbKzz);
        tvStatMojBroj   = findViewById(R.id.tvStatMojBroj);
        pbMojBroj       = findViewById(R.id.pbMojBroj);
        tvStatKorak     = findViewById(R.id.tvStatKorak);
        tvStatAsoc      = findViewById(R.id.tvStatAsoc);
        tvStatSkocko    = findViewById(R.id.tvStatSkocko);
        pbSkocko        = findViewById(R.id.pbSkocko);
        tvStatSpojnice  = findViewById(R.id.tvStatSpojnice);
        tvStatSpojniceAvg = findViewById(R.id.tvStatSpojniceAvg);
        pbSpojnice      = findViewById(R.id.pbSpojnice);
        btnChangeAvatar = findViewById(R.id.btnChangeAvatar);
        btnLogout       = findViewById(R.id.btnLogout);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupListeners() {
        btnChangeAvatar.setOnClickListener(v -> showAvatarPicker());
        btnLogout.setOnClickListener(v -> onLogoutClicked());
    }

    // =========================================================================
    // Učitavanje profila
    // =========================================================================

    private void loadProfile() {
        userRepository.ensureSignedIn(new UserRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                String uid = userRepository.getCurrentUid();
                if (uid == null) {
                    showError(getString(R.string.profile_error_load));
                    return;
                }
                bindQrCode(uid);
                userRepository.getOrCreateUser(uid, new UserRepository.UserCallback() {
                    @Override
                    public void onSuccess(@NonNull User user) {
                        bindUser(user);
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        showError(message);
                    }
                });
            }

            @Override
            public void onError(@NonNull String message) {
                showError(message);
            }
        });
    }

    private void bindUser(User user) {
        tvUsername.setText(user.getUsername());
        tvEmail.setText(user.getEmail().isEmpty()
                ? getString(R.string.profile_no_email) : user.getEmail());

        ivAvatar.setImageResource(AvatarProvider.getDrawableForStored(user.getAvatarUrl()));

        chipTokens.setText(getString(R.string.profile_label_tokens, (int) user.getTokens()));
        chipStars.setText(getString(R.string.profile_label_stars, (int) user.getTotalStars()));
        chipLeague.setText(getString(R.string.profile_label_league, leagueName((int) user.getCurrentLeague())));
        chipLeague.setChipIconResource(R.drawable.ic_league);
        chipRegion.setText(getString(R.string.profile_label_region, user.getRegion()));

        bindStatistics(user);
    }

    private void bindStatistics(User user) {
        // Partije: ukupan broj i procenat pobeda/poraza
        tvTotalGames.setText(getString(R.string.profile_total_games, (int) user.getMatchesPlayed()));
        int winRate = percent(user.getMatchesWon(), user.getMatchesPlayed());
        tvWinRate.setText(getString(R.string.profile_win_rate, winRate, 100 - winRate));
        pbWinRate.setProgress(winRate);

        // Ko zna zna: odnos pogođenih i promašenih pitanja + prosečni bodovi
        long kzzTotal = user.getKzzCorrect() + user.getKzzWrong();
        int kzzPct = percent(user.getKzzCorrect(), kzzTotal);
        tvStatKzz.setText(getString(R.string.profile_stat_kzz, kzzPct));
        tvStatKzzDetail.setText(getString(R.string.profile_stat_kzz_detail,
                (int) user.getKzzCorrect(), (int) user.getKzzWrong()));
        tvStatKzzAvg.setText(getString(R.string.profile_stat_avg_points,
                average(user.getKzzPointsSum(), user.getKzzGames())));
        pbKzz.setProgress(kzzPct);

        // Moj broj: procenat pronađenog tačnog broja
        int mojBrojPct = percent(user.getMojBrojHits(), user.getMojBrojGames());
        tvStatMojBroj.setText(getString(R.string.profile_stat_moj_broj, mojBrojPct));
        pbMojBroj.setProgress(mojBrojPct);

        // Korak po korak: prosečan korak pogotka
        float korakAvg = user.getKorakHits() == 0
                ? 0f : (float) user.getKorakStepSum() / user.getKorakHits();
        tvStatKorak.setText(getString(R.string.profile_stat_korak, korakAvg));

        // Asocijacije: odnos rešenih i nerešenih
        long asocTotal = user.getAsocSolved() + user.getAsocUnsolved();
        int asocPct = percent(user.getAsocSolved(), asocTotal);
        tvStatAsoc.setText(getString(R.string.profile_stat_asoc, asocPct, 100 - asocPct));

        // Skočko: procenat pogođene kombinacije u 1–2. pokušaju
        int skockoPct = percent(user.getSkockoEarlyHits(), user.getSkockoGames());
        tvStatSkocko.setText(getString(R.string.profile_stat_skocko, skockoPct));
        pbSkocko.setProgress(skockoPct);

        // Spojnice: procenat uspešno povezanih pojmova + prosečni bodovi
        long spojniceTotal = user.getSpojniceConnected() + user.getSpojniceMissed();
        int spojnicePct = percent(user.getSpojniceConnected(), spojniceTotal);
        tvStatSpojnice.setText(getString(R.string.profile_stat_spojnice, spojnicePct));
        tvStatSpojniceAvg.setText(getString(R.string.profile_stat_avg_points,
                average(user.getSpojnicePointsSum(), user.getSpojniceGames())));
        pbSpojnice.setProgress(spojnicePct);
    }

    private void bindQrCode(String uid) {
        Bitmap qr = QrCodeGenerator.generate(uid, QR_SIZE_PX);
        if (qr != null) {
            ivQrCode.setImageBitmap(qr);
        }
    }

    // =========================================================================
    // Izmena avatara
    // =========================================================================

    private void showAvatarPicker() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_avatar_picker, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.profile_avatar_dialog_title)
                .setView(dialogView)
                .setNegativeButton(R.string.dialog_cancel, null)
                .create();

        int[] optionIds = {
                R.id.ivAvatarOption0, R.id.ivAvatarOption1, R.id.ivAvatarOption2,
                R.id.ivAvatarOption3, R.id.ivAvatarOption4, R.id.ivAvatarOption5
        };
        for (int i = 0; i < optionIds.length; i++) {
            final int avatarId = i;
            dialogView.findViewById(optionIds[i]).setOnClickListener(v -> {
                dialog.dismiss();
                changeAvatar(avatarId);
            });
        }
        dialog.show();
    }

    private void changeAvatar(int avatarId) {
        String uid = userRepository.getCurrentUid();
        if (uid == null) {
            return;
        }
        userRepository.updateAvatar(uid, avatarId, new UserRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                ivAvatar.setImageResource(AvatarProvider.getDrawableRes(avatarId));
                Toast.makeText(ProfileActivity.this,
                        getString(R.string.profile_toast_avatar_updated),
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(@NonNull String message) {
                showError(message);
            }
        });
    }

    // =========================================================================
    // Odjava i pomoćne metode
    // =========================================================================

    private void onLogoutClicked() {
        userRepository.signOut();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private String leagueName(int league) {
        String[] names = getResources().getStringArray(R.array.leagues_array);
        if (league < 0 || league >= names.length) {
            return names[0];
        }
        return names[league];
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private static int percent(long part, long total) {
        if (total <= 0) {
            return 0;
        }
        return (int) Math.round(part * 100.0 / total);
    }

    private static float average(long sum, long count) {
        if (count <= 0) {
            return 0f;
        }
        return (float) sum / count;
    }
}

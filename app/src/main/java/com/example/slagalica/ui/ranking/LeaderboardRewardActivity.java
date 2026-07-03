package com.example.slagalica.ui.ranking;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.example.slagalica.data.repository.LeaderboardRepository;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.util.Constants;
import com.google.android.material.button.MaterialButton;

/**
 * Popup koji se prikazuje kad igrač ima čekajuću nagradu sa zaključenog
 * ciklusa rang liste (vidi {@link LeaderboardRepository#getPendingReward}).
 *
 * <p>Potpuno samostalna — sama učitava svoju nagradu pri pokretanju (ne prima
 * ništa kroz Intent extras), pa se može otvoriti sa bilo kog mesta: odmah
 * nakon zaključenja ciklusa ({@code RankingActivity}), pri sledećem otvaranju
 * aplikacije ({@code MainActivity.onResume()}), ili klikom na obaveštenje u
 * istoriji ({@code NotificationsActivity}). Ako nema čekajuće nagrade (npr.
 * već pregledana negde drugde), tiho se zatvara bez prikaza.</p>
 *
 * <p>Ako igrač ima VIŠE čekajućih nagrada (npr. nedeljna i mesečna istog
 * dana), svaka se prikazuje pojedinačno — po zatvaranju jedne, odmah se
 * proverava sledeća, bez potrebe da se ekran ponovo otvara spolja.</p>
 */
public class LeaderboardRewardActivity extends AppCompatActivity {

    private final UserRepository userRepository = UserRepository.getInstance();
    private final LeaderboardRepository leaderboardRepository = LeaderboardRepository.getInstance();

    private TextView tvTrophy;
    private TextView tvRewardMessage;
    private TextView tvRewardTokens;
    private MaterialButton btnClaim;

    private String myUid;
    @Nullable private LeaderboardRepository.PendingReward reward;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboard_reward);

        tvTrophy        = findViewById(R.id.tvTrophy);
        tvRewardMessage = findViewById(R.id.tvRewardMessage);
        tvRewardTokens  = findViewById(R.id.tvRewardTokens);
        btnClaim        = findViewById(R.id.btnClaim);
        btnClaim.setEnabled(false);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { dismiss(); }
        });

        myUid = userRepository.getCurrentUid();
        if (myUid == null) {
            finish();
            return;
        }
        loadReward();
    }

    private void loadReward() {
        btnClaim.setEnabled(false);
        leaderboardRepository.getPendingReward(myUid, new LeaderboardRepository.PendingRewardListener() {
            @Override
            public void onResult(@Nullable LeaderboardRepository.PendingReward result) {
                if (isFinishing() || isDestroyed()) return;
                if (result == null) {
                    finish(); // ništa (više) ne čeka — tiho zatvori
                    return;
                }
                reward = result;
                bindReward(result);
                btnClaim.setEnabled(true);
                animateTrophy();
                playRewardSound();
            }

            @Override
            public void onError(@NonNull String message) {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(LeaderboardRewardActivity.this, message, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void bindReward(@NonNull LeaderboardRepository.PendingReward reward) {
        String typeAdj = Constants.LEADERBOARD_TYPE_MONTHLY.equals(reward.leaderboardType)
                ? getString(R.string.leaderboard_type_monthly_adj)
                : getString(R.string.leaderboard_type_weekly_adj);
        tvRewardMessage.setText(getString(R.string.leaderboard_reward_message, reward.rank, typeAdj));
        tvRewardTokens.setText(getString(R.string.leaderboard_reward_tokens, reward.tokens));
        btnClaim.setOnClickListener(v -> dismiss());
    }

    /** Poskočna animacija trofeja — ista "overshoot" stilistika kao ostatak aplikacije. */
    private void animateTrophy() {
        tvTrophy.setAlpha(0f);
        tvTrophy.setScaleX(0.2f);
        tvTrophy.setScaleY(0.2f);

        ObjectAnimator alpha  = ObjectAnimator.ofFloat(tvTrophy, "alpha", 0f, 1f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(tvTrophy, "scaleX", 0.2f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(tvTrophy, "scaleY", 0.2f, 1f);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(alpha, scaleX, scaleY);
        set.setInterpolator(new OvershootInterpolator(3f));
        set.setDuration(700);
        set.start();
    }

    /** Pušta podrazumevani zvuk obaveštenja uređaja — aplikacija ne pakuje sopstveni zvuk. */
    private void playRewardSound() {
        try {
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone ringtone = RingtoneManager.getRingtone(getApplicationContext(), uri);
            if (ringtone != null) {
                ringtone.play();
            }
        } catch (Exception e) {
            // Tiho — zvuk je samo ukras, ne sme da sruši ekran nagrade.
        }
    }

    /** Označava trenutnu nagradu viđenom, pa proverava ima li još čekajućih. */
    private void dismiss() {
        if (reward == null || myUid == null) {
            finish();
            return;
        }
        btnClaim.setEnabled(false);
        leaderboardRepository.markRewardSeen(myUid, reward, new LeaderboardRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                if (isFinishing() || isDestroyed()) return;
                reward = null;
                loadReward(); // eventualna sledeća čekajuća nagrada (npr. nedeljna + mesečna)
            }

            @Override
            public void onError(@NonNull String message) {
                // Ne blokiraj korisnika zbog mrežne greške — nagrada ostaje
                // "pending" i pojaviće se ponovo sledeći put.
                finish();
            }
        });
    }
}

package com.example.slagalica.ui.tournament;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.example.slagalica.data.model.KzzQuestion;
import com.example.slagalica.data.model.SpojnicePuzzle;
import com.example.slagalica.data.model.Tournament;
import com.example.slagalica.data.model.TournamentPlayer;
import com.example.slagalica.data.model.TournamentSlot;
import com.example.slagalica.data.repository.GameContentRepository;
import com.example.slagalica.data.repository.MatchRepository;
import com.example.slagalica.data.repository.TournamentRepository;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.ui.games.KoZnaZnaActivity;
import com.example.slagalica.ui.main.MainActivity;
import com.example.slagalica.util.AvatarProvider;
import com.example.slagalica.util.Constants;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

/**
 * Bracket ekran turnira — vizuelni prikaz ko igra protiv koga (avatar + nadimak),
 * uz "spreman" rukovanje po paru: meč jednog para počinje tek kada su oba igrača
 * spremna, nezavisno od drugog para. Tako se sva tri meča (dva polufinala +
 * finale) mogu odigrati u različito vreme, sa samo dva uređaja.
 *
 * <p>Meč se igra kroz postojeći live 1v1 lanac ({@link KoZnaZnaActivity} → …).
 * Pobednik se prijavljuje u {@link MatchResultActivity}, koji vraća igrača ovamo.
 * Kada su oba polufinala rešena, {@link TournamentRepository} automatski pravi
 * finale.</p>
 */
public class TournamentBracketActivity extends AppCompatActivity {

    private final TournamentRepository tournamentRepository = TournamentRepository.getInstance();
    private final UserRepository userRepository = UserRepository.getInstance();
    private final MatchRepository matchRepository = MatchRepository.getInstance();
    private final GameContentRepository contentRepository = GameContentRepository.getInstance();

    private String tid;
    private String myUid;

    private MaterialCardView cardFinal;
    private TextView tvChampion;
    private TextView tvStatus;
    private MaterialButton btnAction;

    @Nullable private Runnable tournamentDetacher;

    // Sprečava višestruko pokretanje istog meča iz uzastopnih onTournament poziva.
    private boolean launching = false;
    @Nullable private String launchedSlotKey;
    private boolean championAnimated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tournament_bracket);

        tid = getIntent().getStringExtra(Constants.EXTRA_TOURNAMENT_ID);
        myUid = userRepository.getCurrentUid();

        cardFinal = findViewById(R.id.cardFinal);
        tvChampion = findViewById(R.id.tvChampion);
        tvStatus = findViewById(R.id.tvStatus);
        btnAction = findViewById(R.id.btnAction);

        if (tid == null || myUid == null) {
            Toast.makeText(this, R.string.profile_error_load, Toast.LENGTH_LONG).show();
            goHome();
            return;
        }

        tournamentDetacher = tournamentRepository.listenTournament(tid, this::updateUi);
    }

    // =========================================================================
    // Render
    // =========================================================================

    private void updateUi(@NonNull Tournament t) {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        bindSlot(R.id.semi1p1, R.id.semi1p2, t, t.semifinal1);
        bindSlot(R.id.semi2p1, R.id.semi2p2, t, t.semifinal2);

        if (t.finalMatch.hasBothPlayers()) {
            cardFinal.setVisibility(View.VISIBLE);
            bindSlot(R.id.finalp1, R.id.finalp2, t, t.finalMatch);
        } else {
            cardFinal.setVisibility(View.GONE);
        }

        updateStatusAndAction(t);
    }

    private void bindSlot(int p1ViewId, int p2ViewId, @NonNull Tournament t,
                          @NonNull TournamentSlot slot) {
        boolean decided = slot.isDecided();
        boolean p1Winner = decided && slot.p1Uid != null && slot.p1Uid.equals(slot.winnerUid);
        boolean p2Winner = decided && slot.p2Uid != null && slot.p2Uid.equals(slot.winnerUid);
        bindPlayer(p1ViewId, t.player(slot.p1Uid), p1Winner, decided);
        bindPlayer(p2ViewId, t.player(slot.p2Uid), p2Winner, decided);
    }

    private void bindPlayer(int includeRootId, @Nullable TournamentPlayer player,
                            boolean isWinner, boolean decided) {
        View root = findViewById(includeRootId);
        ImageView iv = root.findViewById(R.id.ivPlayerAvatar);
        TextView tv = root.findViewById(R.id.tvPlayerName);

        if (player == null) {
            iv.setImageResource(AvatarProvider.getDrawableRes(0));
            tv.setText(R.string.tournament_tbd);
            root.setAlpha(1f);
            return;
        }
        iv.setImageResource(AvatarProvider.getDrawableRes(player.getAvatarIndex()));
        String name = player.getUsername();
        tv.setText(isWinner ? "🏆 " + name : name);
        // Poraženi u rešenom meču se blago zatamni.
        root.setAlpha(decided && !isWinner ? 0.4f : 1f);
    }

    // =========================================================================
    // Stanje i akcija (spreman-rukovanje / pokretanje meča / kraj)
    // =========================================================================

    private void updateStatusAndAction(@NonNull Tournament t) {
        boolean done = Constants.TOURNAMENT_STATUS_DONE.equals(t.status) || t.champion != null;
        if (done) {
            showChampion(t);
            return;
        }

        // Aktivni slot: finale ako sam finalista, inače moje polufinale.
        String activeKey;
        TournamentSlot active;
        if (t.finalMatch.contains(myUid)) {
            activeKey = Constants.TOURNAMENT_SLOT_FINAL;
            active = t.finalMatch;
        } else {
            activeKey = t.semifinalKeyFor(myUid);
            active = t.slotByKey(activeKey);
        }

        if (active == null) {
            // Nisam (još) ni u jednom meču — samo posmatram.
            tvStatus.setText(R.string.tournament_status_waiting_final);
            btnAction.setVisibility(View.GONE);
            return;
        }

        if (active.isDecided()) {
            if (myUid.equals(active.winnerUid)) {
                // Pobedio sam ovaj meč; čekam sledeću fazu (kreiranje finala / kraj).
                tvStatus.setText(Constants.TOURNAMENT_SLOT_FINAL.equals(activeKey)
                        ? R.string.tournament_status_waiting_final
                        : R.string.tournament_status_advanced);
                btnAction.setVisibility(View.GONE);
            } else {
                // Izgubio sam — ispao iz turnira.
                tvStatus.setText(R.string.tournament_status_eliminated);
                showHomeButton();
            }
            return;
        }

        // Meč još nije odigran → spreman-rukovanje.
        if (!active.hasBothPlayers()) {
            tvStatus.setText(R.string.tournament_status_waiting_final);
            btnAction.setVisibility(View.GONE);
            return;
        }

        if (active.bothReady()) {
            tvStatus.setText(R.string.tournament_status_starting);
            btnAction.setVisibility(View.GONE);
            launchMatch(t, active, activeKey);
        } else if (active.ready.contains(myUid)) {
            tvStatus.setText(R.string.tournament_status_waiting_opponent);
            btnAction.setVisibility(View.GONE);
        } else {
            tvStatus.setText(R.string.tournament_status_ready_prompt);
            btnAction.setText(R.string.tournament_btn_ready);
            btnAction.setVisibility(View.VISIBLE);
            final String key = activeKey;
            btnAction.setOnClickListener(v -> {
                btnAction.setVisibility(View.GONE);
                tournamentRepository.setReady(tid, key, myUid);
            });
        }
    }

    private void showChampion(@NonNull Tournament t) {
        tvChampion.setVisibility(View.VISIBLE);
        if (myUid.equals(t.champion)) {
            tvChampion.setText(R.string.tournament_you_champion);
        } else {
            TournamentPlayer champ = t.player(t.champion);
            tvChampion.setText(getString(R.string.tournament_champion,
                    champ != null ? champ.getUsername() : "?"));
        }
        if (!championAnimated) {
            championAnimated = true;
            animatePop(tvChampion);
        }
        tvStatus.setText(myUid.equals(t.champion)
                ? R.string.tournament_you_champion
                : R.string.tournament_status_eliminated);
        showHomeButton();
    }

    private void showHomeButton() {
        btnAction.setText(R.string.tournament_btn_home);
        btnAction.setVisibility(View.VISIBLE);
        btnAction.setOnClickListener(v -> goHome());
    }

    // =========================================================================
    // Pokretanje meča kroz postojeći 1v1 lanac
    // =========================================================================

    private void launchMatch(@NonNull Tournament t, @NonNull TournamentSlot slot,
                             @NonNull String slotKey) {
        if (launching || slotKey.equals(launchedSlotKey)) {
            return;
        }
        if (slot.matchId == null) {
            return;
        }
        launching = true;
        launchedSlotKey = slotKey;

        boolean amPlayerOne = myUid.equals(slot.p1Uid);
        String opponentUid = slot.opponentOf(myUid);
        TournamentPlayer opp = t.player(opponentUid);
        String opponentName = opp != null ? opp.getUsername() : "?";

        if (amPlayerOne) {
            // player1 upisuje sadržaj meča (kao u MatchmakingActivity), pa oboje ulaze.
            prepareContentThenLaunch(slot.matchId, opponentUid, opponentName);
        } else {
            launchGame(slot.matchId, false, opponentUid, opponentName);
        }
    }

    /** Učitava sadržaj igara iz Firestore-a i upisuje ga u meč pre pokretanja. */
    private void prepareContentThenLaunch(@NonNull String matchId,
                                          @Nullable String opponentUid, @NonNull String opponentName) {
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
                                onPrepareError(message);
                            }
                        });
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        onPrepareError(message);
                    }
                });
            }

            @Override
            public void onError(@NonNull String message) {
                onPrepareError(message);
            }
        });
    }

    private void onPrepareError(String message) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        // Dozvoli ponovni pokušaj pri sledećem osvežavanju stanja.
        launching = false;
        launchedSlotKey = null;
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void launchGame(@NonNull String matchId, boolean isPlayerOne,
                            @Nullable String opponentUid, @NonNull String opponentName) {
        Intent intent = new Intent(this, KoZnaZnaActivity.class);
        intent.putExtra(Constants.EXTRA_MATCH_ID, matchId);
        intent.putExtra(Constants.EXTRA_IS_PLAYER_ONE, isPlayerOne);
        intent.putExtra(Constants.EXTRA_OPPONENT_UID, opponentUid);
        intent.putExtra(Constants.EXTRA_OPPONENT_NAME, opponentName);
        startActivity(intent);
        finish(); // po povratku, MatchResultActivity otvara svež bracket
    }

    // =========================================================================
    // Animacija i navigacija
    // =========================================================================

    private void animatePop(@NonNull View view) {
        view.setAlpha(0f);
        view.setScaleX(0.3f);
        view.setScaleY(0.3f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", 0.3f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", 0.3f, 1f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(alpha, scaleX, scaleY);
        set.setInterpolator(new OvershootInterpolator(3f));
        set.setDuration(700);
        set.start();
    }

    private void goHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tournamentDetacher != null) {
            tournamentDetacher.run();
        }
    }
}

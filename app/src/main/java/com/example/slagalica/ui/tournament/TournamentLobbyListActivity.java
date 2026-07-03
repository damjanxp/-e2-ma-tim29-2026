package com.example.slagalica.ui.tournament;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.example.slagalica.data.model.User;
import com.example.slagalica.data.repository.TournamentRepository;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.util.AvatarProvider;
import com.example.slagalica.util.Constants;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Lista turnirskih lobija (fiksno tri, za demo). Svaki lobi prima do četiri
 * igrača; čim se jedan popuni, turnir za tu četvorku počinje i igrači prelaze na
 * {@link TournamentBracketActivity}.
 *
 * <p>Članstvo u lobiju je <b>trajno</b> — kada uđeš, ostaješ prijavljen/a i ako
 * napustiš ekran ili aplikaciju. Tako se lobi puni tokom vremena (i sa jednim
 * uređajem, naizmeničnom prijavom naloga). Samo dugme "Napusti lobi" te izbacuje.
 * Pripadnost se čita iz baze, pa se pri povratku na ekran ispravno prikaže da još
 * čekaš — a ako je turnir u međuvremenu počeo, odmah prelaziš u bracket.</p>
 */
public class TournamentLobbyListActivity extends AppCompatActivity {

    private final TournamentRepository tournamentRepository = TournamentRepository.getInstance();
    private final UserRepository userRepository = UserRepository.getInstance();

    private static final int[] LOBBY_VIEW_IDS = {R.id.lobby0, R.id.lobby1, R.id.lobby2};

    private final int[] counts = new int[Constants.TOURNAMENT_LOBBY_IDS.length];
    private final String[] tids = new String[Constants.TOURNAMENT_LOBBY_IDS.length];
    private final boolean[] members = new boolean[Constants.TOURNAMENT_LOBBY_IDS.length];
    private final List<Runnable> detachers = new ArrayList<>();

    private TextView tvStatus;
    private MaterialButton btnLeave;

    private String myUid;
    private String myUsername;
    private int myAvatarIndex;
    private boolean userLoaded = false;
    private boolean routed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tournament_lobby_list);

        tvStatus = findViewById(R.id.tvStatus);
        btnLeave = findViewById(R.id.btnCancel);
        btnLeave.setText(R.string.tournament_lobby_leave);
        btnLeave.setOnClickListener(v -> leaveMyLobby());

        for (int i = 0; i < Constants.TOURNAMENT_LOBBY_IDS.length; i++) {
            final int index = i;
            View root = findViewById(LOBBY_VIEW_IDS[i]);
            TextView tvName = root.findViewById(R.id.tvLobbyName);
            MaterialButton btnJoin = root.findViewById(R.id.btnLobbyJoin);
            tvName.setText(getString(R.string.tournament_lobby_name, i + 1));
            btnJoin.setEnabled(false); // do učitavanja profila
            btnJoin.setOnClickListener(v -> joinLobby(index));
        }

        loadUserThenListen();
    }

    private void loadUserThenListen() {
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
                        myUsername = user.getUsername();
                        myAvatarIndex = AvatarProvider.indexFromStored(user.getAvatarUrl());
                        userLoaded = true;
                        attachLobbyListeners();
                        renderLobbies();
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

    private void attachLobbyListeners() {
        for (int i = 0; i < Constants.TOURNAMENT_LOBBY_IDS.length; i++) {
            final int index = i;
            final String lobbyId = Constants.TOURNAMENT_LOBBY_IDS[i];
            detachers.add(tournamentRepository.listenLobby(lobbyId, myUid,
                    (count, formed, tid, iAmMember) -> {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        counts[index] = count;
                        tids[index] = tid;
                        members[index] = iAmMember;
                        // Ako sam član ovog lobija i turnir je formiran → u bracket.
                        if (!routed && iAmMember && tid != null) {
                            routed = true;
                            launchBracket(tid);
                            return;
                        }
                        renderLobbies();
                    }));
        }
    }

    /** Osvežava prikaz svih lobija i (ako sam član nekog) statusnu traku. */
    private void renderLobbies() {
        int myLobby = myLobbyIndex();
        boolean waiting = myLobby >= 0;

        for (int i = 0; i < Constants.TOURNAMENT_LOBBY_IDS.length; i++) {
            View root = findViewById(LOBBY_VIEW_IDS[i]);
            TextView tvCount = root.findViewById(R.id.tvLobbyCount);
            MaterialButton btnJoin = root.findViewById(R.id.btnLobbyJoin);

            tvCount.setText(getString(R.string.tournament_lobby_count,
                    counts[i], Constants.TOURNAMENT_SIZE));

            if (waiting) {
                // Dok sam član jednog lobija, ostali se ne mogu birati.
                btnJoin.setVisibility(View.GONE);
            } else {
                btnJoin.setVisibility(View.VISIBLE);
                boolean full = counts[i] >= Constants.TOURNAMENT_SIZE;
                boolean formed = tids[i] != null;
                btnJoin.setEnabled(userLoaded && !formed && !full);
                if (formed) {
                    btnJoin.setText(R.string.tournament_lobby_in_progress);
                } else if (full) {
                    btnJoin.setText(R.string.tournament_lobby_full);
                } else {
                    btnJoin.setText(R.string.tournament_lobby_join);
                }
            }
        }

        if (waiting) {
            tvStatus.setVisibility(View.VISIBLE);
            tvStatus.setText(getString(R.string.tournament_lobby_waiting,
                    counts[myLobby], Constants.TOURNAMENT_SIZE));
            btnLeave.setVisibility(View.VISIBLE);
        } else {
            tvStatus.setVisibility(View.GONE);
            btnLeave.setVisibility(View.GONE);
        }
    }

    private int myLobbyIndex() {
        for (int i = 0; i < members.length; i++) {
            if (members[i]) {
                return i;
            }
        }
        return -1;
    }

    private void joinLobby(int index) {
        if (!userLoaded || myLobbyIndex() >= 0) {
            return;
        }
        String lobbyId = Constants.TOURNAMENT_LOBBY_IDS[index];
        tournamentRepository.joinLobby(lobbyId, myUid, myUsername, myAvatarIndex,
                new TournamentRepository.JoinLobbyListener() {
                    @Override
                    public void onJoined(@NonNull String joinedId) {
                        // Prikaz se osvežava kroz listenLobby (members[] iz baze).
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        Toast.makeText(TournamentLobbyListActivity.this,
                                message, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /** Eksplicitno napuštanje lobija (dugme). Navigacija sa ekrana NE poziva ovo. */
    private void leaveMyLobby() {
        int myLobby = myLobbyIndex();
        if (myLobby >= 0 && myUid != null) {
            tournamentRepository.leaveLobby(Constants.TOURNAMENT_LOBBY_IDS[myLobby], myUid);
        }
    }

    private void launchBracket(String tournamentId) {
        tvStatus.setText(R.string.tournament_lobby_ready);
        Intent intent = new Intent(this, TournamentBracketActivity.class);
        intent.putExtra(Constants.EXTRA_TOURNAMENT_ID, tournamentId);
        startActivity(intent);
        finish();
    }

    private void showErrorAndExit(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Članstvo je trajno — NE napuštamo lobi pri izlasku sa ekrana.
        for (Runnable detacher : detachers) {
            detacher.run();
        }
    }
}

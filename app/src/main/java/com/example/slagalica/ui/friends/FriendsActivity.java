package com.example.slagalica.ui.friends;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.data.model.KzzQuestion;
import com.example.slagalica.data.model.SpojnicePuzzle;
import com.example.slagalica.data.model.User;
import com.example.slagalica.data.repository.FriendRepository;
import com.example.slagalica.data.repository.GameContentRepository;
import com.example.slagalica.data.repository.LeaderboardRepository;
import com.example.slagalica.data.repository.MatchRepository;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.ui.games.KoZnaZnaActivity;
import com.example.slagalica.util.AvatarProvider;
import com.example.slagalica.util.Constants;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ekran "7. Prijatelji" — lista prijatelja sa trenutnim podacima profila,
 * dodavanje prijatelja pretragom po korisničkom imenu ili skeniranjem QR
 * koda, i poziv prijatelja na partiju (prihvata/odbija se u roku od 10s).
 *
 * <p>Dolazni pozivi na partiju rade samo dok je ovaj ekran otvoren — nema
 * Cloud Functions/push-a sa servera koji bi probudio aplikaciju (ista
 * simplifikacija kao kod regionalnog četa, vidi {@code ChatRepository}).</p>
 */
public class FriendsActivity extends AppCompatActivity {

    private static final int INVITE_TIMEOUT_MS = 10_000;

    private final UserRepository userRepository = UserRepository.getInstance();
    private final FriendRepository friendRepository = FriendRepository.getInstance();
    private final MatchRepository matchRepository = MatchRepository.getInstance();
    private final GameContentRepository contentRepository = GameContentRepository.getInstance();
    private final LeaderboardRepository leaderboardRepository = LeaderboardRepository.getInstance();

    private RecyclerView rvFriends;
    private TextView tvEmpty;
    private View pbLoading;

    private FriendsAdapter adapter;

    @Nullable private String myUid;
    @Nullable private String myUsername;

    @Nullable private Runnable stopIncomingListener;
    @Nullable private Runnable stopInviteStatusListener;
    @Nullable private AlertDialog incomingInviteDialog;
    @Nullable private AlertDialog waitingDialog;
    @Nullable private CountDownTimer waitingTimer;
    @Nullable private CountDownTimer incomingTimer;
    @Nullable private String pendingInviteFriendUid;

    private final ActivityResultLauncher<Intent> qrScanLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                IntentResult scan = IntentIntegrator.parseActivityResult(
                        result.getResultCode(), result.getData());
                if (scan != null && scan.getContents() != null) {
                    onQrScanned(scan.getContents());
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friends);

        rvFriends = findViewById(R.id.rvFriends);
        tvEmpty = findViewById(R.id.tvFriendsEmpty);
        pbLoading = findViewById(R.id.pbFriendsLoading);

        setupToolbar();
        setupRecycler();
        setupAddFriendButton();
        loadMyProfileThenFriends();
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void setupRecycler() {
        String[] leagueNames = getResources().getStringArray(R.array.leagues_array);
        adapter = new FriendsAdapter(this::onPlayClicked, leagueNames);
        rvFriends.setLayoutManager(new LinearLayoutManager(this));
        rvFriends.setAdapter(adapter);
    }

    private void setupAddFriendButton() {
        MaterialButton btnAddFriend = findViewById(R.id.btnAddFriend);
        btnAddFriend.setOnClickListener(v -> showAddFriendDialog());
    }

    // =========================================================================
    // Učitavanje sopstvenog profila i liste prijatelja
    // =========================================================================

    private void loadMyProfileThenFriends() {
        pbLoading.setVisibility(View.VISIBLE);
        userRepository.ensureSignedIn(new UserRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                myUid = userRepository.getCurrentUid();
                if (myUid == null) {
                    pbLoading.setVisibility(View.GONE);
                    return;
                }
                userRepository.getOrCreateUser(myUid, new UserRepository.UserCallback() {
                    @Override
                    public void onSuccess(User user) {
                        myUsername = user.getUsername();
                        loadFriends();
                        attachIncomingInviteListener();
                    }

                    @Override
                    public void onError(String message) {
                        pbLoading.setVisibility(View.GONE);
                        Toast.makeText(FriendsActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String message) {
                pbLoading.setVisibility(View.GONE);
                Toast.makeText(FriendsActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadFriends() {
        if (myUid == null) return;
        friendRepository.getFriends(myUid, new FriendRepository.FriendsListCallback() {
            @Override
            public void onSuccess(@NonNull List<User> friends) {
                if (isFinishing() || isDestroyed()) return;
                pbLoading.setVisibility(View.GONE);
                tvEmpty.setVisibility(friends.isEmpty() ? View.VISIBLE : View.GONE);
                rvFriends.setVisibility(friends.isEmpty() ? View.GONE : View.VISIBLE);
                loadRanksThenBind(friends);
            }

            @Override
            public void onError(@NonNull String message) {
                if (isFinishing() || isDestroyed()) return;
                pbLoading.setVisibility(View.GONE);
                Toast.makeText(FriendsActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Učitava trenutni mesečni rang svakog prijatelja (specifikacija 7.c) pa tek onda puni listu. */
    private void loadRanksThenBind(@NonNull List<User> friends) {
        if (friends.isEmpty()) {
            adapter.setItems(friends, new HashMap<>());
            return;
        }
        Map<String, Integer> ranksByUid = new HashMap<>();
        int[] remaining = {friends.size()};
        for (User friend : friends) {
            leaderboardRepository.getMonthlyRank(friend.getMonthlyStars(), new LeaderboardRepository.RankCallback() {
                @Override
                public void onSuccess(int rank) {
                    ranksByUid.put(friend.getUid(), rank);
                    finishOne();
                }

                @Override
                public void onError(@NonNull String message) {
                    finishOne(); // tiho — red bez ranga i dalje prikazuje ostale podatke
                }

                private void finishOne() {
                    remaining[0]--;
                    if (remaining[0] == 0 && !isFinishing() && !isDestroyed()) {
                        adapter.setItems(friends, ranksByUid);
                    }
                }
            });
        }
    }

    // =========================================================================
    // Dodavanje prijatelja — pretraga i QR
    // =========================================================================

    private void showAddFriendDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_friend, null);
        TextInputEditText etSearch = view.findViewById(R.id.etSearchUsername);
        View pbSearch = view.findViewById(R.id.pbSearchLoading);
        LinearLayout container = view.findViewById(R.id.containerSearchResults);
        MaterialButton btnScanQr = view.findViewById(R.id.btnScanQr);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.friends_dialog_add_title)
                .setView(view)
                .setNegativeButton(R.string.dialog_cancel, null)
                .create();

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                runSearch(s.toString(), container, pbSearch, dialog);
            }

            @Override public void afterTextChanged(Editable s) { }
        });

        btnScanQr.setOnClickListener(v -> {
            dialog.dismiss();
            launchQrScanner();
        });

        dialog.show();
    }

    private void runSearch(String query, LinearLayout container, View pbSearch, AlertDialog dialog) {
        container.removeAllViews();
        if (query.trim().length() < 2 || myUid == null) {
            return;
        }
        pbSearch.setVisibility(View.VISIBLE);
        friendRepository.searchUsersByUsername(query.trim(), myUid, new FriendRepository.UserSearchCallback() {
            @Override
            public void onSuccess(@NonNull List<User> users) {
                if (isFinishing() || isDestroyed()) return;
                pbSearch.setVisibility(View.GONE);
                container.removeAllViews();
                for (User u : users) {
                    container.addView(buildSearchResultRow(u, container, dialog));
                }
            }

            @Override
            public void onError(@NonNull String message) {
                if (isFinishing() || isDestroyed()) return;
                pbSearch.setVisibility(View.GONE);
            }
        });
    }

    private View buildSearchResultRow(User u, LinearLayout container, AlertDialog dialog) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_friend_search_result, container, false);
        ShapeableImageView ivAvatar = row.findViewById(R.id.ivSearchAvatar);
        TextView tvUsername = row.findViewById(R.id.tvSearchUsername);
        MaterialButton btnAdd = row.findViewById(R.id.btnSearchAdd);

        ivAvatar.setImageResource(AvatarProvider.getDrawableForStored(u.getAvatarUrl()));
        tvUsername.setText(getString(R.string.friends_search_item_format, u.getUsername(), u.getRegion()));
        btnAdd.setOnClickListener(v -> addFriendAndClose(u, dialog));
        return row;
    }

    private void launchQrScanner() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        integrator.setPrompt(getString(R.string.friends_qr_scan_prompt));
        integrator.setBeepEnabled(false);
        integrator.setOrientationLocked(true);
        Intent intent = integrator.createScanIntent();
        qrScanLauncher.launch(intent);
    }

    private void onQrScanned(@NonNull String scannedUid) {
        if (myUid == null) return;
        if (scannedUid.equals(myUid)) {
            Toast.makeText(this, R.string.friends_error_self, Toast.LENGTH_SHORT).show();
            return;
        }
        userRepository.getUserIfExists(scannedUid, new UserRepository.UserCallback() {
            @Override
            public void onSuccess(User user) {
                addFriendAndClose(user, null);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(FriendsActivity.this, R.string.friends_error_qr_not_found, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addFriendAndClose(User friend, @Nullable AlertDialog dialogToClose) {
        if (myUid == null || myUsername == null) return;
        friendRepository.addFriend(myUid, myUsername, friend.getUid(), friend.getUsername(),
                new FriendRepository.SimpleCallback() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(FriendsActivity.this,
                                getString(R.string.friends_added_toast, friend.getUsername()),
                                Toast.LENGTH_SHORT).show();
                        if (dialogToClose != null) dialogToClose.dismiss();
                        loadFriends();
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        Toast.makeText(FriendsActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // =========================================================================
    // Poziv prijatelja na partiju (strana koja poziva)
    // =========================================================================

    private void onPlayClicked(@NonNull User friend) {
        if (myUid == null || myUsername == null) return;
        if (pendingInviteFriendUid != null) return; // poziv je već u toku — ignoriši dupli klik
        pendingInviteFriendUid = friend.getUid();

        friendRepository.sendMatchInvite(myUid, myUsername, friend.getUid(), new FriendRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                if (isFinishing() || isDestroyed()) {
                    pendingInviteFriendUid = null;
                    return;
                }
                showWaitingDialog(friend);
            }

            @Override
            public void onError(@NonNull String message) {
                pendingInviteFriendUid = null;
                Toast.makeText(FriendsActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showWaitingDialog(@NonNull User friend) {
        View view = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_1, null);
        TextView text = view.findViewById(android.R.id.text1);
        text.setPadding(48, 32, 48, 32);
        text.setText(getString(R.string.friends_waiting_message, friend.getUsername(), 10));

        waitingDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.friends_waiting_title)
                .setView(view)
                .setCancelable(false)
                .setNegativeButton(R.string.friends_btn_cancel_invite, (d, w) -> cancelPendingInvite())
                .show();

        stopInviteStatusListener = friendRepository.listenForInviteStatus(friend.getUid(), friend.getUsername(),
                new FriendRepository.InviteStatusListener() {
                    @Override
                    public void onPending() { /* i dalje čekamo */ }

                    @Override
                    public void onAccepted(@NonNull String matchId, @NonNull String friendUsername) {
                        onInviteAccepted(friend.getUid(), matchId, friendUsername);
                    }

                    @Override
                    public void onDeclined() {
                        onInviteDeclined(friend.getUid());
                    }
                });

        waitingTimer = new CountDownTimer(INVITE_TIMEOUT_MS, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (text.isAttachedToWindow()) {
                    text.setText(getString(R.string.friends_waiting_message, friend.getUsername(),
                            (millisUntilFinished / 1000) + 1));
                }
            }

            @Override
            public void onFinish() {
                onInviteTimedOut(friend.getUid());
            }
        }.start();
    }

    private void onInviteAccepted(String friendUid, String matchId, String friendUsername) {
        detachInviteWaiting();
        friendRepository.clearInvite(friendUid);
        if (waitingDialog != null) waitingDialog.dismiss();
        if (isFinishing() || isDestroyed() || myUid == null) return;

        // Kao "player1" (kreator poziva), moram da upišem sadržaj igara u meč
        // pre ulaska u prvu igru — isto što MatchmakingActivity radi za
        // kreatora nasumičnog meča (vidi MatchRepository#writeMatchContent).
        Toast.makeText(this, R.string.friends_preparing_match, Toast.LENGTH_SHORT).show();
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
                                                        launchFriendlyGame(matchId, friendUid, friendUsername);
                                                    }

                                                    @Override
                                                    public void onError(@NonNull String message) {
                                                        Toast.makeText(FriendsActivity.this, message, Toast.LENGTH_LONG).show();
                                                    }
                                                });
                                    }

                                    @Override
                                    public void onError(@NonNull String message) {
                                        Toast.makeText(FriendsActivity.this, message, Toast.LENGTH_LONG).show();
                                    }
                                });
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        Toast.makeText(FriendsActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void launchFriendlyGame(String matchId, String friendUid, String friendUsername) {
        if (isFinishing() || isDestroyed()) return;
        Intent intent = new Intent(this, KoZnaZnaActivity.class);
        intent.putExtra(Constants.EXTRA_MATCH_ID, matchId);
        intent.putExtra(Constants.EXTRA_IS_PLAYER_ONE, true);
        intent.putExtra(Constants.EXTRA_OPPONENT_UID, friendUid);
        intent.putExtra(Constants.EXTRA_OPPONENT_NAME, friendUsername);
        intent.putExtra(Constants.EXTRA_IS_FRIENDLY, true);
        startActivity(intent);
    }

    private void onInviteDeclined(String friendUid) {
        detachInviteWaiting();
        friendRepository.clearInvite(friendUid);
        if (waitingDialog != null) waitingDialog.dismiss();
        Toast.makeText(this, R.string.friends_invite_declined, Toast.LENGTH_SHORT).show();
    }

    private void onInviteTimedOut(String friendUid) {
        detachInviteWaiting();
        friendRepository.cancelMatchInvite(friendUid);
        if (waitingDialog != null) waitingDialog.dismiss();
        Toast.makeText(this, R.string.friends_invite_expired, Toast.LENGTH_SHORT).show();
    }

    private void cancelPendingInvite() {
        if (pendingInviteFriendUid != null) {
            friendRepository.cancelMatchInvite(pendingInviteFriendUid);
        }
        detachInviteWaiting();
        if (waitingDialog != null) waitingDialog.dismiss();
    }

    private void detachInviteWaiting() {
        if (stopInviteStatusListener != null) {
            stopInviteStatusListener.run();
            stopInviteStatusListener = null;
        }
        if (waitingTimer != null) {
            waitingTimer.cancel();
            waitingTimer = null;
        }
        waitingDialog = null;
        pendingInviteFriendUid = null;
    }

    // =========================================================================
    // Dolazni poziv na partiju (pozvana strana)
    // =========================================================================

    private void attachIncomingInviteListener() {
        if (myUid == null) return;
        stopIncomingListener = friendRepository.listenForIncomingInvites(myUid,
                new FriendRepository.IncomingInviteListener() {
                    @Override
                    public void onIncomingInvite(@NonNull String fromUid, @NonNull String fromName) {
                        showIncomingInviteDialog(fromUid, fromName);
                    }

                    @Override
                    public void onCleared() {
                        if (incomingInviteDialog != null) {
                            incomingInviteDialog.dismiss();
                            incomingInviteDialog = null;
                        }
                        if (incomingTimer != null) {
                            incomingTimer.cancel();
                            incomingTimer = null;
                        }
                    }
                });
    }

    private void showIncomingInviteDialog(@NonNull String fromUid, @NonNull String fromName) {
        if (incomingInviteDialog != null || isFinishing() || isDestroyed()) {
            return; // već prikazan dijalog za ovaj poziv
        }
        View view = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_1, null);
        TextView text = view.findViewById(android.R.id.text1);
        text.setPadding(48, 32, 48, 32);
        text.setText(getString(R.string.friends_incoming_message, fromName, 10));

        incomingInviteDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.friends_incoming_title)
                .setView(view)
                .setCancelable(false)
                .setPositiveButton(R.string.friends_btn_accept, (d, w) -> respondToIncoming(fromUid, fromName, true))
                .setNegativeButton(R.string.friends_btn_decline, (d, w) -> respondToIncoming(fromUid, fromName, false))
                .show();

        incomingTimer = new CountDownTimer(INVITE_TIMEOUT_MS, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (text.isAttachedToWindow()) {
                    text.setText(getString(R.string.friends_incoming_message, fromName,
                            (millisUntilFinished / 1000) + 1));
                }
            }

            @Override
            public void onFinish() {
                respondToIncoming(fromUid, fromName, false);
            }
        }.start();
    }

    private void respondToIncoming(@NonNull String fromUid, @NonNull String fromName, boolean accept) {
        if (myUid == null || myUsername == null) return;
        if (incomingTimer != null) {
            incomingTimer.cancel();
            incomingTimer = null;
        }
        if (incomingInviteDialog != null) {
            incomingInviteDialog.dismiss();
            incomingInviteDialog = null;
        }
        friendRepository.respondToInvite(myUid, myUsername, fromUid, fromName, accept,
                new FriendRepository.RespondCallback() {
                    @Override
                    public void onAccepted(@NonNull String matchId) {
                        if (isFinishing() || isDestroyed()) return;
                        Intent intent = new Intent(FriendsActivity.this, KoZnaZnaActivity.class);
                        intent.putExtra(Constants.EXTRA_MATCH_ID, matchId);
                        intent.putExtra(Constants.EXTRA_IS_PLAYER_ONE, false);
                        intent.putExtra(Constants.EXTRA_OPPONENT_UID, fromUid);
                        intent.putExtra(Constants.EXTRA_OPPONENT_NAME, fromName);
                        intent.putExtra(Constants.EXTRA_IS_FRIENDLY, true);
                        startActivity(intent);
                    }

                    @Override
                    public void onDeclined() {
                        // ništa dodatno — pošiljalac vidi status i čisti čvor
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        Toast.makeText(FriendsActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    protected void onResume() {
        super.onResume();
        loadFriends();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (stopIncomingListener != null) stopIncomingListener.run();
        if (stopInviteStatusListener != null) stopInviteStatusListener.run();
        if (waitingTimer != null) waitingTimer.cancel();
        if (incomingTimer != null) incomingTimer.cancel();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

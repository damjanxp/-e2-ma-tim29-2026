package com.example.slagalica.ui.chat;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.data.model.Message;
import com.example.slagalica.data.model.User;
import com.example.slagalica.data.repository.ChatRepository;
import com.example.slagalica.data.repository.DailyChallengeRepository;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.util.Constants;
import com.example.slagalica.util.RegionKey;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

/**
 * Regionalni čet — poruke se razmenjuju sa svim korisnicima istog regiona
 * kroz {@link ChatRepository}.
 */
public class ChatActivity extends AppCompatActivity {

    private final UserRepository userRepository = UserRepository.getInstance();
    private final ChatRepository chatRepository = ChatRepository.getInstance();
    private final DailyChallengeRepository dailyChallengeRepository = DailyChallengeRepository.getInstance();

    private RecyclerView rvMessages;
    private TextInputEditText etMessage;
    private MaterialButton btnSend;

    private ChatAdapter adapter;
    @Nullable private Runnable stopListening;

    private String myUid;
    private String myUsername;
    private String regionKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        initViews();
        setupToolbar();
        setupSendButton();
        loadUserAndStart();
    }

    private void initViews() {
        rvMessages = findViewById(R.id.rvMessages);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnSend.setEnabled(false);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.chat_title);
        }
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
                        myUid = uid;
                        myUsername = user.getUsername();
                        regionKey = RegionKey.toKey(user.getRegion());
                        setupRecycler();
                        attachMessagesListener();
                        btnSend.setEnabled(true);
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(ChatActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ChatActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupRecycler() {
        adapter = new ChatAdapter(myUid);
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(adapter);
    }

    private void attachMessagesListener() {
        stopListening = chatRepository.listenMessages(this, regionKey, new ChatRepository.MessagesListener() {
            @Override
            public void onMessages(@NonNull List<Message> messages) {
                adapter.setItems(messages);
                if (!messages.isEmpty()) {
                    rvMessages.scrollToPosition(messages.size() - 1);
                }
            }
        });
    }

    private void setupSendButton() {
        btnSend.setOnClickListener(v -> sendMessage());
    }

    private void sendMessage() {
        String text = etMessage.getText() != null
                ? etMessage.getText().toString().trim() : "";
        if (TextUtils.isEmpty(text) || regionKey == null) {
            return;
        }
        Message message = new Message();
        message.setSenderUid(myUid);
        message.setSenderName(myUsername);
        message.setText(text);
        message.setTimestamp(System.currentTimeMillis());

        btnSend.setEnabled(false);
        chatRepository.sendMessage(regionKey, message, new ChatRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                etMessage.setText("");
                btnSend.setEnabled(true);
                dailyChallengeRepository.completeChallenge(myUid, Constants.DAILY_CHALLENGE_SEND_CHAT_MESSAGE,
                        new DailyChallengeRepository.CompleteListener() {
                            @Override
                            public void onResult(boolean newlyCompleted, boolean bonusAwarded) {
                                // Tiho — čet ostaje fokusiran na poruke, bez dodatnih dijaloga.
                            }

                            @Override
                            public void onError(@NonNull String message) {
                                // Tiho — dnevni izazovi nisu kritični za tok četa.
                            }
                        });
            }

            @Override
            public void onError(@NonNull String message) {
                Toast.makeText(ChatActivity.this, message, Toast.LENGTH_SHORT).show();
                btnSend.setEnabled(true);
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
    protected void onResume() {
        super.onResume();
        ChatRepository.setChatActivityInForeground(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ChatRepository.setChatActivityInForeground(false);
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

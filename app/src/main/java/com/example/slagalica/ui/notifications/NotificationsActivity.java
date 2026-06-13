package com.example.slagalica.ui.notifications;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.data.model.AppNotification;
import com.example.slagalica.data.model.NotificationType;
import com.example.slagalica.data.repository.NotificationRepository;
import com.example.slagalica.util.NotificationChannelHelper;
import com.example.slagalica.util.NotificationPoster;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

public class NotificationsActivity extends AppCompatActivity
        implements NotificationsAdapter.OnNotificationClickListener {

    // One test notification per type, fired with a short stagger so the user
    // sees them arrive one by one in the system tray.
    private static final Object[][] TEST_NOTIFICATIONS = {
        { NotificationType.CHAT,         "Nova poruka",                  "Marko Vam je poslao/la poruku",        null        },
        { NotificationType.RANKING,      "Rang lista",                   "Postali ste #5 na rang listi",         null        },
        { NotificationType.REWARD,       "Dnevna nagrada",               "Otključali ste dnevnu nagradu",        "Preuzmi"   },
        { NotificationType.GENERAL,      "Sistemsko obaveštenje",        "Dobrodošli u novu ligu",               null        },
        { NotificationType.FRIEND_INVITE,"Zahtev za prijateljstvo",      "Ana Vam je poslala zahtev",            "Prihvati"  },
        { NotificationType.LEAGUE,       "Napredak u ligi",              "Uznapredovali ste do Srebrne lige!",   null        },
    };
    private static final long TEST_STAGGER_MS = 700L;

    private enum FilterState { ALL, READ, UNREAD }

    private RecyclerView   rvNotifications;
    private TextView       tvEmpty;
    private Chip           chipAll, chipRead, chipUnread;
    private MaterialButton btnMarkAllRead;
    private MaterialButton btnTestNotifications;

    private NotificationsAdapter  adapter;
    private NotificationRepository repository;

    private final List<AppNotification> allNotifications = new ArrayList<>();
    private FilterState currentFilter = FilterState.ALL;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    fireTestNotifications();
                } else {
                    Toast.makeText(this,
                            getString(R.string.notifications_permission_denied),
                            Toast.LENGTH_LONG).show();
                    // Still fire test entries into the in-app center even without tray permission.
                    fireTestNotifications();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        NotificationChannelHelper.createChannels(this);
        repository = NotificationRepository.getInstance();

        initViews();
        setupToolbar();
        setupRecycler();
        setupFilters();
        setupActions();
        loadNotifications();
    }

    private void initViews() {
        rvNotifications     = findViewById(R.id.rvNotifications);
        tvEmpty             = findViewById(R.id.tvNotificationsEmpty);
        chipAll             = findViewById(R.id.chipFilterAll);
        chipRead            = findViewById(R.id.chipFilterRead);
        chipUnread          = findViewById(R.id.chipFilterUnread);
        btnMarkAllRead      = findViewById(R.id.btnMarkAllRead);
        btnTestNotifications = findViewById(R.id.btnTestNotifications);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.notifications_title);
        }
    }

    private void setupRecycler() {
        adapter = new NotificationsAdapter(new ArrayList<>(), this);
        rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        rvNotifications.setAdapter(adapter);
    }

    private void setupFilters() {
        chipAll.setChecked(true);
        ChipGroup chipGroup = findViewById(R.id.chipGroupFilters);
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chipFilterRead)) {
                currentFilter = FilterState.READ;
            } else if (checkedIds.contains(R.id.chipFilterUnread)) {
                currentFilter = FilterState.UNREAD;
            } else {
                currentFilter = FilterState.ALL;
            }
            applyFilter();
        });
    }

    private void setupActions() {
        btnMarkAllRead.setOnClickListener(v -> {
            repository.markAllAsRead();
            for (AppNotification n : allNotifications) n.setRead(true);
            applyFilter();
        });

        btnTestNotifications.setOnClickListener(v -> requestPermissionAndTest());
    }

    // ── Test notifications ────────────────────────────────────────────────────

    private void requestPermissionAndTest() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) {
                fireTestNotifications();
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            fireTestNotifications();
        }
    }

    private void fireTestNotifications() {
        Toast.makeText(this, R.string.notifications_test_sending, Toast.LENGTH_SHORT).show();
        btnTestNotifications.setEnabled(false);

        Handler handler = new Handler(Looper.getMainLooper());
        for (int i = 0; i < TEST_NOTIFICATIONS.length; i++) {
            final Object[] row = TEST_NOTIFICATIONS[i];
            handler.postDelayed(() ->
                    NotificationPoster.post(
                            NotificationsActivity.this,
                            (NotificationType) row[0],
                            (String) row[1],
                            (String) row[2],
                            (String) row[3]),
                    i * TEST_STAGGER_MS);
        }

        // Reload the in-app list once all have been posted.
        handler.postDelayed(() -> {
            loadNotifications();
            btnTestNotifications.setEnabled(true);
        }, TEST_NOTIFICATIONS.length * TEST_STAGGER_MS + 300L);
    }

    // ── Notifications loading / display ───────────────────────────────────────

    private void loadNotifications() {
        repository.getAllNotifications(new NotificationRepository.NotificationListCallback() {
            @Override
            public void onSuccess(@NonNull List<AppNotification> notifications) {
                allNotifications.clear();
                allNotifications.addAll(notifications);
                applyFilter();
            }

            @Override
            public void onError(@NonNull String errorMessage) {
                Toast.makeText(NotificationsActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void applyFilter() {
        List<AppNotification> filtered = new ArrayList<>();
        for (AppNotification n : allNotifications) {
            if (currentFilter == FilterState.READ   && !n.isRead()) continue;
            if (currentFilter == FilterState.UNREAD &&  n.isRead()) continue;
            filtered.add(n);
        }
        adapter.setItems(filtered);
        updateEmptyState(filtered.isEmpty());
        updateMarkAllEnabled();
    }

    private void updateEmptyState(boolean isEmpty) {
        tvEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        rvNotifications.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void updateMarkAllEnabled() {
        boolean hasUnread = false;
        for (AppNotification n : allNotifications) {
            if (!n.isRead()) { hasUnread = true; break; }
        }
        btnMarkAllRead.setEnabled(hasUnread);
    }

    @Override
    public void onNotificationClicked(@NonNull AppNotification notification, int position) {
        if (!notification.isRead()) {
            notification.setRead(true);
            repository.markAsRead(notification.getId());
            applyFilter();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}

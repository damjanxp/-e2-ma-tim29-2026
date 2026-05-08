package com.example.slagalica.ui.notifications;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.data.model.AppNotification;
import com.example.slagalica.data.repository.NotificationRepository;
import com.example.slagalica.util.NotificationChannelHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * In-app notifications screen with filtering and read/unread handling.
 */
public class NotificationsActivity extends AppCompatActivity
        implements NotificationsAdapter.OnNotificationClickListener {

    private enum FilterState {
        ALL,
        READ,
        UNREAD
    }

    private RecyclerView rvNotifications;
    private TextView tvEmpty;
    private Chip chipAll;
    private Chip chipRead;
    private Chip chipUnread;
    private MaterialButton btnMarkAllRead;

    private NotificationsAdapter adapter;
    private NotificationRepository repository;

    private final List<AppNotification> allNotifications = new ArrayList<>();
    private FilterState currentFilter = FilterState.ALL;

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
        rvNotifications = findViewById(R.id.rvNotifications);
        tvEmpty = findViewById(R.id.tvNotificationsEmpty);
        chipAll = findViewById(R.id.chipFilterAll);
        chipRead = findViewById(R.id.chipFilterRead);
        chipUnread = findViewById(R.id.chipFilterUnread);
        btnMarkAllRead = findViewById(R.id.btnMarkAllRead);
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
        ChipGroup chipGroup = findViewById(R.id.chipGroupFilters);
        chipAll.setChecked(true);
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
            for (AppNotification notification : allNotifications) {
                notification.setRead(true);
            }
            applyFilter();
        });
    }

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
        for (AppNotification notification : allNotifications) {
            if (currentFilter == FilterState.READ && !notification.isRead()) {
                continue;
            }
            if (currentFilter == FilterState.UNREAD && notification.isRead()) {
                continue;
            }
            filtered.add(notification);
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
        for (AppNotification notification : allNotifications) {
            if (!notification.isRead()) {
                hasUnread = true;
                break;
            }
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
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}


package com.example.slagalica.data.repository;

import androidx.annotation.NonNull;

import com.example.slagalica.R;
import com.example.slagalica.data.model.AppNotification;
import com.example.slagalica.data.model.NotificationType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Repository for in-app notifications. Currently in-memory only.
 */
public class NotificationRepository {

    /** Callback for loading notifications. */
    public interface NotificationListCallback {
        void onSuccess(@NonNull List<AppNotification> notifications);

        void onError(@NonNull String errorMessage);
    }

    private static NotificationRepository instance;

    private final List<AppNotification> notifications = new ArrayList<>();

    private NotificationRepository() {
        seedMockData();
    }

    /**
     * Returns the singleton instance of the repository.
     */
    @NonNull
    public static synchronized NotificationRepository getInstance() {
        if (instance == null) {
            instance = new NotificationRepository();
        }
        return instance;
    }

    /**
     * Loads all notifications.
     */
    public void getAllNotifications(@NonNull NotificationListCallback callback) {
        callback.onSuccess(new ArrayList<>(notifications));
    }

    /**
     * Adds a new notification to the history.
     */
    public void addNotification(@NonNull AppNotification notification) {
        notifications.add(0, notification);
    }

    /**
     * Marks a single notification as read.
     */
    public void markAsRead(@NonNull String notificationId) {
        for (AppNotification notification : notifications) {
            if (notificationId.equals(notification.getId())) {
                notification.setRead(true);
                break;
            }
        }
    }

    /**
     * Marks all notifications as read.
     */
    public void markAllAsRead() {
        for (AppNotification notification : notifications) {
            notification.setRead(true);
        }
    }

    private void seedMockData() {
        long now = System.currentTimeMillis();

        notifications.add(new AppNotification(UUID.randomUUID().toString(),
                R.string.notification_title_chat,
                R.string.notification_message_chat,
                NotificationType.CHAT,
                now - TimeUnit.MINUTES.toMillis(5),
                false,
                null));

        notifications.add(new AppNotification(UUID.randomUUID().toString(),
                R.string.notification_title_ranking,
                R.string.notification_message_ranking,
                NotificationType.RANKING,
                now - TimeUnit.HOURS.toMillis(2),
                false,
                null));

        notifications.add(new AppNotification(UUID.randomUUID().toString(),
                R.string.notification_title_reward,
                R.string.notification_message_reward,
                NotificationType.REWARD,
                now - TimeUnit.HOURS.toMillis(6),
                true,
                R.string.notification_action_claim));

        notifications.add(new AppNotification(UUID.randomUUID().toString(),
                R.string.notification_title_general,
                R.string.notification_message_general,
                NotificationType.GENERAL,
                now - TimeUnit.DAYS.toMillis(1),
                true,
                null));

        notifications.add(new AppNotification(UUID.randomUUID().toString(),
                R.string.notification_title_friend_invite,
                R.string.notification_message_friend_invite,
                NotificationType.FRIEND_INVITE,
                now - TimeUnit.DAYS.toMillis(2),
                false,
                R.string.notification_action_accept));

        notifications.add(new AppNotification(UUID.randomUUID().toString(),
                R.string.notification_title_league,
                R.string.notification_message_league,
                NotificationType.LEAGUE,
                now - TimeUnit.DAYS.toMillis(4),
                true,
                null));
    }
}


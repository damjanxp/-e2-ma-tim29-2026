package com.example.slagalica.data.repository;

import androidx.annotation.NonNull;

import com.example.slagalica.data.model.AppNotification;
import com.example.slagalica.data.model.NotificationType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * In-memory store for in-app notifications.
 *
 * Porting path to persistence: replace addNotification / markAsRead / markAllAsRead
 * with Firestore writes; replace getAllNotifications with a Firestore query listener.
 * The rest of the codebase (NotificationPoster, NotificationsActivity) stays unchanged.
 */
public class NotificationRepository {

    public interface NotificationListCallback {
        void onSuccess(@NonNull List<AppNotification> notifications);
        void onError(@NonNull String errorMessage);
    }

    private static NotificationRepository instance;
    private final List<AppNotification> notifications = new ArrayList<>();

    private NotificationRepository() {
        seedMockData();
    }

    @NonNull
    public static synchronized NotificationRepository getInstance() {
        if (instance == null) {
            instance = new NotificationRepository();
        }
        return instance;
    }

    public void getAllNotifications(@NonNull NotificationListCallback callback) {
        callback.onSuccess(new ArrayList<>(notifications));
    }

    /** Called by NotificationPoster whenever a notification arrives (push or local). */
    public void addNotification(@NonNull AppNotification notification) {
        notifications.add(0, notification);
    }

    public void markAsRead(@NonNull String notificationId) {
        for (AppNotification n : notifications) {
            if (notificationId.equals(n.getId())) { n.setRead(true); break; }
        }
    }

    public void markAllAsRead() {
        for (AppNotification n : notifications) n.setRead(true);
    }

    // ── Seed data ─────────────────────────────────────────────────────────────

    private void seedMockData() {
        long now = System.currentTimeMillis();
        notifications.add(make("Nova poruka",
                "Marko Vam je poslao/la poruku",
                NotificationType.CHAT,
                now - TimeUnit.MINUTES.toMillis(5), false, null));

        notifications.add(make("Rang lista",
                "Postali ste #5 na rang listi",
                NotificationType.RANKING,
                now - TimeUnit.HOURS.toMillis(2), false, null));

        notifications.add(make("Dnevna nagrada",
                "Otključali ste dnevnu nagradu",
                NotificationType.REWARD,
                now - TimeUnit.HOURS.toMillis(6), true, "Preuzmi"));

        notifications.add(make("Liga",
                "Dobrodošli u novu ligu",
                NotificationType.GENERAL,
                now - TimeUnit.DAYS.toMillis(1), true, null));

        notifications.add(make("Zahtev za prijateljstvo",
                "Ana Vam je poslala zahtev za prijateljstvo",
                NotificationType.FRIEND_INVITE,
                now - TimeUnit.DAYS.toMillis(2), false, "Prihvati"));

        notifications.add(make("Nova liga",
                "Uznapredovali ste do Srebrne lige!",
                NotificationType.LEAGUE,
                now - TimeUnit.DAYS.toMillis(4), true, null));
    }

    private static AppNotification make(String title, String message,
                                        NotificationType type, long ts,
                                        boolean read, String actionLabel) {
        return new AppNotification(UUID.randomUUID().toString(),
                title, message, type, ts, read, actionLabel);
    }
}

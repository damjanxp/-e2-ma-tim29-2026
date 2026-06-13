package com.example.slagalica.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.data.model.AppNotification;
import com.example.slagalica.data.model.NotificationType;
import com.example.slagalica.util.NotificationChannelHelper;

import java.util.UUID;

/**
 * Facade over NotificationPoster and NotificationRepository.
 *
 * Use this class from feature code (e.g. after a match ends, after a friend
 * request is accepted) to fire a notification without depending on Android
 * notification APIs directly.
 *
 * Porting path: replace the body of post() to write a Firestore document that
 * a Cloud Function will read and forward to the recipient's FCM token.
 */
public class NotificationService {

    private static final String TAG = "NotificationService";

    private static NotificationService instance;
    private final Context appContext;

    private NotificationService(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        NotificationChannelHelper.createChannels(appContext);
    }

    @NonNull
    public static synchronized NotificationService getInstance(@NonNull Context context) {
        if (instance == null) instance = new NotificationService(context);
        return instance;
    }

    /**
     * Posts a notification to the system tray and to the in-app center.
     *
     * @param type        notification category (determines channel + badge colour)
     * @param title       notification headline
     * @param message     notification body text
     * @param actionLabel optional CTA label shown in the in-app center (e.g. "Prihvati")
     */
    public void post(@NonNull NotificationType type,
                     @NonNull String title,
                     @NonNull String message,
                     @Nullable String actionLabel) {
        com.example.slagalica.util.NotificationPoster.post(appContext, type, title, message, actionLabel);
    }

    /** Convenience overload without an action label. */
    public void post(@NonNull NotificationType type,
                     @NonNull String title,
                     @NonNull String message) {
        post(type, title, message, null);
    }

    /** @deprecated Use {@link #post(NotificationType, String, String, String)} instead. */
    @Deprecated
    @NonNull
    public AppNotification createNotification(@NonNull NotificationType type,
                                              @NonNull String title,
                                              @NonNull String message,
                                              @Nullable String actionLabel) {
        return new AppNotification(UUID.randomUUID().toString(),
                title, message, type, System.currentTimeMillis(), false, actionLabel);
    }

    /** @deprecated */
    @Deprecated
    public void showSystemNotification(@NonNull AppNotification notification) {
        Log.d(TAG, "showSystemNotification() is deprecated — use post() instead.");
    }

    /** @deprecated */
    @Deprecated
    public void saveNotification(@NonNull AppNotification notification) {
        NotificationRepository.getInstance().addNotification(notification);
    }

    /** @deprecated */
    @Deprecated
    public void handleNotificationClick(@NonNull String notificationId) {
        Log.d(TAG, "handleNotificationClick: " + notificationId);
    }
}

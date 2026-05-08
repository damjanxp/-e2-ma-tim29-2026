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
 * Helper service for future system notification handling and persistence.
 */
public class NotificationService {

    private static final String TAG = "NotificationService";

    private static NotificationService instance;

    private final Context appContext;
    private final NotificationRepository repository;

    private NotificationService(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.repository = NotificationRepository.getInstance();
        NotificationChannelHelper.createChannels(appContext);
    }

    /**
     * Returns the singleton instance of the notification service.
     */
    @NonNull
    public static synchronized NotificationService getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new NotificationService(context);
        }
        return instance;
    }

    /**
     * Creates a new notification model for future use.
     */
    @NonNull
    public AppNotification createNotification(@NonNull NotificationType type,
                                              int titleResId,
                                              int messageResId,
                                              @Nullable Integer actionResId) {
        return new AppNotification(UUID.randomUUID().toString(),
                titleResId,
                messageResId,
                type,
                System.currentTimeMillis(),
                false,
                actionResId);
    }

    /**
     * Shows a system notification (placeholder).
     */
    public void showSystemNotification(@NonNull AppNotification notification) {
        Log.d(TAG, "System notification placeholder: " + notification.getId());
        // TODO: Connect Firebase Messaging and NotificationCompat here.
    }

    /**
     * Persists a notification (placeholder).
     */
    public void saveNotification(@NonNull AppNotification notification) {
        repository.addNotification(notification);
        // TODO: Save to Firestore/Realtime Database when backend is ready.
    }

    /**
     * Handles notification click routing (placeholder).
     */
    public void handleNotificationClick(@NonNull String notificationId) {
        Log.d(TAG, "Notification click placeholder: " + notificationId);
        // TODO: Route to specific screens based on payload.
    }
}


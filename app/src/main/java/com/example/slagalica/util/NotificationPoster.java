package com.example.slagalica.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.slagalica.R;
import com.example.slagalica.data.model.AppNotification;
import com.example.slagalica.data.model.NotificationType;
import com.example.slagalica.data.repository.NotificationRepository;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Single entry-point for all notification delivery.
 *
 * Called from two places:
 *  1. The "Testiraj obaveštenja" button in NotificationsActivity (local demo).
 *  2. SlagalicaMessagingService.onMessageReceived() (real FCM push from server).
 *
 * Porting path to server-triggered notifications:
 *  - Keep this class as-is.
 *  - Have a Cloud Function write the FCM data payload to the target device.
 *  - SlagalicaMessagingService parses the payload and calls post() here.
 *  - No other code changes needed.
 */
public final class NotificationPoster {

    // System notification IDs start here; each call gets a unique ID.
    private static final AtomicInteger idGen = new AtomicInteger(9000);

    private NotificationPoster() {}

    /**
     * Posts a system tray notification AND adds an entry to the in-app center.
     *
     * @param context     any Context (application context is used internally)
     * @param type        determines the channel and badge colour
     * @param title       notification headline
     * @param message     notification body
     * @param actionLabel optional CTA label for the in-app card (e.g. "Prihvati")
     */
    public static void post(@NonNull Context context,
                            @NonNull NotificationType type,
                            @NonNull String title,
                            @NonNull String message,
                            @Nullable String actionLabel) {

        Context appCtx = context.getApplicationContext();
        NotificationChannelHelper.createChannels(appCtx);

        // ── System tray notification ──────────────────────────────────────────
        String channelId = NotificationChannelHelper.channelForType(type);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(appCtx, channelId)
                .setSmallIcon(R.drawable.ic_symbol_heart)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        // On Android 13+ this silently no-ops if POST_NOTIFICATIONS was denied.
        NotificationManagerCompat.from(appCtx).notify(idGen.getAndIncrement(), builder.build());

        // ── In-app notification center ────────────────────────────────────────
        AppNotification entry = new AppNotification(
                UUID.randomUUID().toString(),
                title, message, type,
                System.currentTimeMillis(),
                false,
                actionLabel);
        NotificationRepository.getInstance().addNotification(entry);
    }

    /** Convenience overload without an action label. */
    public static void post(@NonNull Context context,
                            @NonNull NotificationType type,
                            @NonNull String title,
                            @NonNull String message) {
        post(context, type, title, message, null);
    }
}

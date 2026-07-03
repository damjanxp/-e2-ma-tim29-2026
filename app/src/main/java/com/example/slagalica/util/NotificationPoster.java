package com.example.slagalica.util;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.slagalica.R;
import com.example.slagalica.data.model.AppNotification;
import com.example.slagalica.data.model.NotificationType;
import com.example.slagalica.data.repository.NotificationRepository;
import com.example.slagalica.ui.main.MainActivity;

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
        post(context, type, title, message, actionLabel, null);
    }

    /**
     * Same as {@link #post(Context, NotificationType, String, String, String)}, plus
     * an optional {@code relatedId} tag (see {@link AppNotification#getRelatedId()})
     * so the in-app center can route a tap to a specific screen (e.g. a
     * leaderboard reward popup) instead of just marking the entry as read.
     */
    public static void post(@NonNull Context context,
                            @NonNull NotificationType type,
                            @NonNull String title,
                            @NonNull String message,
                            @Nullable String actionLabel,
                            @Nullable String relatedId) {

        Context appCtx = context.getApplicationContext();
        NotificationChannelHelper.createChannels(appCtx);

        // ── System tray notification ──────────────────────────────────────────
        String channelId = NotificationChannelHelper.channelForType(type);
        int notificationId = idGen.getAndIncrement();

        // Tapping the tray notification opens the app; MainActivity.onResume()
        // is where any pending state (e.g. an unseen leaderboard reward) gets
        // picked back up, so no per-type routing is needed here.
        Intent openIntent = new Intent(appCtx, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT
                | (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent contentIntent = PendingIntent.getActivity(
                appCtx, notificationId, openIntent, piFlags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(appCtx, channelId)
                .setSmallIcon(R.drawable.ic_symbol_heart)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(contentIntent)
                .setAutoCancel(true);

        // On Android 13+ this silently no-ops if POST_NOTIFICATIONS was denied.
        NotificationManagerCompat.from(appCtx).notify(notificationId, builder.build());

        // ── In-app notification center ────────────────────────────────────────
        AppNotification entry = new AppNotification(
                UUID.randomUUID().toString(),
                title, message, type,
                System.currentTimeMillis(),
                false,
                actionLabel,
                relatedId);
        NotificationRepository.getInstance().addNotification(entry);
    }

    /** Convenience overload without an action label. */
    public static void post(@NonNull Context context,
                            @NonNull NotificationType type,
                            @NonNull String title,
                            @NonNull String message) {
        post(context, type, title, message, null, null);
    }
}

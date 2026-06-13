package com.example.slagalica.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

import com.example.slagalica.R;
import com.example.slagalica.data.model.NotificationType;

/**
 * Creates and maps Android notification channels.
 *
 * One channel per NotificationType so users can mute individual categories
 * from system settings. Adding a new NotificationType requires:
 *   1. A CHANNEL_XXX constant here
 *   2. A case in channelForType()
 *   3. Channel creation in createChannels()
 *   4. Channel name/desc strings in strings.xml
 */
public final class NotificationChannelHelper {

    public static final String CHANNEL_CHAT    = "chat_channel";
    public static final String CHANNEL_RANKING = "ranking_channel";
    public static final String CHANNEL_REWARD  = "reward_channel";
    public static final String CHANNEL_FRIEND  = "friend_channel";
    public static final String CHANNEL_LEAGUE  = "league_channel";
    public static final String CHANNEL_GENERAL = "general_channel";

    private NotificationChannelHelper() {}

    /** Returns the channel ID for the given notification type. */
    @NonNull
    public static String channelForType(@NonNull NotificationType type) {
        switch (type) {
            case CHAT:         return CHANNEL_CHAT;
            case RANKING:      return CHANNEL_RANKING;
            case REWARD:       return CHANNEL_REWARD;
            case FRIEND_INVITE: return CHANNEL_FRIEND;
            case LEAGUE:       return CHANNEL_LEAGUE;
            default:           return CHANNEL_GENERAL;
        }
    }

    /** Idempotent — safe to call multiple times (Android deduplicates). */
    public static void createChannels(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager mgr = context.getSystemService(NotificationManager.class);
        if (mgr == null) return;

        mgr.createNotificationChannel(build(context,
                CHANNEL_CHAT,
                R.string.notification_channel_chat_name,
                R.string.notification_channel_chat_desc,
                NotificationManager.IMPORTANCE_DEFAULT));

        mgr.createNotificationChannel(build(context,
                CHANNEL_RANKING,
                R.string.notification_channel_ranking_name,
                R.string.notification_channel_ranking_desc,
                NotificationManager.IMPORTANCE_DEFAULT));

        mgr.createNotificationChannel(build(context,
                CHANNEL_REWARD,
                R.string.notification_channel_reward_name,
                R.string.notification_channel_reward_desc,
                NotificationManager.IMPORTANCE_DEFAULT));

        mgr.createNotificationChannel(build(context,
                CHANNEL_FRIEND,
                R.string.notification_channel_friend_name,
                R.string.notification_channel_friend_desc,
                NotificationManager.IMPORTANCE_HIGH));

        mgr.createNotificationChannel(build(context,
                CHANNEL_LEAGUE,
                R.string.notification_channel_league_name,
                R.string.notification_channel_league_desc,
                NotificationManager.IMPORTANCE_DEFAULT));

        mgr.createNotificationChannel(build(context,
                CHANNEL_GENERAL,
                R.string.notification_channel_general_name,
                R.string.notification_channel_general_desc,
                NotificationManager.IMPORTANCE_LOW));
    }

    private static NotificationChannel build(@NonNull Context ctx, @NonNull String id,
                                             int nameRes, int descRes, int importance) {
        NotificationChannel ch = new NotificationChannel(id, ctx.getString(nameRes), importance);
        ch.setDescription(ctx.getString(descRes));
        return ch;
    }
}

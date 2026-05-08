package com.example.slagalica.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

import com.example.slagalica.R;

/**
 * Creates Android notification channels for the app.
 */
public final class NotificationChannelHelper {

    public static final String CHANNEL_CHAT = "chat_channel";
    public static final String CHANNEL_RANKING = "ranking_channel";
    public static final String CHANNEL_REWARD = "reward_channel";
    public static final String CHANNEL_GENERAL = "general_channel";

    private NotificationChannelHelper() {
    }

    /**
     * Creates all notification channels required by the app.
     */
    public static void createChannels(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }

        NotificationChannel chatChannel = new NotificationChannel(
                CHANNEL_CHAT,
                context.getString(R.string.notification_channel_chat_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        chatChannel.setDescription(context.getString(R.string.notification_channel_chat_desc));

        NotificationChannel rankingChannel = new NotificationChannel(
                CHANNEL_RANKING,
                context.getString(R.string.notification_channel_ranking_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        rankingChannel.setDescription(context.getString(R.string.notification_channel_ranking_desc));

        NotificationChannel rewardChannel = new NotificationChannel(
                CHANNEL_REWARD,
                context.getString(R.string.notification_channel_reward_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        rewardChannel.setDescription(context.getString(R.string.notification_channel_reward_desc));

        NotificationChannel generalChannel = new NotificationChannel(
                CHANNEL_GENERAL,
                context.getString(R.string.notification_channel_general_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        generalChannel.setDescription(context.getString(R.string.notification_channel_general_desc));

        manager.createNotificationChannel(chatChannel);
        manager.createNotificationChannel(rankingChannel);
        manager.createNotificationChannel(rewardChannel);
        manager.createNotificationChannel(generalChannel);
    }
}


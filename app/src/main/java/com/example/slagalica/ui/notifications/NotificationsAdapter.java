package com.example.slagalica.ui.notifications;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.data.model.AppNotification;
import com.example.slagalica.data.model.NotificationType;
import com.example.slagalica.util.DateUtils;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for the in-app notifications list.
 */
public class NotificationsAdapter extends RecyclerView.Adapter<NotificationsAdapter.NotificationViewHolder> {

    interface OnNotificationClickListener {
        void onNotificationClicked(@NonNull AppNotification notification, int position);
    }

    private final List<AppNotification> notifications = new ArrayList<>();
    @Nullable
    private final OnNotificationClickListener listener;

    NotificationsAdapter(@NonNull List<AppNotification> initial,
                         @Nullable OnNotificationClickListener listener) {
        notifications.addAll(initial);
        this.listener = listener;
    }

    public void setItems(@NonNull List<AppNotification> items) {
        notifications.clear();
        notifications.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        AppNotification notification = notifications.get(position);
        Context context = holder.itemView.getContext();

        holder.tvType.setText(getTypeLabel(context, notification.getType()));
        holder.tvType.setTextColor(ContextCompat.getColor(context, getTypeColor(notification.getType())));
        holder.tvTitle.setText(context.getString(notification.getTitleResId()));
        holder.tvMessage.setText(context.getString(notification.getMessageResId()));
        holder.tvTimestamp.setText(DateUtils.formatTimestamp(notification.getTimestampMillis()));

        if (notification.getActionResId() != null) {
            holder.tvAction.setVisibility(View.VISIBLE);
            holder.tvAction.setText(context.getString(notification.getActionResId()));
        } else {
            holder.tvAction.setVisibility(View.GONE);
        }

        int backgroundColor = ContextCompat.getColor(context,
                notification.isRead() ? R.color.notification_read_bg : R.color.notification_unread_bg);
        holder.card.setCardBackgroundColor(backgroundColor);
        holder.tvTitle.setTypeface(null, notification.isRead() ? Typeface.NORMAL : Typeface.BOLD);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onNotificationClicked(notification, holder.getAdapterPosition());
            }
        });
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    private String getTypeLabel(@NonNull Context context, @NonNull NotificationType type) {
        switch (type) {
            case CHAT:
                return context.getString(R.string.notification_type_chat);
            case RANKING:
                return context.getString(R.string.notification_type_ranking);
            case REWARD:
                return context.getString(R.string.notification_type_reward);
            case FRIEND_INVITE:
                return context.getString(R.string.notification_type_friend_invite);
            case LEAGUE:
                return context.getString(R.string.notification_type_league);
            case GENERAL:
            default:
                return context.getString(R.string.notification_type_general);
        }
    }

    @ColorRes
    private int getTypeColor(@NonNull NotificationType type) {
        switch (type) {
            case CHAT:
                return R.color.notification_type_chat;
            case RANKING:
                return R.color.notification_type_ranking;
            case REWARD:
                return R.color.notification_type_reward;
            case FRIEND_INVITE:
                return R.color.notification_type_friend;
            case LEAGUE:
                return R.color.notification_type_league;
            case GENERAL:
            default:
                return R.color.notification_type_general;
        }
    }

    static class NotificationViewHolder extends RecyclerView.ViewHolder {

        final MaterialCardView card;
        final TextView tvType;
        final TextView tvTitle;
        final TextView tvMessage;
        final TextView tvTimestamp;
        final TextView tvAction;

        NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.cardNotification);
            tvType = itemView.findViewById(R.id.tvNotificationType);
            tvTitle = itemView.findViewById(R.id.tvNotificationTitle);
            tvMessage = itemView.findViewById(R.id.tvNotificationMessage);
            tvTimestamp = itemView.findViewById(R.id.tvNotificationTimestamp);
            tvAction = itemView.findViewById(R.id.tvNotificationAction);
        }
    }
}


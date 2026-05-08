package com.example.slagalica.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Model for an in-app notification entry.
 */
public class AppNotification {

    private String id;
    private int titleResId;
    private int messageResId;
    private NotificationType type;
    private long timestampMillis;
    private boolean read;
    @Nullable
    private Integer actionResId;

    public AppNotification() {
    }

    public AppNotification(@NonNull String id, int titleResId, int messageResId,
                           @NonNull NotificationType type, long timestampMillis,
                           boolean read, @Nullable Integer actionResId) {
        this.id = id;
        this.titleResId = titleResId;
        this.messageResId = messageResId;
        this.type = type;
        this.timestampMillis = timestampMillis;
        this.read = read;
        this.actionResId = actionResId;
    }

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    public int getTitleResId() {
        return titleResId;
    }

    public void setTitleResId(int titleResId) {
        this.titleResId = titleResId;
    }

    public int getMessageResId() {
        return messageResId;
    }

    public void setMessageResId(int messageResId) {
        this.messageResId = messageResId;
    }

    @NonNull
    public NotificationType getType() {
        return type;
    }

    public void setType(@NonNull NotificationType type) {
        this.type = type;
    }

    public long getTimestampMillis() {
        return timestampMillis;
    }

    public void setTimestampMillis(long timestampMillis) {
        this.timestampMillis = timestampMillis;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    @Nullable
    public Integer getActionResId() {
        return actionResId;
    }

    public void setActionResId(@Nullable Integer actionResId) {
        this.actionResId = actionResId;
    }
}


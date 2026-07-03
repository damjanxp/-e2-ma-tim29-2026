package com.example.slagalica.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * In-app notification entry.
 *
 * Uses plain Strings for title/message so the same model can be hydrated
 * from Firestore documents or from an incoming FCM data payload without
 * requiring a Context to resolve resource IDs.
 */
public class AppNotification {

    private String id;
    private String title;
    private String message;
    private NotificationType type;
    private long timestampMillis;
    private boolean read;
    @Nullable private String actionLabel;
    /**
     * Generic pointer back to whatever this notification refers to (e.g. a
     * leaderboard reward marker — see {@code Constants.NOTIFICATION_RELATED_*}).
     * Optional; {@code null} for notifications that don't need one.
     */
    @Nullable private String relatedId;

    /** Required by Firestore deserialization. */
    public AppNotification() {}

    public AppNotification(@NonNull String id,
                           @NonNull String title,
                           @NonNull String message,
                           @NonNull NotificationType type,
                           long timestampMillis,
                           boolean read,
                           @Nullable String actionLabel) {
        this(id, title, message, type, timestampMillis, read, actionLabel, null);
    }

    public AppNotification(@NonNull String id,
                           @NonNull String title,
                           @NonNull String message,
                           @NonNull NotificationType type,
                           long timestampMillis,
                           boolean read,
                           @Nullable String actionLabel,
                           @Nullable String relatedId) {
        this.id             = id;
        this.title          = title;
        this.message        = message;
        this.type           = type;
        this.timestampMillis = timestampMillis;
        this.read           = read;
        this.actionLabel    = actionLabel;
        this.relatedId      = relatedId;
    }

    @NonNull public String getId()             { return id; }
    public void setId(@NonNull String id)      { this.id = id; }

    @NonNull public String getTitle()          { return title != null ? title : ""; }
    public void setTitle(@NonNull String t)    { this.title = t; }

    @NonNull public String getMessage()        { return message != null ? message : ""; }
    public void setMessage(@NonNull String m)  { this.message = m; }

    @NonNull public NotificationType getType() { return type; }
    public void setType(@NonNull NotificationType type) { this.type = type; }

    public long getTimestampMillis()           { return timestampMillis; }
    public void setTimestampMillis(long ts)    { this.timestampMillis = ts; }

    public boolean isRead()                    { return read; }
    public void setRead(boolean read)          { this.read = read; }

    @Nullable public String getActionLabel()               { return actionLabel; }
    public void setActionLabel(@Nullable String actionLabel) { this.actionLabel = actionLabel; }

    @Nullable public String getRelatedId()                 { return relatedId; }
    public void setRelatedId(@Nullable String relatedId)   { this.relatedId = relatedId; }
}

package com.example.slagalica.data.repository;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.slagalica.data.model.NotificationType;
import com.example.slagalica.util.NotificationPoster;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/**
 * Receives FCM messages and token refreshes.
 *
 * Expected FCM data payload keys:
 *   "type"        — NotificationType name (e.g. "CHAT", "RANKING")
 *   "title"       — notification headline
 *   "message"     — notification body text
 *   "actionLabel" — (optional) CTA label shown in the in-app card
 *
 * Porting path: once the backend (Cloud Functions) is implemented, it will
 * write FCM data messages using these keys. This service handles them
 * transparently — no changes needed here.
 */
public class SlagalicaMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";

    /**
     * Called when a new FCM token is generated (first install or token refresh).
     * Stores the token under users/{uid}/fcmToken in Firestore so the backend
     * can target this device for push notifications.
     */
    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(TAG, "FCM token refreshed");
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .update("fcmToken", token)
                .addOnFailureListener(e -> Log.w(TAG, "Failed to store FCM token", e));
    }

    /**
     * Called when the app is in the foreground and a data message arrives,
     * OR when the app is in the background and a data-only message (no
     * notification key) is received.
     *
     * To send a notification from the backend, use a data-only FCM message
     * (do NOT include the "notification" key) so this handler is always invoked.
     */
    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        Map<String, String> data = message.getData();
        if (data.isEmpty()) return;

        String title       = data.get("title");
        String body        = data.get("message");
        String typeStr     = data.get("type");
        String actionLabel = data.get("actionLabel");

        if (title == null || body == null) {
            Log.w(TAG, "Received FCM message without title/message — ignored");
            return;
        }

        NotificationType type;
        try {
            type = NotificationType.valueOf(typeStr);
        } catch (Exception e) {
            type = NotificationType.GENERAL;
        }

        NotificationPoster.post(this, type, title, body, actionLabel);
    }
}

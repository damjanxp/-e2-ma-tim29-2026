package com.example.slagalica.data.repository;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.slagalica.R;
import com.example.slagalica.data.model.Message;
import com.example.slagalica.data.model.NotificationType;
import com.example.slagalica.util.Constants;
import com.example.slagalica.util.NotificationPoster;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Regionalni čet kroz Firebase Realtime Database.
 *
 * <p>Struktura podataka: {@code chats/{regionKey}/{messageId}}.</p>
 *
 * <p>Slušalac vraćen iz {@link #listenMessages} se obavezno uklanja u
 * {@code onDestroy()} pozivom vraćenog {@link Runnable}-a.</p>
 */
public class ChatRepository {

    /** Callback bez povratne vrednosti. */
    public interface SimpleCallback {
        void onSuccess();
        void onError(@NonNull String message);
    }

    /** Slušalac poruka jednog regiona, sortiranih rastuće po vremenu. */
    public interface MessagesListener {
        void onMessages(@NonNull List<Message> messages);
    }

    /**
     * Da li je {@code ChatActivity} trenutno u prvom planu — postavlja je sama
     * aktivnost u {@code onResume()}/{@code onPause()}. Koristi se da se izbegne
     * dupla notifikacija dok korisnik već gleda čet.
     */
    private static volatile boolean chatActivityInForeground = false;

    public static void setChatActivityInForeground(boolean inForeground) {
        chatActivityInForeground = inForeground;
    }

    private static ChatRepository instance;

    private final FirebaseDatabase database;

    private ChatRepository() {
        // Eksplicitna adresa baze (europe-west1) — bez ovoga se SDK povezuje na
        // pogrešan podrazumevani region jer google-services.json nema firebase_url.
        database = FirebaseDatabase.getInstance(Constants.RTDB_URL);
    }

    public static synchronized ChatRepository getInstance() {
        if (instance == null) {
            instance = new ChatRepository();
        }
        return instance;
    }

    /** Šalje poruku u čet regiona {@code regionKey}. */
    public void sendMessage(@NonNull String regionKey, @NonNull Message message,
                            @NonNull SimpleCallback cb) {
        DatabaseReference ref = chatRef(regionKey).push();
        String messageId = ref.getKey();
        if (messageId == null) {
            cb.onError("Slanje poruke nije uspelo.");
            return;
        }
        message.setId(messageId);
        ref.setValue(message)
                .addOnSuccessListener(v -> cb.onSuccess())
                .addOnFailureListener(e -> cb.onError("Slanje poruke nije uspelo."));
    }

    /**
     * Sluša sve poruke regiona {@code regionKey}, sortirane rastuće po vremenu.
     *
     * <p>Za svaku novoprispelu poruku koja nije poslata od trenutnog korisnika,
     * a {@code ChatActivity} nije u prvom planu, prikazuje se notifikacija van
     * aplikacije putem {@link NotificationPoster}. U produkciji bi ovaj put
     * zamenio server koji šalje FCM data poruku, koju hvata
     * {@code SlagalicaMessagingService} i prosleđuje istom {@code NotificationPoster}-u;
     * ovde se koristi direktan RTDB listener jer nema Cloud Function-a koji bi
     * okinuo push sa servera.</p>
     *
     * @param context   bilo koji Context — interno se koristi samo application context
     * @param regionKey ključ regiona (vidi {@link com.example.slagalica.util.RegionKey})
     * @param listener  slušalac koji prima ažuriranu, sortiranu listu poruka
     */
    public Runnable listenMessages(@NonNull Context context, @NonNull String regionKey,
                                   @NonNull MessagesListener listener) {
        Context appContext = context.getApplicationContext();
        DatabaseReference ref = chatRef(regionKey);
        ValueEventListener vel = new ValueEventListener() {
            private final Set<String> knownMessageIds = new HashSet<>();
            private boolean firstLoad = true;

            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Message> messages = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Message message = child.getValue(Message.class);
                    if (message != null) {
                        messages.add(message);
                    }
                }
                Collections.sort(messages, Comparator.comparingLong(Message::getTimestamp));

                if (firstLoad) {
                    for (Message message : messages) {
                        knownMessageIds.add(message.getId());
                    }
                    firstLoad = false;
                } else {
                    notifyAboutNewMessages(appContext, messages);
                }

                listener.onMessages(messages);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // prekid veze se tiho ignoriše — lista poruka ostaje kakva je bila
            }

            private void notifyAboutNewMessages(Context appContext, List<Message> messages) {
                String currentUid = FirebaseAuth.getInstance().getCurrentUser() != null
                        ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
                for (Message message : messages) {
                    if (knownMessageIds.contains(message.getId())) {
                        continue;
                    }
                    knownMessageIds.add(message.getId());
                    boolean fromSomeoneElse = currentUid == null
                            || !currentUid.equals(message.getSenderUid());
                    if (fromSomeoneElse && !chatActivityInForeground) {
                        NotificationPoster.post(appContext, NotificationType.CHAT,
                                appContext.getString(R.string.notification_title_chat),
                                message.getSenderName() + ": " + message.getText());
                    }
                }
            }
        };
        ref.addValueEventListener(vel);
        return () -> ref.removeEventListener(vel);
    }

    private DatabaseReference chatRef(String regionKey) {
        return database.getReference(Constants.RTDB_CHATS).child(regionKey);
    }
}

package com.example.slagalica;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.data.model.User;
import com.example.slagalica.data.repository.ChatRepository;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.util.RegionKey;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Drži regionalni čet-listener aktivnim dok god je aplikacija pokrenuta,
 * bez obzira koji je ekran trenutno u prvom planu.
 *
 * <p>{@code ChatRepository#listenMessages} pokreće sistemsku notifikaciju za
 * svaku novu poruku dok {@code ChatActivity} nije u prvom planu (spec. 8.e —
 * "Ukoliko igrač nije u aplikaciji, poslati notifikaciju o pristigloj poruci").
 * Ranije je taj listener živeo samo dok je {@code ChatActivity} bila na
 * steku, pa se gasio čim se korisnik vrati na glavni meni ili u igru — ovde
 * se umesto toga vezuje za ceo životni vek procesa aplikacije, počevši od
 * trenutka prijave.</p>
 *
 * <p>Napomena: ovo i dalje ne pokriva slučaj kada je aplikacija potpuno
 * ugašena (proces ubijen) — za to bi bio potreban server (Cloud Function)
 * koji šalje FCM data poruku, čega u ovom projektu nema.</p>
 */
public class SlagalicaApplication extends Application {

    private ChatRepository chatRepository;
    private UserRepository userRepository;

    @Nullable private Runnable stopChatListening;
    @Nullable private String listeningForRegion;

    @Override
    public void onCreate() {
        super.onCreate();

        chatRepository = ChatRepository.getInstance();
        userRepository = UserRepository.getInstance();

        FirebaseAuth.getInstance().addAuthStateListener(auth -> {
            if (auth.getCurrentUser() != null) {
                startChatNotifierFor(auth.getCurrentUser().getUid());
            } else {
                stopChatNotifier();
            }
        });
    }

    private void startChatNotifierFor(@NonNull String uid) {
        userRepository.getOrCreateUser(uid, new UserRepository.UserCallback() {
            @Override
            public void onSuccess(@NonNull User user) {
                String regionKey = RegionKey.toKey(user.getRegion());
                if (regionKey.equals(listeningForRegion)) {
                    return; // već slušamo isti region
                }
                stopChatNotifier();
                listeningForRegion = regionKey;
                stopChatListening = chatRepository.listenMessages(
                        SlagalicaApplication.this, regionKey, messages -> { /* samo za notifikacije */ });
            }

            @Override
            public void onError(@NonNull String message) {
                // Bez konekcije — pokušaće ponovo pri sledećoj promeni auth stanja.
            }
        });
    }

    private void stopChatNotifier() {
        if (stopChatListening != null) {
            stopChatListening.run();
            stopChatListening = null;
        }
        listeningForRegion = null;
    }
}

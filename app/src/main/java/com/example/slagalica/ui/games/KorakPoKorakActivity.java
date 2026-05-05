package com.example.slagalica.ui.games;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.data.model.Korak;
import com.example.slagalica.data.model.KorakState;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Aktivnost za igru "Korak po korak".
 * KT1: vizuelni demo — svaki 10s se otvara sledeći hint, bez prave validacije.
 * KT2: biće nadograđena Firebase integracijom i tačnom proverom odgovora.
 */
public class KorakPoKorakActivity extends AppCompatActivity {

    // KT1: fake hint-ovi na temu "JABUKA"
    private static final String[] FAKE_HINTS = {
            "voće",
            "crvena ili zelena",
            "Newton",
            "raste na drvetu",
            "Adam i Eva",
            "u tortama i pitama",
            "JA_UKA"
    };

    private RecyclerView rvKoraci;
    private TextInputLayout tlGuess;
    private TextInputEditText etGuess;
    private MaterialButton btnGuess;
    private MaterialButton btnGiveUp;

    private KorakAdapter adapter;

    /** Tajmer za otvaranje hint-ova — čuva se kao polje i cancels se u onDestroy(). */
    private CountDownTimer hintTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_korak_po_korak);

        initViews();
        setupRecyclerView();
        setupListeners();
        startHintTimer();
    }

    private void initViews() {
        rvKoraci = findViewById(R.id.rvKoraci);
        tlGuess  = findViewById(R.id.tlGuess);
        etGuess  = findViewById(R.id.etGuess);
        btnGuess  = findViewById(R.id.btnGuess);
        btnGiveUp = findViewById(R.id.btnGiveUp);
    }

    private void setupRecyclerView() {
        List<Korak> koraci = buildFakeKoraci();
        adapter = new KorakAdapter(koraci);
        rvKoraci.setLayoutManager(new LinearLayoutManager(this));
        rvKoraci.setAdapter(adapter);
        // Otvori prvi hint odmah — preostalih 6 otvara tajmer (svakih 10s)
        adapter.otvoriSledeci();
    }

    /** Gradi listu od 7 zaključanih koraka s fake hint-ovima. */
    private List<Korak> buildFakeKoraci() {
        List<Korak> list = new ArrayList<>();
        for (int i = 0; i < FAKE_HINTS.length; i++) {
            list.add(new Korak(i + 1, FAKE_HINTS[i], KorakState.ZAKLJUCAN));
        }
        return list;
    }

    private void setupListeners() {
        btnGuess.setOnClickListener(v ->
                Toast.makeText(this, "(KT1) Provera odgovora", Toast.LENGTH_SHORT).show());

        btnGiveUp.setOnClickListener(v -> finish());
    }

    /**
     * Pokreće CountDownTimer od 70s koji svaki 10s otvara sledeći hint.
     * Na isteku prikazuje Toast poruku.
     */
    private void startHintTimer() {
        hintTimer = new CountDownTimer(70_000, 10_000) {

            @Override
            public void onTick(long millisUntilFinished) {
                adapter.otvoriSledeci();
            }

            @Override
            public void onFinish() {
                Toast.makeText(KorakPoKorakActivity.this,
                        getString(R.string.korak_time_up),
                        Toast.LENGTH_SHORT).show();
            }
        }.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (hintTimer != null) {
            hintTimer.cancel();
        }
    }
}


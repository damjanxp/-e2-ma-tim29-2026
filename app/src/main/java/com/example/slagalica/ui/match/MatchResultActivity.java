package com.example.slagalica.ui.match;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.ui.main.MainActivity;
import com.example.slagalica.util.Constants;
import com.google.android.material.button.MaterialButton;

import android.widget.TextView;

/**
 * Ekran sa rezultatom završenog meča (KT2).
 *
 * <p>Prikazuje bodove po igrama ("Ko zna zna" i "Spojnice") i ukupan rezultat,
 * te ishod iz ugla prijavljenog igrača. Beleži odigranu partiju (i pobedu) u
 * statistiku korisnika kroz {@link UserRepository#recordMatchResult}.</p>
 *
 * <p>Napomena: dodela/oduzimanje zvezda, tokeni i liga su deo funkcionalnosti
 * "3. Igranje partija" i "6. Lige", koje po raspodeli rade druge kolege; ovde
 * se beleži samo ono što ulazi u statistiku profila (Student 2).</p>
 */
public class MatchResultActivity extends AppCompatActivity {

    public static final String EXTRA_MY_KZZ       = "extra_my_kzz";
    public static final String EXTRA_OPP_KZZ      = "extra_opp_kzz";
    public static final String EXTRA_MY_SPOJNICE  = "extra_my_spojnice";
    public static final String EXTRA_OPP_SPOJNICE = "extra_opp_spojnice";

    private final UserRepository userRepository = UserRepository.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_match_result);

        int myKzz       = getIntent().getIntExtra(EXTRA_MY_KZZ, 0);
        int oppKzz      = getIntent().getIntExtra(EXTRA_OPP_KZZ, 0);
        int mySpojnice  = getIntent().getIntExtra(EXTRA_MY_SPOJNICE, 0);
        int oppSpojnice = getIntent().getIntExtra(EXTRA_OPP_SPOJNICE, 0);
        String opponentName = getIntent().getStringExtra(Constants.EXTRA_OPPONENT_NAME);

        int myTotal  = myKzz + mySpojnice;
        int oppTotal = oppKzz + oppSpojnice;

        bindResult(myKzz, oppKzz, mySpojnice, oppSpojnice, myTotal, oppTotal, opponentName);
        recordStatistics(myTotal, oppTotal);
        setupNavigation();
    }

    private void bindResult(int myKzz, int oppKzz, int mySpojnice, int oppSpojnice,
                            int myTotal, int oppTotal, String opponentName) {
        TextView tvOutcome = findViewById(R.id.tvOutcome);
        TextView tvPlayers = findViewById(R.id.tvPlayers);
        TextView tvKzzScore = findViewById(R.id.tvKzzScore);
        TextView tvSpojniceScore = findViewById(R.id.tvSpojniceScore);
        TextView tvTotalScore = findViewById(R.id.tvTotalScore);

        if (myTotal > oppTotal) {
            tvOutcome.setText(R.string.result_win);
        } else if (myTotal < oppTotal) {
            tvOutcome.setText(R.string.result_lose);
        } else {
            tvOutcome.setText(R.string.result_draw);
        }

        tvPlayers.setText(getString(R.string.result_players, getString(R.string.result_you),
                opponentName != null ? opponentName : "?"));

        tvKzzScore.setText(getString(R.string.result_score_row, myKzz, oppKzz));
        tvSpojniceScore.setText(getString(R.string.result_score_row, mySpojnice, oppSpojnice));
        tvTotalScore.setText(getString(R.string.result_score_row, myTotal, oppTotal));
    }

    private void recordStatistics(int myTotal, int oppTotal) {
        String uid = userRepository.getCurrentUid();
        if (uid != null) {
            userRepository.recordMatchResult(uid, myTotal > oppTotal);
        }
    }

    private void setupNavigation() {
        MaterialButton btnHome = findViewById(R.id.btnHome);
        btnHome.setOnClickListener(v -> goHome());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                goHome();
            }
        });
    }

    private void goHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

package com.example.slagalica.ui.games;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.google.android.material.button.MaterialButton;

public class SkockoActivity extends AppCompatActivity {

    private static final int TURN_SECONDS = 30;
    private static final int TURN_TIME_MS = TURN_SECONDS * 1000;

    private ProgressBar pbTimer;
    private TextView tvTimer;
    private CountDownTimer turnTimer;

    private final MaterialButton[] symbolButtons = new MaterialButton[5];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_skocko);
        initViews();
        setupListeners();
        startTurnTimer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (turnTimer != null) {
            turnTimer.cancel();
        }
    }

    private void initViews() {
        pbTimer = findViewById(R.id.pbTimer);
        tvTimer = findViewById(R.id.tvTimer);

        symbolButtons[0] = findViewById(R.id.btnSymbolSmiley);
        symbolButtons[1] = findViewById(R.id.btnSymbolSquare);
        symbolButtons[2] = findViewById(R.id.btnSymbolCircle);
        symbolButtons[3] = findViewById(R.id.btnSymbolHeart);
        symbolButtons[4] = findViewById(R.id.btnSymbolTriangle);
    }

    private void setupListeners() {
        for (MaterialButton btn : symbolButtons) {
            btn.setOnClickListener(v -> {
                // No-op for now; input handling will be added later.
            });
        }
        findViewById(R.id.btnGiveUp).setOnClickListener(v -> finish());
    }

    private void startTurnTimer() {
        pbTimer.setMax(TURN_SECONDS);
        pbTimer.setProgress(TURN_SECONDS);
        tvTimer.setText(getString(R.string.skocko_time_left, TURN_SECONDS));

        turnTimer = new CountDownTimer(TURN_TIME_MS, 1_000) {
            @Override
            public void onTick(long ms) {
                int s = (int) (ms / 1000) + 1;
                pbTimer.setProgress(s);
                tvTimer.setText(getString(R.string.skocko_time_left, s));
            }

            @Override
            public void onFinish() {
                pbTimer.setProgress(0);
                tvTimer.setText(getString(R.string.skocko_time_left, 0));
            }
        }.start();
    }
}


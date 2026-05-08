package com.example.slagalica.ui.games;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.google.android.material.button.MaterialButton;

public class AsocijacijeActivity extends AppCompatActivity {

    private static final int TURN_SECONDS = 120;
    private static final int TURN_TIME_MS = TURN_SECONDS * 1000;

    private ProgressBar pbTimer;
    private TextView tvTimer;
    private CountDownTimer turnTimer;

    private final MaterialButton[] buttons = new MaterialButton[21];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_asocijacije);
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

        buttons[0] = findViewById(R.id.btnA1);
        buttons[1] = findViewById(R.id.btnB1);
        buttons[2] = findViewById(R.id.btnA2);
        buttons[3] = findViewById(R.id.btnB2);
        buttons[4] = findViewById(R.id.btnA3);
        buttons[5] = findViewById(R.id.btnB3);
        buttons[6] = findViewById(R.id.btnA4);
        buttons[7] = findViewById(R.id.btnB4);
        buttons[8] = findViewById(R.id.btnA);
        buttons[9] = findViewById(R.id.btnB);
        buttons[10] = findViewById(R.id.btnCenter);
        buttons[11] = findViewById(R.id.btnC);
        buttons[12] = findViewById(R.id.btnD);
        buttons[13] = findViewById(R.id.btnC4);
        buttons[14] = findViewById(R.id.btnD4);
        buttons[15] = findViewById(R.id.btnC3);
        buttons[16] = findViewById(R.id.btnD3);
        buttons[17] = findViewById(R.id.btnC2);
        buttons[18] = findViewById(R.id.btnD2);
        buttons[19] = findViewById(R.id.btnC1);
        buttons[20] = findViewById(R.id.btnD1);
    }

    private void setupListeners() {
        for (MaterialButton button : buttons) {
            if (button == null) continue;
            button.setOnClickListener(v -> {
                // No-op for now; interactions will be added later.
            });
        }
        findViewById(R.id.btnGiveUp).setOnClickListener(v -> finish());
    }

    private void startTurnTimer() {
        pbTimer.setMax(TURN_SECONDS);
        pbTimer.setProgress(TURN_SECONDS);
        tvTimer.setText(getString(R.string.asoc_time_left, TURN_SECONDS));

        turnTimer = new CountDownTimer(TURN_TIME_MS, 1_000) {
            @Override
            public void onTick(long ms) {
                int s = (int) (ms / 1000) + 1;
                pbTimer.setProgress(s);
                tvTimer.setText(getString(R.string.asoc_time_left, s));
            }

            @Override
            public void onFinish() {
                pbTimer.setProgress(0);
                tvTimer.setText(getString(R.string.asoc_time_left, 0));
            }
        }.start();
    }
}

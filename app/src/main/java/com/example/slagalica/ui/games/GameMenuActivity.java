package com.example.slagalica.ui.games;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.google.android.material.appbar.MaterialToolbar;

public class GameMenuActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_menu);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        findViewById(R.id.btnKoZnaZna).setOnClickListener(v ->
                startActivity(new Intent(this, KoZnaZnaActivity.class)));

        findViewById(R.id.btnSpojnice).setOnClickListener(v ->
                startActivity(new Intent(this, SpojniceActivity.class)));

        findViewById(R.id.btnKorakPoKorak).setOnClickListener(v ->
                startActivity(new Intent(this, KorakPoKorakActivity.class)));

        findViewById(R.id.btnMojBroj).setOnClickListener(v ->
                startActivity(new Intent(this, MojBrojActivity.class)));
    }
}

package com.example.slagalica.ui.games;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.example.slagalica.ui.match.MatchmakingActivity;
import com.google.android.material.appbar.MaterialToolbar;

public class GameMenuActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_menu);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Sve 1v1 igre idu kroz matchmaking koji uparuje igrače
        // pa pokreće celi lanac: KoZnaZna → Spojnice → MojBroj → KorakPoKorak
        findViewById(R.id.btnKoZnaZna).setOnClickListener(v ->
                startActivity(new Intent(this, MatchmakingActivity.class)));

        findViewById(R.id.btnSpojnice).setOnClickListener(v ->
                startActivity(new Intent(this, MatchmakingActivity.class)));

        findViewById(R.id.btnKorakPoKorak).setOnClickListener(v ->
                startActivity(new Intent(this, MatchmakingActivity.class)));

        findViewById(R.id.btnMojBroj).setOnClickListener(v ->
                startActivity(new Intent(this, MatchmakingActivity.class)));

        findViewById(R.id.btnSkocko).setOnClickListener(v ->
                startActivity(new Intent(this, SkockoActivity.class)));

        findViewById(R.id.btnAsocijacije).setOnClickListener(v ->
                startActivity(new Intent(this, AsocijacijeActivity.class)));
    }
}

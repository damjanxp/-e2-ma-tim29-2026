package com.example.slagalica.ui.ranking;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.data.model.LeaderboardCycle;
import com.example.slagalica.data.model.User;
import com.example.slagalica.data.repository.LeaderboardRepository;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.util.Constants;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Ekran rang liste — nedeljni i mesečni ciklus, sortirano po zvezdama.
 *
 * <p>Ciklusi se ne zaključuju automatski (nema Cloud Functions na besplatnom
 * planu); umesto toga, dok je ekran otvoren, rang lista se sama osvežava na
 * svaka 2 minuta ({@link Constants#LEADERBOARD_REFRESH_INTERVAL_MS}), a
 * zaključivanje ciklusa i dodavanje zvezda pokreću se ručno test-dugmadima
 * na dnu ekrana.</p>
 */
public class RankingActivity extends AppCompatActivity {

    private final LeaderboardRepository leaderboardRepository = LeaderboardRepository.getInstance();
    private final UserRepository userRepository = UserRepository.getInstance();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());

    private TabLayout tabLayout;
    private TextView tvPeriod;
    private RecyclerView rvRanking;
    private TextView tvEmpty;
    private View pbLoading;
    private MaterialButton btnAddDebugStar;
    private MaterialButton btnConcludeCycle;

    private RankingAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoRefresh = this::autoRefreshTick;

    private String currentType = Constants.LEADERBOARD_TYPE_WEEKLY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ranking);

        initViews();
        setupToolbar();
        setupRecycler();
        setupTabs();
        setupDebugButtons();
        loadCycleAndRanking();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.postDelayed(autoRefresh, Constants.LEADERBOARD_REFRESH_INTERVAL_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(autoRefresh);
    }

    private void initViews() {
        tabLayout        = findViewById(R.id.tabLayoutRanking);
        tvPeriod         = findViewById(R.id.tvRankingPeriod);
        rvRanking        = findViewById(R.id.rvRanking);
        tvEmpty          = findViewById(R.id.tvRankingEmpty);
        pbLoading        = findViewById(R.id.pbRankingLoading);
        btnAddDebugStar  = findViewById(R.id.btnAddDebugStar);
        btnConcludeCycle = findViewById(R.id.btnConcludeCycle);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.ranking_title);
        }
    }

    private void setupRecycler() {
        adapter = new RankingAdapter();
        rvRanking.setLayoutManager(new LinearLayoutManager(this));
        rvRanking.setAdapter(adapter);
    }

    private void setupTabs() {
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(@NonNull TabLayout.Tab tab) {
                currentType = tab.getPosition() == 1
                        ? Constants.LEADERBOARD_TYPE_MONTHLY
                        : Constants.LEADERBOARD_TYPE_WEEKLY;
                loadCycleAndRanking();
            }

            @Override public void onTabUnselected(@NonNull TabLayout.Tab tab) { }
            @Override public void onTabReselected(@NonNull TabLayout.Tab tab) { }
        });
    }

    private void setupDebugButtons() {
        btnAddDebugStar.setOnClickListener(v -> onAddDebugStarClicked());
        btnConcludeCycle.setOnClickListener(v -> confirmConcludeCycle());
    }

    // =========================================================================
    // Učitavanje
    // =========================================================================

    private void loadCycleAndRanking() {
        showLoading(true);
        leaderboardRepository.getOrInitCycle(currentType, new LeaderboardRepository.CycleCallback() {
            @Override
            public void onSuccess(@NonNull LeaderboardCycle cycle) {
                if (isFinishing() || isDestroyed()) return;
                tvPeriod.setText(getString(R.string.ranking_period,
                        dateFormat.format(new Date(cycle.getCycleStart())),
                        dateFormat.format(new Date(cycle.getCycleEnd()))));
                loadRanking();
            }

            @Override
            public void onError(@NonNull String message) {
                if (isFinishing() || isDestroyed()) return;
                showLoading(false);
                Toast.makeText(RankingActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadRanking() {
        final String requestedType = currentType;
        leaderboardRepository.getTopPlayers(currentType, new LeaderboardRepository.RankingCallback() {
            @Override
            public void onSuccess(@NonNull List<User> rankedUsers) {
                if (isFinishing() || isDestroyed() || !requestedType.equals(currentType)) return;
                showLoading(false);
                adapter.setItems(rankedUsers, requestedType);
                updateEmptyState(rankedUsers.isEmpty());
            }

            @Override
            public void onError(@NonNull String message) {
                if (isFinishing() || isDestroyed()) return;
                showLoading(false);
                Toast.makeText(RankingActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Automatsko osvežavanje na svaka 2 minuta dok je ekran otvoren. */
    private void autoRefreshTick() {
        loadCycleAndRanking();
        handler.postDelayed(autoRefresh, Constants.LEADERBOARD_REFRESH_INTERVAL_MS);
    }

    // =========================================================================
    // Debug dugmad
    // =========================================================================

    private void onAddDebugStarClicked() {
        String uid = userRepository.getCurrentUid();
        if (uid == null) return;
        btnAddDebugStar.setEnabled(false);
        leaderboardRepository.addDebugStar(uid, new LeaderboardRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                btnAddDebugStar.setEnabled(true);
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(RankingActivity.this, R.string.ranking_star_added, Toast.LENGTH_SHORT).show();
                loadRanking();
            }

            @Override
            public void onError(@NonNull String message) {
                btnAddDebugStar.setEnabled(true);
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(RankingActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmConcludeCycle() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.ranking_conclude_confirm_title)
                .setMessage(R.string.ranking_conclude_confirm_message)
                .setPositiveButton(R.string.ranking_conclude_confirm_yes, (d, w) -> onConcludeCycleClicked())
                .setNegativeButton(R.string.ranking_conclude_confirm_no, null)
                .show();
    }

    private void onConcludeCycleClicked() {
        btnConcludeCycle.setEnabled(false);
        leaderboardRepository.concludeCycle(currentType, new LeaderboardRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                btnConcludeCycle.setEnabled(true);
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(RankingActivity.this, R.string.ranking_cycle_concluded, Toast.LENGTH_SHORT).show();
                loadCycleAndRanking();
            }

            @Override
            public void onError(@NonNull String message) {
                btnConcludeCycle.setEnabled(true);
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(RankingActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // =========================================================================
    // UI helpers
    // =========================================================================

    private void showLoading(boolean loading) {
        pbLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void updateEmptyState(boolean isEmpty) {
        tvEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        rvRanking.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}

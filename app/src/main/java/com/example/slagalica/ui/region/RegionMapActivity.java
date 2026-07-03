package com.example.slagalica.ui.region;

import android.graphics.PointF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.example.slagalica.data.model.RegionCycleResult;
import com.example.slagalica.data.model.User;
import com.example.slagalica.data.repository.RegionRepository;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.ui.widget.RegionMapView;
import com.example.slagalica.util.AvatarProvider;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Ekran "5. Prikaz regiona" — šematska mapa Srbije podeljena na 7 regiona
 * (vidi {@link com.example.slagalica.ui.widget.RegionMapView}), sa nasumičnim
 * tačkama registrovanih igrača po regionu, statistikom regiona (klik na
 * region) i mesečnom rang listom po regionima.
 */
public class RegionMapActivity extends AppCompatActivity {

    private final UserRepository userRepository = UserRepository.getInstance();
    private final RegionRepository regionRepository = RegionRepository.getInstance();

    /** Šematske granice svakog regiona na mapi (relativne 0..1 koordinate). */
    private static final Map<String, float[]> ZONE_BOUNDS = new HashMap<>();
    static {
        ZONE_BOUNDS.put("Vojvodina",          new float[]{0.05f, 0.00f, 0.95f, 0.22f});
        ZONE_BOUNDS.put("Beograd",            new float[]{0.30f, 0.24f, 0.70f, 0.38f});
        ZONE_BOUNDS.put("Zapadna Srbija",     new float[]{0.03f, 0.40f, 0.35f, 0.62f});
        ZONE_BOUNDS.put("Šumadija",           new float[]{0.37f, 0.40f, 0.63f, 0.62f});
        ZONE_BOUNDS.put("Istočna Srbija",     new float[]{0.65f, 0.40f, 0.97f, 0.62f});
        ZONE_BOUNDS.put("Južna Srbija",       new float[]{0.15f, 0.64f, 0.85f, 0.80f});
        ZONE_BOUNDS.put("Kosovo i Metohija",  new float[]{0.20f, 0.82f, 0.80f, 0.98f});
    }

    /** Proizvoljna ikonica po regionu (spec 5c). */
    private static final Map<String, Integer> ZONE_ICONS = new HashMap<>();
    static {
        ZONE_ICONS.put("Beograd", R.drawable.ic_symbol_star);
        ZONE_ICONS.put("Vojvodina", R.drawable.ic_symbol_circle);
        ZONE_ICONS.put("Šumadija", R.drawable.ic_symbol_triangle);
        ZONE_ICONS.put("Zapadna Srbija", R.drawable.ic_symbol_square);
        ZONE_ICONS.put("Istočna Srbija", R.drawable.ic_symbol_heart);
        ZONE_ICONS.put("Južna Srbija", R.drawable.ic_symbol_smiley);
        ZONE_ICONS.put("Kosovo i Metohija", R.drawable.ic_league);
    }

    /** Najviše markera koje iscrtavamo po regionu (čitljivost mape). */
    private static final int MAX_MARKERS_PER_ZONE = 40;

    private RegionMapView regionMapView;
    private View pbLoading;

    @Nullable private String myUid;
    @Nullable private String myRegion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_region_map);

        regionMapView = findViewById(R.id.regionMapView);
        pbLoading = findViewById(R.id.pbRegionLoading);

        setupToolbar();
        setupListeners();
        loadMap();
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void setupListeners() {
        regionMapView.setOnZoneClickListener(this::showRegionStatsDialog);
        MaterialButton btnLeaderboard = findViewById(R.id.btnRegionLeaderboard);
        btnLeaderboard.setOnClickListener(v -> showRegionLeaderboardDialog());
    }

    // =========================================================================
    // Učitavanje mape
    // =========================================================================

    private void loadMap() {
        pbLoading.setVisibility(View.VISIBLE);
        userRepository.ensureSignedIn(new UserRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                myUid = userRepository.getCurrentUid();
                if (myUid == null) {
                    loadZonesWithoutUser();
                    return;
                }
                userRepository.getOrCreateUser(myUid, new UserRepository.UserCallback() {
                    @Override
                    public void onSuccess(User user) {
                        myRegion = user.getRegion();
                        loadZones();
                    }

                    @Override
                    public void onError(String message) {
                        loadZones();
                    }
                });
            }

            @Override
            public void onError(String message) {
                loadZonesWithoutUser();
            }
        });
    }

    private void loadZonesWithoutUser() {
        loadZones();
    }

    /** Učitava igrače svakog regiona + rezultat prošlog mesečnog ciklusa, pa gradi zone mape. */
    private void loadZones() {
        String[] regionNames = getResources().getStringArray(R.array.regions_array);
        Map<String, List<User>> playersByRegion = new HashMap<>();
        int[] pending = {regionNames.length + 1}; // +1 za rezultat ciklusa

        for (String regionName : regionNames) {
            regionRepository.getPlayersInRegion(regionName, new RegionRepository.RegionPlayersCallback() {
                @Override
                public void onSuccess(@NonNull List<User> players) {
                    playersByRegion.put(regionName, players);
                    onOnePartLoaded(regionNames, playersByRegion, null, pending);
                }

                @Override
                public void onError(@NonNull String message) {
                    playersByRegion.put(regionName, new ArrayList<>());
                    onOnePartLoaded(regionNames, playersByRegion, null, pending);
                }
            });
        }

        regionRepository.getLastCycleResult(new RegionRepository.RegionCycleResultCallback() {
            @Override
            public void onSuccess(@Nullable RegionCycleResult result) {
                onOnePartLoaded(regionNames, playersByRegion, result, pending);
            }

            @Override
            public void onError(@NonNull String message) {
                onOnePartLoaded(regionNames, playersByRegion, null, pending);
            }
        });
    }

    /** Sinhronizuje asinhrone pozive; kad je sve stiglo, gradi zone i prikazuje mapu. */
    private final RegionCycleResultHolder cycleResultHolder = new RegionCycleResultHolder();

    private static final class RegionCycleResultHolder {
        @Nullable RegionCycleResult value;
    }

    private void onOnePartLoaded(String[] regionNames, Map<String, List<User>> playersByRegion,
                                  @Nullable RegionCycleResult cycleResult, int[] pending) {
        if (cycleResult != null) {
            cycleResultHolder.value = cycleResult;
        }
        pending[0]--;
        if (pending[0] > 0) {
            return;
        }
        if (isFinishing() || isDestroyed()) {
            return;
        }
        buildAndShowZones(regionNames, playersByRegion, cycleResultHolder.value);
    }

    private void buildAndShowZones(String[] regionNames, Map<String, List<User>> playersByRegion,
                                   @Nullable RegionCycleResult cycleResult) {
        List<RegionMapView.Zone> zones = new ArrayList<>();
        for (String regionName : regionNames) {
            float[] bounds = ZONE_BOUNDS.get(regionName);
            if (bounds == null) {
                continue; // nepoznat region (izmenjen regions_array) — preskoči na mapi
            }
            List<User> players = playersByRegion.getOrDefault(regionName, new ArrayList<>());
            List<PointF> markers = randomMarkersFor(players);
            int frameType = frameTypeFor(regionName, cycleResult);

            zones.add(new RegionMapView.Zone(
                    regionName, bounds[0], bounds[1], bounds[2], bounds[3],
                    players.size(), frameType, markers));
        }
        regionMapView.setZones(zones);
        pbLoading.setVisibility(View.GONE);
    }

    /** Deterministički nasumične pozicije igrača unutar regiona (seed = hash uid-a). */
    private List<PointF> randomMarkersFor(List<User> players) {
        List<PointF> markers = new ArrayList<>();
        int limit = Math.min(players.size(), MAX_MARKERS_PER_ZONE);
        for (int i = 0; i < limit; i++) {
            User u = players.get(i);
            String seedKey = u.getUid() != null ? u.getUid() : String.valueOf(i);
            Random random = new Random(seedKey.hashCode());
            float x = 0.12f + random.nextFloat() * 0.76f;
            float y = 0.12f + random.nextFloat() * 0.76f;
            markers.add(new PointF(x, y));
        }
        return markers;
    }

    private int frameTypeFor(String regionName, @Nullable RegionCycleResult cycleResult) {
        if (cycleResult == null) return 0;
        if (regionName.equals(cycleResult.getFirstRegion())) return 1;
        if (regionName.equals(cycleResult.getSecondRegion())) return 2;
        if (regionName.equals(cycleResult.getThirdRegion())) return 3;
        return 0;
    }

    // =========================================================================
    // Dijalog: statistika regiona
    // =========================================================================

    private void showRegionStatsDialog(@NonNull String regionName) {
        regionRepository.getRegionStats(regionName, new RegionRepository.RegionStatsCallback() {
            @Override
            public void onSuccess(@NonNull RegionRepository.RegionStats stats) {
                if (isFinishing() || isDestroyed()) return;
                String message = getString(R.string.region_stats_message,
                        stats.getFirstPlaceCount(), stats.getSecondPlaceCount(), stats.getThirdPlaceCount(),
                        stats.getActivePlayers(), stats.getTotalRegistered());

                Integer icon = ZONE_ICONS.get(regionName);
                AlertDialog.Builder builder = new AlertDialog.Builder(RegionMapActivity.this)
                        .setTitle(regionName)
                        .setMessage(message)
                        .setPositiveButton(R.string.dialog_ok_got_it, null)
                        .setNeutralButton(R.string.region_btn_leaderboard,
                                (d, w) -> showRegionLeaderboardDialog());
                if (icon != null) {
                    builder.setIcon(icon);
                }
                builder.show();
            }

            @Override
            public void onError(@NonNull String message) {
                Toast.makeText(RegionMapActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // =========================================================================
    // Dijalog: mesečna rang lista po regionima
    // =========================================================================

    private void showRegionLeaderboardDialog() {
        regionRepository.getMonthlyRegionLeaderboard(myUid, new RegionRepository.RegionLeaderboardCallback() {
            @Override
            public void onSuccess(@NonNull List<RegionRepository.RegionRanking> ranking,
                                  @Nullable String detectedMyRegion) {
                if (isFinishing() || isDestroyed()) return;
                if (detectedMyRegion != null) {
                    myRegion = detectedMyRegion;
                }
                renderRegionLeaderboardDialog(ranking);
            }

            @Override
            public void onError(@NonNull String message) {
                Toast.makeText(RegionMapActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderRegionLeaderboardDialog(List<RegionRepository.RegionRanking> ranking) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_region_leaderboard, null);
        LinearLayout container = dialogView.findViewById(R.id.containerRegionRows);

        if (ranking.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.region_leaderboard_empty);
            container.addView(empty);
        }

        for (int i = 0; i < ranking.size(); i++) {
            RegionRepository.RegionRanking row = ranking.get(i);
            View rowView = LayoutInflater.from(this).inflate(R.layout.item_region_ranking, container, false);

            TextView tvPosition = rowView.findViewById(R.id.tvRegionRankPosition);
            ImageView ivIcon = rowView.findViewById(R.id.ivRegionRankIcon);
            TextView tvName = rowView.findViewById(R.id.tvRegionRankName);
            TextView tvStars = rowView.findViewById(R.id.tvRegionRankStars);

            tvPosition.setText(getString(R.string.region_rank_position, i + 1));
            Integer icon = ZONE_ICONS.get(row.getRegionName());
            if (icon != null) {
                ivIcon.setImageResource(icon);
            } else {
                ivIcon.setImageResource(AvatarProvider.getDrawableRes(0));
            }
            tvName.setText(getString(R.string.region_rank_name_format,
                    row.getRegionName(), row.getPlayerCount()));
            tvStars.setText(getString(R.string.region_rank_stars_format, row.getTotalStars()));

            boolean isMine = row.getRegionName().equals(myRegion);
            if (isMine) {
                tvName.setTypeface(tvName.getTypeface(), Typeface.BOLD);
                rowView.setBackgroundColor(getColor(R.color.notification_unread_bg));
            }

            container.addView(rowView);
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.region_leaderboard_title)
                .setView(dialogView)
                .setPositiveButton(R.string.dialog_ok_got_it, null)
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

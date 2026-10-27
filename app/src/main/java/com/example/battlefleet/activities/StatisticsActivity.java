package com.example.battlefleet.activities;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.battlefleet.R;
import com.example.battlefleet.database.MatchHistoryRepository;
import com.example.battlefleet.models.PlayerStats;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.List;
import java.util.Locale;


public class StatisticsActivity extends AppCompatActivity {

    private TextView tvWinRate;
    private ProgressBar pbWinRate;
    private TextView tvBattlesSummary;
    private TextView tvTotalMatches;
    private TextView tvOverallAccuracy;
    private TextView tvTotalShots;
    private TextView tvTotalHits;
    private LinearLayout layoutRecentForm;
    private TextView tvFormEmpty;

    private MatchHistoryRepository historyRepo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

        historyRepo = MatchHistoryRepository.getInstance(this);

        initViews();
        loadStatistics();
    }

    private void initViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar_stats);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvWinRate = findViewById(R.id.tv_stats_win_rate);
        pbWinRate = findViewById(R.id.pb_win_rate);
        tvBattlesSummary = findViewById(R.id.tv_stats_battles_summary);
        tvTotalMatches = findViewById(R.id.tv_stats_total_matches);
        tvOverallAccuracy = findViewById(R.id.tv_stats_overall_accuracy);
        tvTotalShots = findViewById(R.id.tv_stats_total_shots);
        tvTotalHits = findViewById(R.id.tv_stats_total_hits);
        layoutRecentForm = findViewById(R.id.layout_recent_form);
        tvFormEmpty = findViewById(R.id.tv_form_empty);
    }

    private void loadStatistics() {
        historyRepo.getStatistics(new MatchHistoryRepository.Callback<PlayerStats>() {
            @Override
            public void onComplete(PlayerStats stats) {
                if (stats == null || stats.getTotalMatches() == 0) {
                    displayEmpty();
                    return;
                }

                tvWinRate.setText(String.format(Locale.getDefault(), "%.1f%%", stats.getWinRate()));
                pbWinRate.setProgress((int) stats.getWinRate());

                tvBattlesSummary.setText(String.format(Locale.getDefault(),
                        "%d Engagements • %d Victories • %d Defeats",
                        stats.getTotalMatches(), stats.getWins(), stats.getLosses()));

                tvTotalMatches.setText(String.valueOf(stats.getTotalMatches()));
                tvOverallAccuracy.setText(String.format(Locale.getDefault(), "%.1f%%", stats.getOverallAccuracy()));
                tvTotalShots.setText(String.valueOf(stats.getTotalShots()));
                tvTotalHits.setText(String.valueOf(stats.getTotalHits()));

                populateRecentForm(stats.getRecentResults());
            }

            @Override
            public void onError(Exception e) {
                displayEmpty();
            }
        });
    }

    private void displayEmpty() {
        tvWinRate.setText("0.0%");
        pbWinRate.setProgress(0);
        tvBattlesSummary.setText("0 Engagements • 0 Victories • 0 Defeats");
        tvTotalMatches.setText("0");
        tvOverallAccuracy.setText("0.0%");
        tvTotalShots.setText("0");
        tvTotalHits.setText("0");
        tvFormEmpty.setVisibility(View.VISIBLE);
    }

    private void populateRecentForm(List<String> results) {
        if (results == null || results.isEmpty()) {
            tvFormEmpty.setVisibility(View.VISIBLE);
            return;
        }

        tvFormEmpty.setVisibility(View.GONE);
        layoutRecentForm.removeAllViews();

        int size = (int) (36 * getResources().getDisplayMetrics().density);
        int margin = (int) (6 * getResources().getDisplayMetrics().density);

        for (String res : results) {
            boolean isWin = "WIN".equalsIgnoreCase(res);
            TextView pill = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(0, 0, margin, 0);
            pill.setLayoutParams(lp);
            pill.setGravity(Gravity.CENTER);
            pill.setText(isWin ? "W" : "L");
            pill.setTextSize(14);
            pill.setTextColor(ContextCompat.getColor(this, isWin ? R.color.ship_deployed : R.color.hit_red));
            pill.setBackgroundResource(isWin ? R.drawable.badge_turn_your : R.drawable.badge_turn_opponent);

            layoutRecentForm.addView(pill);
        }
    }
}

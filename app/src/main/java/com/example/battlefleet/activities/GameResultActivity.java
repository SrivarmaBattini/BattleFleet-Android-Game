package com.example.battlefleet.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.battlefleet.MainActivity;
import com.example.battlefleet.R;
import com.example.battlefleet.database.MatchHistoryRepository;
import com.example.battlefleet.models.MatchRecord;
import com.example.battlefleet.utils.NotificationUtils;
import com.example.battlefleet.utils.PreferencesManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class GameResultActivity extends AppCompatActivity {
    public static final String EXTRA_IS_WINNER = "extra_is_winner";
    public static final String EXTRA_WINNER_NAME = "extra_winner_name";
    public static final String EXTRA_LOSER_NAME = "extra_loser_name";
    public static final String EXTRA_OPPONENT_NAME = "extra_opponent_name";
    public static final String EXTRA_DURATION_SECONDS = "extra_duration_seconds";
    public static final String EXTRA_SHOTS_FIRED = "extra_shots_fired";
    public static final String EXTRA_HITS = "extra_hits";
    public static final String EXTRA_ACCURACY = "extra_accuracy";
    public static final String EXTRA_ROOM_CODE = "extra_room_code";

    private boolean isWinner;
    private String opponentName;
    private int durationSeconds;
    private int shotsFired;
    private int hits;
    private double accuracy;
    private String roomCode;

    private MatchHistoryRepository historyRepo;
    private PreferencesManager prefsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_result);

        historyRepo = MatchHistoryRepository.getInstance(this);
        prefsManager = new PreferencesManager(this);

        extractIntentData();
        initViews();
        saveMatchToDatabase();
        triggerNotification();
    }

    private void extractIntentData() {
        Intent intent = getIntent();
        isWinner = intent.getBooleanExtra(EXTRA_IS_WINNER, false);
        opponentName = intent.getStringExtra(EXTRA_OPPONENT_NAME);
        if (opponentName == null) opponentName = "Opponent";
        durationSeconds = intent.getIntExtra(EXTRA_DURATION_SECONDS, 0);
        shotsFired = intent.getIntExtra(EXTRA_SHOTS_FIRED, 0);
        hits = intent.getIntExtra(EXTRA_HITS, 0);
        accuracy = intent.getDoubleExtra(EXTRA_ACCURACY, 0.0);
        roomCode = intent.getStringExtra(EXTRA_ROOM_CODE);
    }

    private void initViews() {
        ImageView ivIcon = findViewById(R.id.iv_result_icon);
        TextView tvTitle = findViewById(R.id.tv_result_title);
        TextView tvSubtitle = findViewById(R.id.tv_result_subtitle);

        TextView tvOpponent = findViewById(R.id.tv_res_opponent);
        TextView tvDuration = findViewById(R.id.tv_res_duration);
        TextView tvShots = findViewById(R.id.tv_res_shots);
        TextView tvHits = findViewById(R.id.tv_res_hits);
        TextView tvAccuracy = findViewById(R.id.tv_res_accuracy);

        if (isWinner) {
            ivIcon.setImageResource(R.drawable.ic_trophy);
            ivIcon.setColorFilter(ContextCompat.getColor(this, R.color.gold_rank));
            tvTitle.setText(R.string.victory_title);
            tvTitle.setTextColor(ContextCompat.getColor(this, R.color.gold_rank));
            tvSubtitle.setText("Enemy fleet completely annihilated in combat!");
        } else {
            ivIcon.setImageResource(R.drawable.ic_explosion);
            ivIcon.setColorFilter(ContextCompat.getColor(this, R.color.hit_red));
            tvTitle.setText(R.string.defeat_title);
            tvTitle.setTextColor(ContextCompat.getColor(this, R.color.hit_red));
            tvSubtitle.setText("Allied fleet was destroyed by enemy torpedoes.");
        }

        tvOpponent.setText(opponentName);
        int mins = durationSeconds / 60;
        int secs = durationSeconds % 60;
        tvDuration.setText(String.format(Locale.getDefault(), "%02d:%02d", mins, secs));
        tvShots.setText(String.valueOf(shotsFired));
        tvHits.setText(String.format(Locale.getDefault(), "%d / 17", hits));
        tvAccuracy.setText(String.format(Locale.getDefault(), "%.1f%%", accuracy));

        // Share battle report (Implicit Intent)
        findViewById(R.id.btn_share_result).setOnClickListener(v -> shareBattleReport());

        // Play Again
        findViewById(R.id.btn_play_again).setOnClickListener(v -> {
            Intent intent = new Intent(GameResultActivity.this, LobbyActivity.class);
            intent.putExtra(LobbyActivity.EXTRA_MODE, LobbyActivity.MODE_HOST);
            startActivity(intent);
            finish();
        });

        // Return to HQ
        findViewById(R.id.btn_return_hq).setOnClickListener(v -> {
            Intent intent = new Intent(GameResultActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }

    /**
     * Demonstrates Unit 3 Syllabus: Implicit Intent using ACTION_SEND and Intent.createChooser.
     */
    private void shareBattleReport() {
        String outcome = isWinner ? "VICTORY" : "DEFEAT";
        String shareText = String.format(Locale.getDefault(),
                "⚓ BATTLEFLEET COMBAT REPORT ⚓\n" +
                        "Commander: %s\n" +
                        "Outcome: %s against %s\n" +
                        "Firing Accuracy: %.1f%% (%d hits / %d shots)\n" +
                        "Duration: %02d:%02d\n" +
                        "Platform: BattleFleet Android TCP Warfare",
                prefsManager.getPlayerName(),
                outcome,
                opponentName,
                accuracy,
                hits,
                shotsFired,
                durationSeconds / 60,
                durationSeconds % 60);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "BattleFleet Match Report - " + outcome);
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);

        startActivity(Intent.createChooser(shareIntent, "Share Battle Report Via"));
    }

    /**
     * Saves authoritative completed match record into local SQLite database.
     */
    private void saveMatchToDatabase() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        String timestamp = sdf.format(new Date());

        MatchRecord record = new MatchRecord();
        record.setRoomCode(roomCode != null ? roomCode : "DIRECT");
        record.setPlayerName(prefsManager.getPlayerName());
        record.setOpponentName(opponentName);
        record.setResult(isWinner ? "WIN" : "LOSS");
        record.setStartedAt(timestamp);
        record.setCompletedAt(timestamp);
        record.setDurationSeconds(durationSeconds);
        record.setShotsFired(shotsFired);
        record.setHits(hits);
        record.setAccuracy(accuracy);

        historyRepo.insertMatch(record, new MatchHistoryRepository.Callback<Long>() {
            @Override
            public void onComplete(Long result) {
                Toast.makeText(GameResultActivity.this, "Match logged to war archives.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(Exception e) {
                // Non-fatal
            }
        });
    }

    /**
     * Demonstrates Unit 3 Syllabus: Local notification on match conclusion.
     */
    private void triggerNotification() {
        String title = isWinner ? "BattleFleet Victory!" : "BattleFleet Defeat";
        String message = String.format(Locale.getDefault(),
                "Match against %s concluded. Accuracy: %.1f%%", opponentName, accuracy);
        NotificationUtils.showMatchCompletedNotification(this, title, message);
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}

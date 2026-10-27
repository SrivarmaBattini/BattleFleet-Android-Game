package com.example.battlefleet;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.battlefleet.activities.LobbyActivity;
import com.example.battlefleet.activities.MatchHistoryActivity;
import com.example.battlefleet.activities.RulesActivity;
import com.example.battlefleet.activities.SettingsActivity;
import com.example.battlefleet.activities.StatisticsActivity;
import com.example.battlefleet.database.MatchHistoryRepository;
import com.example.battlefleet.models.PlayerStats;
import com.example.battlefleet.network.SimpleGameNetworkListener;
import com.example.battlefleet.network.TcpGameClient;
import com.example.battlefleet.utils.NotificationUtils;
import com.example.battlefleet.utils.PreferencesManager;
import com.google.android.material.appbar.MaterialToolbar;


public class MainActivity extends AppCompatActivity {

    private TextView tvCommanderName;
    private TextView tvServerStatus;
    private View viewStatusDot;
    private TextView tvQuickStats;
    private TextView tvServerEndpoint;

    private PreferencesManager prefsManager;
    private MatchHistoryRepository historyRepo;
    private TcpGameClient tcpClient;

    private final SimpleGameNetworkListener networkListener = new SimpleGameNetworkListener() {
        @Override
        public void onConnected() {
            updateConnectionUI(true);
        }

        @Override
        public void onDisconnected() {
            updateConnectionUI(false);
        }

        @Override
        public void onConnectionFailed(String errorMessage) {
            updateConnectionUI(false);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Managers & Utilities
        prefsManager = new PreferencesManager(this);
        historyRepo = MatchHistoryRepository.getInstance(this);
        tcpClient = TcpGameClient.getInstance();

        // Notification channel creation
        NotificationUtils.createNotificationChannel(this);

        initViews();
        setupClickListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        tcpClient.addListener(networkListener);
        refreshData();
    }

    @Override
    protected void onPause() {
        super.onPause();
        tcpClient.removeListener(networkListener);
    }

    private void initViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar_main);
        setSupportActionBar(toolbar);

        tvCommanderName = findViewById(R.id.tv_main_commander_name);
        tvServerStatus = findViewById(R.id.tv_main_server_status);
        viewStatusDot = findViewById(R.id.view_status_dot);
        tvQuickStats = findViewById(R.id.tv_main_quick_stats);
        tvServerEndpoint = findViewById(R.id.tv_main_server_endpoint);
    }

    private void refreshData() {
        // SharedPreferences values
        String name = prefsManager.getPlayerName();
        tvCommanderName.setText(name);

        String host = prefsManager.getServerHost();
        int port = prefsManager.getServerPort();
        tvServerEndpoint.setText(host + ":" + port);

        updateConnectionUI(tcpClient.isConnected());

        // Load SQLite aggregate statistics
        historyRepo.getStatistics(new MatchHistoryRepository.Callback<PlayerStats>() {
            @Override
            public void onComplete(PlayerStats stats) {
                if (stats != null) {
                    tvQuickStats.setText(String.format("Combat Record: %d Battles (%d W / %d L)",
                            stats.getTotalMatches(), stats.getWins(), stats.getLosses()));
                }
            }

            @Override
            public void onError(Exception e) {
                tvQuickStats.setText("Combat Record: 0 Battles (0 W / 0 L)");
            }
        });
    }

    private void updateConnectionUI(boolean isConnected) {
        if (isConnected) {
            tvServerStatus.setText("ONLINE");
            tvServerStatus.setTextColor(ContextCompat.getColor(this, R.color.ship_deployed));
            viewStatusDot.setBackgroundResource(R.drawable.badge_status_online);
        } else {
            tvServerStatus.setText("OFFLINE");
            tvServerStatus.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
            viewStatusDot.setBackgroundResource(R.drawable.badge_status_offline);
        }
    }

    private void setupClickListeners() {
        // Host Room Card
        findViewById(R.id.card_host_room).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, LobbyActivity.class);
            intent.putExtra(LobbyActivity.EXTRA_MODE, LobbyActivity.MODE_HOST);
            startActivity(intent);
        });

        // Join Room Card
        findViewById(R.id.card_join_room).setOnClickListener(v -> showJoinRoomDialog());

        // Combat History Card
        findViewById(R.id.card_history).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, MatchHistoryActivity.class);
            startActivity(intent);
        });

        // War Statistics Card
        findViewById(R.id.card_stats).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, StatisticsActivity.class);
            startActivity(intent);
        });

        // Tactical Manual & Rules Card
        findViewById(R.id.card_rules).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, RulesActivity.class);
            startActivity(intent);
        });

        // Settings Card
        findViewById(R.id.card_settings).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });
    }

    private void showJoinRoomDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_join_room, null);

        EditText input = dialogView.findViewById(R.id.et_join_room_code);
        Button btnCancel = dialogView.findViewById(R.id.btn_join_cancel);
        Button btnConfirm = dialogView.findViewById(R.id.btn_join_confirm);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            String code = input.getText().toString().trim().toUpperCase();
            if (code.isEmpty()) {
                Toast.makeText(MainActivity.this, "Room code cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            Intent intent = new Intent(MainActivity.this, LobbyActivity.class);
            intent.putExtra(LobbyActivity.EXTRA_MODE, LobbyActivity.MODE_JOIN);
            intent.putExtra(LobbyActivity.EXTRA_ROOM_CODE, code);
            startActivity(intent);
        });

        input.setOnEditorActionListener((v, actionId, event) -> {
            btnConfirm.performClick();
            return true;
        });

        dialog.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_history) {
            startActivity(new Intent(this, MatchHistoryActivity.class));
            return true;
        } else if (id == R.id.action_stats) {
            startActivity(new Intent(this, StatisticsActivity.class));
            return true;
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.action_rules) {
            startActivity(new Intent(this, RulesActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
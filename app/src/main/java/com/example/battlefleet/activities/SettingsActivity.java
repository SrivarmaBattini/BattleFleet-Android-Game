package com.example.battlefleet.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.battlefleet.R;
import com.example.battlefleet.database.MatchHistoryRepository;
import com.example.battlefleet.network.SimpleGameNetworkListener;
import com.example.battlefleet.network.TcpGameClient;
import com.example.battlefleet.utils.PreferencesManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;


public class SettingsActivity extends AppCompatActivity {

    private EditText etPlayerName;
    private EditText etHost;
    private EditText etPort;
    private MaterialSwitch switchSound;
    private MaterialSwitch switchVibration;
    private ProgressBar pbTest;
    private TextView tvTestResult;

    private PreferencesManager prefsManager;
    private MatchHistoryRepository historyRepo;
    private TcpGameClient tcpClient;

    private final SimpleGameNetworkListener testListener = new SimpleGameNetworkListener() {
        @Override
        public void onConnected() {
            pbTest.setVisibility(View.GONE);
            tvTestResult.setText("SUCCESS (CONNECTED)");
            tvTestResult.setTextColor(ContextCompat.getColor(SettingsActivity.this, R.color.ship_deployed));
            Toast.makeText(SettingsActivity.this, "TCP Server Reachable!", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onConnectionFailed(String errorMessage) {
            pbTest.setVisibility(View.GONE);
            tvTestResult.setText("FAILED TO REACH");
            tvTestResult.setTextColor(ContextCompat.getColor(SettingsActivity.this, R.color.hit_red));
            Toast.makeText(SettingsActivity.this, "Failed: " + errorMessage, Toast.LENGTH_LONG).show();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefsManager = new PreferencesManager(this);
        historyRepo = MatchHistoryRepository.getInstance(this);
        tcpClient = TcpGameClient.getInstance();

        initViews();
        loadSettings();
    }

    private void initViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar_settings);
        toolbar.setNavigationOnClickListener(v -> finish());

        etPlayerName = findViewById(R.id.et_settings_player_name);
        etHost = findViewById(R.id.et_settings_host);
        etPort = findViewById(R.id.et_settings_port);
        switchSound = findViewById(R.id.switch_sound);
        switchVibration = findViewById(R.id.switch_vibration);
        pbTest = findViewById(R.id.pb_test_connection);
        tvTestResult = findViewById(R.id.tv_test_result);

        // Test connection button
        findViewById(R.id.btn_test_connection).setOnClickListener(v -> testServerConnection());

        // Save settings button
        findViewById(R.id.btn_save_settings).setOnClickListener(v -> saveSettings());

        // Reset settings button
        findViewById(R.id.btn_reset_settings).setOnClickListener(v -> confirmResetSettings());

        // Clear history button
        findViewById(R.id.btn_clear_history).setOnClickListener(v -> confirmClearHistory());
    }

    private void loadSettings() {
        etPlayerName.setText(prefsManager.getPlayerName());
        etHost.setText(prefsManager.getServerHost());
        etPort.setText(String.valueOf(prefsManager.getServerPort()));
        switchSound.setChecked(prefsManager.isSoundEnabled());
        switchVibration.setChecked(prefsManager.isVibrationEnabled());
    }

    private void testServerConnection() {
        String host = etHost.getText().toString().trim();
        String portStr = etPort.getText().toString().trim();

        if (host.isEmpty() || portStr.isEmpty()) {
            Toast.makeText(this, "Enter valid host and port", Toast.LENGTH_SHORT).show();
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port <= 0 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Port must be between 1 and 65535", Toast.LENGTH_SHORT).show();
            return;
        }

        pbTest.setVisibility(View.VISIBLE);
        tvTestResult.setText("Testing...");
        tvTestResult.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));

        tcpClient.addListener(testListener);
        tcpClient.disconnect();
        tcpClient.connect(host, port);
    }

    private void saveSettings() {
        String name = etPlayerName.getText().toString().trim();
        String host = etHost.getText().toString().trim();
        String portStr = etPort.getText().toString().trim();

        if (name.isEmpty()) {
            name = PreferencesManager.DEFAULT_NAME;
        }
        if (host.isEmpty()) {
            host = PreferencesManager.DEFAULT_HOST;
        }

        int port = PreferencesManager.DEFAULT_PORT;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException ignored) {}

        prefsManager.setPlayerName(name);
        prefsManager.setServerHost(host);
        prefsManager.setServerPort(port);
        prefsManager.setSoundEnabled(switchSound.isChecked());
        prefsManager.setVibrationEnabled(switchVibration.isChecked());

        Toast.makeText(this, "Tactical Settings Saved!", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void confirmResetSettings() {
        new AlertDialog.Builder(this)
                .setTitle("Reset Settings")
                .setMessage("Reset all server parameters and preferences to factory defaults?")
                .setPositiveButton("Reset", (dialog, which) -> {
                    prefsManager.resetDefaults();
                    loadSettings();
                    Toast.makeText(SettingsActivity.this, "Settings reset to defaults", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmClearHistory() {
        new AlertDialog.Builder(this)
                .setTitle("Clear Combat Log")
                .setMessage("Are you sure you want to permanently erase all local match history records?")
                .setPositiveButton("Erase All", (dialog, which) -> {
                    historyRepo.clearAll(new MatchHistoryRepository.Callback<Boolean>() {
                        @Override
                        public void onComplete(Boolean result) {
                            Toast.makeText(SettingsActivity.this, "All combat history wiped.", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(Exception e) {
                            Toast.makeText(SettingsActivity.this, "Error erasing history: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        tcpClient.removeListener(testListener);
    }
}

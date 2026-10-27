package com.example.battlefleet.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.battlefleet.R;
import com.example.battlefleet.network.SimpleGameNetworkListener;
import com.example.battlefleet.network.TcpGameClient;
import com.example.battlefleet.utils.PreferencesManager;
import com.google.android.material.appbar.MaterialToolbar;


public class LobbyActivity extends AppCompatActivity {
    public static final String EXTRA_MODE = "extra_mode";
    public static final String EXTRA_ROOM_CODE = "extra_room_code";

    public static final int MODE_HOST = 1;
    public static final int MODE_JOIN = 2;

    private int mode;
    private String targetRoomCode;

    private TextView tvRoomCode;
    private TextView tvHostName;
    private TextView tvHostStatus;
    private TextView tvGuestName;
    private TextView tvGuestStatus;
    private ProgressBar pbGuestWaiting;
    private Button btnProceed;

    private TcpGameClient tcpClient;
    private PreferencesManager prefsManager;

    private final SimpleGameNetworkListener lobbyListener = new SimpleGameNetworkListener() {
        @Override
        public void onConnected() {
            String name = prefsManager.getPlayerName();
            if (mode == MODE_HOST) {
                tcpClient.sendCreateRoom(name);
            } else {
                tcpClient.sendJoinRoom(targetRoomCode, name);
            }
        }

        @Override
        public void onConnectionFailed(String errorMessage) {
            Toast.makeText(LobbyActivity.this, "Connection failed: " + errorMessage, Toast.LENGTH_LONG).show();
            finish();
        }

        @Override
        public void onRoomCreated(String roomId, String playerId, String playerName) {
            tvRoomCode.setText(roomId);
            tvHostName.setText(playerName);
            tvHostStatus.setText("READY");
            Toast.makeText(LobbyActivity.this, "Room created! Share code: " + roomId, Toast.LENGTH_LONG).show();
        }

        @Override
        public void onRoomJoined(String roomId, String playerId, String oppName) {
            tvRoomCode.setText(roomId);
            tvHostName.setText(oppName);
            tvHostStatus.setText("CONNECTED");
            tvGuestName.setText(prefsManager.getPlayerName());
            tvGuestStatus.setVisibility(View.VISIBLE);
            tvGuestStatus.setText("CONNECTED");
            pbGuestWaiting.setVisibility(View.GONE);
            btnProceed.setEnabled(true);
            Toast.makeText(LobbyActivity.this, "Joined room " + roomId, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onPlayerJoined(String opponentId, String opponentName) {
            tvGuestName.setText(opponentName);
            tvGuestStatus.setVisibility(View.VISIBLE);
            tvGuestStatus.setText("CONNECTED");
            pbGuestWaiting.setVisibility(View.GONE);
            btnProceed.setEnabled(true);
            Toast.makeText(LobbyActivity.this, "Commander " + opponentName + " has joined the room!", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onPlayerLeft(String message) {
            tvGuestName.setText("Awaiting Opponent...");
            tvGuestStatus.setVisibility(View.GONE);
            pbGuestWaiting.setVisibility(View.VISIBLE);
            btnProceed.setEnabled(false);
            Toast.makeText(LobbyActivity.this, message, Toast.LENGTH_LONG).show();
        }

        @Override
        public void onError(String code, String message) {
            Toast.makeText(LobbyActivity.this, "Server error: " + message, Toast.LENGTH_LONG).show();
            if ("ROOM_NOT_FOUND".equals(code) || "ROOM_FULL".equals(code)) {
                finish();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lobby);

        mode = getIntent().getIntExtra(EXTRA_MODE, MODE_HOST);
        targetRoomCode = getIntent().getStringExtra(EXTRA_ROOM_CODE);

        tcpClient = TcpGameClient.getInstance();
        prefsManager = new PreferencesManager(this);

        initViews();
        connectAndInitiate();
    }

    private void initViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar_lobby);
        toolbar.setNavigationOnClickListener(v -> confirmLeaveRoom());

        tvRoomCode = findViewById(R.id.tv_lobby_room_code);
        tvHostName = findViewById(R.id.tv_host_name);
        tvHostStatus = findViewById(R.id.tv_host_status);
        tvGuestName = findViewById(R.id.tv_guest_name);
        tvGuestStatus = findViewById(R.id.tv_guest_status);
        pbGuestWaiting = findViewById(R.id.pb_guest_waiting);
        btnProceed = findViewById(R.id.btn_proceed_placement);

        findViewById(R.id.iv_copy_code).setOnClickListener(v -> copyRoomCode());
        btnProceed.setOnClickListener(v -> navigateToShipPlacement());
        findViewById(R.id.btn_leave_room).setOnClickListener(v -> confirmLeaveRoom());
    }

    private void connectAndInitiate() {
        tcpClient.addListener(lobbyListener);

        if (!tcpClient.isConnected()) {
            String host = prefsManager.getServerHost();
            int port = prefsManager.getServerPort();
            tcpClient.connect(host, port);
        } else {
            // Already connected
            String name = prefsManager.getPlayerName();
            if (mode == MODE_HOST) {
                tcpClient.sendCreateRoom(name);
            } else {
                tcpClient.sendJoinRoom(targetRoomCode, name);
            }
        }
    }

    private void copyRoomCode() {
        String code = tvRoomCode.getText().toString();
        if (!code.isEmpty() && !code.contains("-")) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("BattleFleet Room Code", code);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Room Code " + code + " copied to clipboard!", Toast.LENGTH_SHORT).show();
        }
    }

    private void navigateToShipPlacement() {
        Intent intent = new Intent(LobbyActivity.this, ShipPlacementActivity.class);
        startActivity(intent);
        finish();
    }

    private void confirmLeaveRoom() {
        new AlertDialog.Builder(this)
                .setTitle("Abandon Room")
                .setMessage("Are you sure you want to exit this lobby?")
                .setPositiveButton("Leave", (dialog, which) -> {
                    tcpClient.sendLeaveRoom(null);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        confirmLeaveRoom();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        tcpClient.removeListener(lobbyListener);
    }
}

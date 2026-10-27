package com.example.battlefleet.activities;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.battlefleet.R;
import com.example.battlefleet.models.BoardCell;
import com.example.battlefleet.models.CellState;
import com.example.battlefleet.models.GameBoard;
import com.example.battlefleet.network.SimpleGameNetworkListener;
import com.example.battlefleet.network.TcpGameClient;
import com.example.battlefleet.utils.PreferencesManager;
import com.example.battlefleet.views.BoardView;
import com.google.android.material.appbar.MaterialToolbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;


public class BattleActivity extends AppCompatActivity {
    public static final String EXTRA_GAME_BOARD = "extra_game_board";
    public static final String EXTRA_FIRST_TURN = "extra_first_turn";
    public static final String EXTRA_CURRENT_TURN = "extra_current_turn";
    public static final String EXTRA_OPPONENT_NAME = "extra_opponent_name";
    public static final String EXTRA_ROOM_ID = "extra_room_id";

    public static class ChatEntry {
        public final String sender;
        public final String message;
        public final boolean isMe;
        public final long timestamp;

        public ChatEntry(String sender, String message, boolean isMe, long timestamp) {
            this.sender = sender;
            this.message = message;
            this.isMe = isMe;
            this.timestamp = timestamp;
        }
    }

    private BoardView boardOpponentRadar;
    private BoardView boardFriendlyFleet;

    private TextView tvTurnBadge;
    private TextView tvFriendlyFleetStatus;
    private TextView tvEnemyFleetStatus;
    private TextView tvBattleLog;
    private EditText etChat;
    private ImageView btnSendChat;

    private GameBoard friendlyBoard;
    private GameBoard opponentRadarBoard; // tracks hits & misses against opponent

    private String currentTurn;
    private String opponentName;
    private String roomId;
    private boolean isMyTurn = false;

    private int myShots = 0;
    private int myHits = 0;
    private int enemyRemainingShips = 5;

    private final List<ChatEntry> chatHistory = new ArrayList<>();
    private AlertDialog tacticalCommsDialog;
    private LinearLayout llDialogMessages;
    private ScrollView scrollDialogComms;
    private TextView tvDialogEmpty;

    private TcpGameClient tcpClient;
    private PreferencesManager prefsManager;
    private Vibrator vibrator;

    private final SimpleGameNetworkListener battleListener = new SimpleGameNetworkListener() {
        @Override
        public void onAttackResult(String attackerId, String defenderId, int row, int col,
                                   String result, boolean sunk, String shipName, String nextTurn, int remainingShips) {
            handleAttackResult(attackerId, defenderId, row, col, result, sunk, shipName, nextTurn, remainingShips);
        }

        @Override
        public void onGameOver(String winnerId, String winnerName, String loserName,
                               String reason, int duration, int shots, int hits) {
            handleGameOver(winnerId, winnerName, loserName, duration, shots, hits);
        }

        @Override
        public void onChatMessage(String senderName, String message, long timestamp) {
            ChatEntry entry = new ChatEntry(senderName, message, false, timestamp);
            chatHistory.add(entry);

            logMessage("[COMMS] " + senderName + ": " + message);
            Toast.makeText(BattleActivity.this, senderName + ": " + message, Toast.LENGTH_SHORT).show();
            triggerVibration(40);

            if (tacticalCommsDialog != null && tacticalCommsDialog.isShowing() && llDialogMessages != null) {
                appendChatBubble(llDialogMessages, entry);
            }
        }

        @Override
        public void onOpponentDisconnected(String message) {
            logMessage("[ALERT] " + message);
            new AlertDialog.Builder(BattleActivity.this)
                    .setTitle("Opponent Disconnected")
                    .setMessage(message + "\nVictory awarded by default.")
                    .setCancelable(false)
                    .setPositiveButton("Proceed to Results", (dialog, which) -> {
                        handleGameOver(tcpClient.getLocalPlayerId(), prefsManager.getPlayerName(), opponentName, 60, myShots, myHits);
                    })
                    .show();
        }

        @Override
        public void onError(String code, String message) {
            Toast.makeText(BattleActivity.this, "Combat error: " + message, Toast.LENGTH_LONG).show();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_battle);

        tcpClient = TcpGameClient.getInstance();
        prefsManager = new PreferencesManager(this);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        // Retrieve passed parameters
        Intent intent = getIntent();
        friendlyBoard = (GameBoard) intent.getSerializableExtra(EXTRA_GAME_BOARD);
        if (friendlyBoard == null) {
            friendlyBoard = new GameBoard();
            friendlyBoard.randomizeFleet();
        }
        currentTurn = intent.getStringExtra(EXTRA_CURRENT_TURN);
        opponentName = intent.getStringExtra(EXTRA_OPPONENT_NAME);
        if (opponentName == null) opponentName = "Opponent";

        roomId = intent.getStringExtra(EXTRA_ROOM_ID);
        if (roomId == null || roomId.isEmpty()) {
            roomId = tcpClient.getCurrentRoomId();
        } else {
            tcpClient.setCurrentRoomId(roomId);
        }

        opponentRadarBoard = new GameBoard();

        initViews();
        updateTurnDisplay();
        logMessage("[RADAR] Engagement initialized against " + opponentName + ".");
    }

    @Override
    protected void onResume() {
        super.onResume();
        tcpClient.addListener(battleListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        tcpClient.removeListener(battleListener);
    }

    private void initViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar_battle);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("VS " + opponentName);
        }

        tvTurnBadge = findViewById(R.id.tv_battle_turn_badge);
        tvFriendlyFleetStatus = findViewById(R.id.tv_friendly_fleet_status);
        tvEnemyFleetStatus = findViewById(R.id.tv_enemy_fleet_status);
        tvBattleLog = findViewById(R.id.tv_battle_log);

        etChat = findViewById(R.id.et_battle_chat);
        btnSendChat = findViewById(R.id.btn_battle_send_chat);

        // Primary targeting radar (opponent)
        boardOpponentRadar = findViewById(R.id.board_opponent_radar);
        boardOpponentRadar.setMode(BoardView.Mode.TARGET_RADAR);
        boardOpponentRadar.setBoard(opponentRadarBoard);

        // Secondary friendly fleet status
        boardFriendlyFleet = findViewById(R.id.board_friendly_fleet);
        boardFriendlyFleet.setMode(BoardView.Mode.FLEET_DEFENSE);
        boardFriendlyFleet.setBoard(friendlyBoard);

        // Radar click listener: firing attacks
        boardOpponentRadar.setOnCellClickListener((row, col) -> handleRadarTargetClick(row, col));

        // Chat send button and keyboard Send action
        btnSendChat.setOnClickListener(v -> sendChatMessage());
        etChat.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                sendChatMessage();
                return true;
            }
            return false;
        });
    }

    private void handleRadarTargetClick(int row, int col) {
        if (!isMyTurn) {
            Toast.makeText(this, "It is not your turn yet! Stand by.", Toast.LENGTH_SHORT).show();
            return;
        }

        BoardCell cell = opponentRadarBoard.getCell(row, col);
        if (cell != null && cell.isAttacked()) {
            Toast.makeText(this, "Coordinates already targeted!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Send attack to TCP server
        tcpClient.sendAttack(null, row, col);
        logMessage(String.format("[TORPEDO] Fired on grid (%c%d)...", (char) ('A' + row), col + 1));
    }

    private void handleAttackResult(String attackerId, String defenderId, int row, int col,
                                    String result, boolean sunk, String shipName, String nextTurn, int remainingShips) {
        boolean isHit = "hit".equalsIgnoreCase(result);
        String myId = tcpClient.getLocalPlayerId();
        boolean iAttacked = myId != null && myId.equals(attackerId);

        if (iAttacked) {
            myShots++;
            if (isHit) myHits++;

            // Update opponent radar board
            BoardCell cell = opponentRadarBoard.getCell(row, col);
            if (cell != null) {
                cell.setState(isHit ? (sunk ? CellState.SUNK : CellState.HIT) : CellState.MISS);
            }
            boardOpponentRadar.invalidate();

            this.enemyRemainingShips = remainingShips;
            tvEnemyFleetStatus.setText("Enemy Fleet: " + enemyRemainingShips + "/5 Intact");

            if (isHit) {
                if (sunk) {
                    logMessage(String.format("💥 DIRECT HIT! Enemy %s SUNK at (%c%d)!", shipName, (char) ('A' + row), col + 1));
                    triggerVibration(180);
                } else {
                    logMessage(String.format("🎯 DIRECT HIT at (%c%d)!", (char) ('A' + row), col + 1));
                    triggerVibration(90);
                }
            } else {
                logMessage(String.format("💧 Torpedo missed at (%c%d).", (char) ('A' + row), col + 1));
            }
        } else {
            // Opponent attacked our friendly fleet
            friendlyBoard.registerAttack(row, col, isHit, sunk, shipName);
            boardFriendlyFleet.invalidate();

            int friendlyRemaining = friendlyBoard.getRemainingShipsCount();
            tvFriendlyFleetStatus.setText("Allied Fleet: " + friendlyRemaining + "/5 Intact");

            if (isHit) {
                if (sunk) {
                    logMessage(String.format("⚠️ CRITICAL ALERT! Allied %s was SUNK at (%c%d)!", shipName, (char) ('A' + row), col + 1));
                    triggerVibration(300);
                } else {
                    logMessage(String.format("💥 ENEMY HIT our ship at (%c%d)!", (char) ('A' + row), col + 1));
                    triggerVibration(120);
                }
            } else {
                logMessage(String.format("🛡️ Enemy torpedo splashed into open water at (%c%d).", (char) ('A' + row), col + 1));
            }
        }

        // Update turn
        this.currentTurn = nextTurn;
        updateTurnDisplay();
    }

    private void updateTurnDisplay() {
        String myId = tcpClient.getLocalPlayerId();
        isMyTurn = (myId != null && myId.equals(currentTurn));

        if (isMyTurn) {
            tvTurnBadge.setText("YOUR TURN - FIRE TORPEDO");
            tvTurnBadge.setTextColor(ContextCompat.getColor(this, R.color.ship_deployed));
            tvTurnBadge.setBackgroundResource(R.drawable.badge_turn_your);
        } else {
            tvTurnBadge.setText("ENEMY TURN - BRACE FOR IMPACT");
            tvTurnBadge.setTextColor(ContextCompat.getColor(this, R.color.hit_red));
            tvTurnBadge.setBackgroundResource(R.drawable.badge_turn_opponent);
        }
    }

    private void handleGameOver(String winnerId, String winnerName, String loserName,
                                int duration, int shots, int hits) {
        String myId = tcpClient.getLocalPlayerId();
        boolean isWinner = (myId != null && myId.equals(winnerId));

        double accuracy = myShots > 0 ? (myHits * 100.0 / myShots) : 0.0;

        Intent intent = new Intent(BattleActivity.this, GameResultActivity.class);
        intent.putExtra(GameResultActivity.EXTRA_IS_WINNER, isWinner);
        intent.putExtra(GameResultActivity.EXTRA_WINNER_NAME, winnerName);
        intent.putExtra(GameResultActivity.EXTRA_LOSER_NAME, loserName);
        intent.putExtra(GameResultActivity.EXTRA_OPPONENT_NAME, opponentName);
        intent.putExtra(GameResultActivity.EXTRA_DURATION_SECONDS, duration);
        intent.putExtra(GameResultActivity.EXTRA_SHOTS_FIRED, myShots);
        intent.putExtra(GameResultActivity.EXTRA_HITS, myHits);
        intent.putExtra(GameResultActivity.EXTRA_ACCURACY, accuracy);
        intent.putExtra(GameResultActivity.EXTRA_ROOM_CODE, tcpClient.getCurrentRoomId());

        startActivity(intent);
        finish();
    }

    private void sendChatMessage() {
        String text = etChat.getText().toString();
        sendChatMessage(text);
        etChat.setText("");
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etChat.getWindowToken(), 0);
        }
    }

    private void sendChatMessage(String text) {
        if (text == null) return;
        text = text.trim();
        if (text.isEmpty()) return;

        String targetRoom = (roomId != null && !roomId.isEmpty()) ? roomId : tcpClient.getCurrentRoomId();
        tcpClient.sendChat(targetRoom, text);

        ChatEntry entry = new ChatEntry(prefsManager.getPlayerName(), text, true, System.currentTimeMillis());
        chatHistory.add(entry);

        logMessage("[COMMS] You: " + text);

        if (tacticalCommsDialog != null && tacticalCommsDialog.isShowing() && llDialogMessages != null) {
            appendChatBubble(llDialogMessages, entry);
        }
    }

    private void showTacticalCommsDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_tactical_comms, null);

        TextView tvSubtitle = dialogView.findViewById(R.id.tv_dialog_comms_subtitle);
        tvSubtitle.setText("Secure link • " + prefsManager.getPlayerName() + " vs " + opponentName);

        llDialogMessages = dialogView.findViewById(R.id.ll_dialog_chat_messages);
        scrollDialogComms = dialogView.findViewById(R.id.scroll_dialog_comms);
        tvDialogEmpty = dialogView.findViewById(R.id.tv_dialog_comms_empty);

        // Populate history
        if (chatHistory.isEmpty()) {
            tvDialogEmpty.setVisibility(View.VISIBLE);
        } else {
            tvDialogEmpty.setVisibility(View.GONE);
            for (ChatEntry entry : chatHistory) {
                appendChatBubble(llDialogMessages, entry);
            }
        }

        // Setup preset chips
        Button chipHit = dialogView.findViewById(R.id.chip_direct_hit);
        Button chipMiss = dialogView.findViewById(R.id.chip_miss);
        Button chipSunk = dialogView.findViewById(R.id.chip_sunk);
        Button chipBrace = dialogView.findViewById(R.id.chip_brace);
        Button chipGg = dialogView.findViewById(R.id.chip_gg);

        chipHit.setOnClickListener(v -> sendChatMessage("🎯 Direct Hit!"));
        chipMiss.setOnClickListener(v -> sendChatMessage("🌊 Splash / Miss!"));
        chipSunk.setOnClickListener(v -> sendChatMessage("💥 You sank my battleship!"));
        chipBrace.setOnClickListener(v -> sendChatMessage("🛡️ Brace for impact!"));
        chipGg.setOnClickListener(v -> sendChatMessage("🫡 Well fought, Captain!"));

        // Setup custom text input
        EditText etDialogInput = dialogView.findViewById(R.id.et_dialog_chat_input);
        ImageButton btnDialogSend = dialogView.findViewById(R.id.btn_dialog_chat_send);

        Runnable transmitDialog = () -> {
            String text = etDialogInput.getText().toString();
            if (!text.trim().isEmpty()) {
                sendChatMessage(text);
                etDialogInput.setText("");
            }
        };

        btnDialogSend.setOnClickListener(v -> transmitDialog.run());
        etDialogInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                transmitDialog.run();
                return true;
            }
            return false;
        });

        ImageButton btnClose = dialogView.findViewById(R.id.btn_dialog_comms_close);

        tacticalCommsDialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (tacticalCommsDialog.getWindow() != null) {
            tacticalCommsDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnClose.setOnClickListener(v -> tacticalCommsDialog.dismiss());
        tacticalCommsDialog.setOnDismissListener(d -> {
            tacticalCommsDialog = null;
            llDialogMessages = null;
            scrollDialogComms = null;
            tvDialogEmpty = null;
        });

        tacticalCommsDialog.show();
    }

    private void appendChatBubble(ViewGroup container, ChatEntry entry) {
        if (tvDialogEmpty != null) {
            tvDialogEmpty.setVisibility(View.GONE);
        }

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = (int) (4 * getResources().getDisplayMetrics().density);
        params.bottomMargin = (int) (4 * getResources().getDisplayMetrics().density);

        if (entry.isMe) {
            params.gravity = Gravity.END;
            params.leftMargin = (int) (40 * getResources().getDisplayMetrics().density);
        } else {
            params.gravity = Gravity.START;
            params.rightMargin = (int) (40 * getResources().getDisplayMetrics().density);
        }
        bubble.setLayoutParams(params);
        bubble.setBackgroundResource(R.drawable.bg_card_tactical);
        int padH = (int) (12 * getResources().getDisplayMetrics().density);
        int padV = (int) (8 * getResources().getDisplayMetrics().density);
        bubble.setPadding(padH, padV, padH, padV);

        TextView tvHeader = new TextView(this);
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String timeStr = sdf.format(new Date(entry.timestamp));
        tvHeader.setText(String.format("%s • %s", entry.isMe ? "YOU" : entry.sender, timeStr));
        tvHeader.setTextSize(10);
        tvHeader.setTextColor(ContextCompat.getColor(this, entry.isMe ? R.color.radar_cyan : R.color.gold_rank));
        tvHeader.setTypeface(null, Typeface.BOLD);
        bubble.addView(tvHeader);

        TextView tvBody = new TextView(this);
        tvBody.setText(entry.message);
        tvBody.setTextSize(13);
        tvBody.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        bubble.addView(tvBody);

        container.addView(bubble);

        if (scrollDialogComms != null) {
            scrollDialogComms.post(() -> scrollDialogComms.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void logMessage(String text) {
        tvBattleLog.setText(text);
    }

    private void triggerVibration(long ms) {
        if (prefsManager.isVibrationEnabled() && vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(ms);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_battle, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_surrender) {
            confirmSurrender();
            return true;
        } else if (id == R.id.action_chat) {
            showTacticalCommsDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void confirmSurrender() {
        new AlertDialog.Builder(this)
                .setTitle("Abort Battle")
                .setMessage("Surrendering will forfeit the engagement and record a defeat. Confirm?")
                .setPositiveButton("Surrender", (dialog, which) -> {
                    tcpClient.sendLeaveRoom(null);
                    finish();
                })
                .setNegativeButton("Keep Fighting", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        confirmSurrender();
    }
}

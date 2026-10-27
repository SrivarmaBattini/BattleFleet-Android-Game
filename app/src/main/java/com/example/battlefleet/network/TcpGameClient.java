package com.example.battlefleet.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Robust TCP Socket Client for BattleFleet.
 * - Handles non-blocking background connection and read loop.
 * - Enforces newline-delimited JSON message framing.
 * - Marshals all incoming network events safely to the Android Main UI Thread.
 * - Provides thread-safe socket lifecycle and graceful disconnection.
 */
public class TcpGameClient {
    private static final String TAG = "TcpGameClient";
    private static final int CONNECT_TIMEOUT_MS = 6000;
    private static volatile TcpGameClient instance;

    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private ConnectionState state = ConnectionState.DISCONNECTED;

    private final ExecutorService networkExecutor;
    private final Handler mainHandler;
    private final List<GameNetworkListener> listeners;
    private final Object socketLock = new Object();

    // Session cache
    private String currentRoomId;
    private String localPlayerId;
    private String localPlayerName;
    private String opponentName;
    private boolean isHost;

    private TcpGameClient() {
        this.networkExecutor = Executors.newCachedThreadPool();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.listeners = new ArrayList<>();
    }

    public static synchronized TcpGameClient getInstance() {
        if (instance == null) {
            instance = new TcpGameClient();
        }
        return instance;
    }

    public synchronized void addListener(GameNetworkListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public synchronized void removeListener(GameNetworkListener listener) {
        listeners.remove(listener);
    }

    public ConnectionState getState() {
        return state;
    }

    public boolean isConnected() {
        return state == ConnectionState.CONNECTED && socket != null && socket.isConnected() && !socket.isClosed();
    }

    public String getCurrentRoomId() { return currentRoomId; }
    public String getLocalPlayerId() { return localPlayerId; }
    public String getLocalPlayerName() { return localPlayerName; }
    public String getOpponentName() { return opponentName; }
    public boolean isHost() { return isHost; }

    public void setLocalPlayerName(String name) { this.localPlayerName = name; }
    public void setCurrentRoomId(String id) { this.currentRoomId = id; }
    public void setOpponentName(String name) { this.opponentName = name; }

    /**
     * Connects to the TCP server on a background thread.
     */
    public void connect(String host, int port) {
        if (state == ConnectionState.CONNECTING || state == ConnectionState.CONNECTED) {
            Log.d(TAG, "Already connected or connecting. Skipping redundant connect call.");
            return;
        }

        state = ConnectionState.CONNECTING;
        networkExecutor.execute(() -> {
            try {
                Log.i(TAG, "Connecting to TCP server at " + host + ":" + port + "...");
                Socket newSocket = new Socket();
                newSocket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
                newSocket.setKeepAlive(true);

                synchronized (socketLock) {
                    this.socket = newSocket;
                    this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    this.writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                    this.state = ConnectionState.CONNECTED;
                }

                Log.i(TAG, "Successfully connected to " + host + ":" + port);
                notifyMainThread(GameNetworkListener::onConnected);

                // Start continuous background read loop
                runReadLoop();

            } catch (Exception e) {
                Log.e(TAG, "Connection failure to " + host + ":" + port, e);
                disconnect();
                final String err = e.getMessage() != null ? e.getMessage() : "Failed to connect to " + host + ":" + port;
                notifyMainThread(listener -> listener.onConnectionFailed(err));
            }
        });
    }

    /**
     * Background continuous read loop enforcing newline-delimited JSON framing.
     */
    private void runReadLoop() {
        try {
            String line;
            while (state == ConnectionState.CONNECTED && reader != null && (line = reader.readLine()) != null) {
                final String jsonStr = line.trim();
                if (jsonStr.isEmpty()) continue;
                Log.d(TAG, "RECV: " + jsonStr);
                try {
                    JSONObject obj = new JSONObject(jsonStr);
                    handleIncomingJson(obj);
                } catch (JSONException je) {
                    Log.w(TAG, "Malformed JSON received: " + jsonStr, je);
                }
            }
        } catch (Exception e) {
            Log.i(TAG, "Read loop terminated or socket closed: " + e.getMessage());
        } finally {
            disconnect();
        }
    }

    /**
     * Parses server JSON messages and dispatches to registered listeners on the Main UI thread.
     */
    private void handleIncomingJson(JSONObject json) {
        String type = json.optString("type", "");

        switch (type) {
            case "pong":
                Log.d(TAG, "Pong received from server.");
                break;

            case "room_created": {
                final String roomId = json.optString("room_id");
                final String playerId = json.optString("player_id");
                final String pName = json.optString("player_name");
                this.currentRoomId = roomId;
                this.localPlayerId = playerId;
                this.localPlayerName = pName;
                this.isHost = true;
                notifyMainThread(l -> l.onRoomCreated(roomId, playerId, pName));
                break;
            }

            case "room_joined": {
                final String roomId = json.optString("room_id");
                final String playerId = json.optString("player_id");
                final String oppName = json.optString("opponent_name");
                this.currentRoomId = roomId;
                this.localPlayerId = playerId;
                this.opponentName = oppName;
                this.isHost = false;
                notifyMainThread(l -> l.onRoomJoined(roomId, playerId, oppName));
                break;
            }

            case "player_joined": {
                final String oppId = json.optString("opponent_id");
                final String oppName = json.optString("opponent_name");
                this.opponentName = oppName;
                notifyMainThread(l -> l.onPlayerJoined(oppId, oppName));
                break;
            }

            case "player_left": {
                final String msg = json.optString("message", "Opponent left the room.");
                notifyMainThread(l -> l.onPlayerLeft(msg));
                break;
            }

            case "opponent_ready": {
                final String oppId = json.optString("opponent_id");
                final String oppName = json.optString("opponent_name");
                notifyMainThread(l -> l.onOpponentReady(oppId, oppName));
                break;
            }

            case "placement_confirmed": {
                final String roomId = json.optString("room_id");
                notifyMainThread(l -> l.onPlacementConfirmed(roomId));
                break;
            }

            case "game_start": {
                final String roomId = json.optString("room_id");
                final String firstTurn = json.optString("first_turn");
                final String currentTurn = json.optString("current_turn");
                final JSONObject p1 = json.optJSONObject("player1");
                final JSONObject p2 = json.optJSONObject("player2");
                String opp = opponentName;
                if (p1 != null && p2 != null) {
                    if (p1.optString("id").equals(localPlayerId)) {
                        opp = p2.optString("name");
                    } else {
                        opp = p1.optString("name");
                    }
                }
                this.opponentName = opp;
                final String finalOpp = opp;
                notifyMainThread(l -> l.onGameStart(roomId, firstTurn, currentTurn, finalOpp));
                break;
            }

            case "attack_result": {
                final String attackerId = json.optString("attacker_id");
                final String defenderId = json.optString("defender_id");
                final int row = json.optInt("row");
                final int col = json.optInt("col");
                final String result = json.optString("result"); // "hit" or "miss"
                final boolean sunk = json.optBoolean("sunk", false);
                final String shipName = json.optString("ship_name", "");
                final String nextTurn = json.optString("next_turn", "");
                final int remainingShips = json.optInt("defender_remaining_ships", 5);

                notifyMainThread(l -> l.onAttackResult(attackerId, defenderId, row, col, result, sunk, shipName, nextTurn, remainingShips));
                break;
            }

            case "game_over": {
                final String winnerId = json.optString("winner_id");
                final String winnerName = json.optString("winner_name");
                final String loserName = json.optString("loser_name");
                final String reason = json.optString("reason", "fleet_destroyed");
                final int duration = json.optInt("duration_seconds", 0);
                JSONObject attStats = json.optJSONObject("attacker_stats");
                int shots = attStats != null ? attStats.optInt("shots_fired", 0) : 0;
                int hits = attStats != null ? attStats.optInt("hits", 0) : 0;

                notifyMainThread(l -> l.onGameOver(winnerId, winnerName, loserName, reason, duration, shots, hits));
                break;
            }

            case "chat_message": {
                final String sender = json.optString("sender_name", "Player");
                final String message = json.optString("message", "");
                final long ts = json.optLong("timestamp", System.currentTimeMillis());
                notifyMainThread(l -> l.onChatMessage(sender, message, ts));
                break;
            }

            case "opponent_disconnected": {
                final String msg = json.optString("message", "Opponent disconnected.");
                notifyMainThread(l -> l.onOpponentDisconnected(msg));
                break;
            }

            case "error":
            case "placement_error": {
                final String code = json.optString("code", "ERROR");
                final String msg = json.optString("message", "Unknown error");
                notifyMainThread(l -> l.onError(code, msg));
                break;
            }

            default:
                Log.w(TAG, "Unhandled message type: " + type);
                break;
        }
    }

    /**
     * Sends a raw JSON object safely on a background thread.
     */
    public void sendJson(JSONObject json) {
        networkExecutor.execute(() -> {
            synchronized (socketLock) {
                if (writer != null && state == ConnectionState.CONNECTED) {
                    try {
                        String payload = json.toString();
                        Log.d(TAG, "SEND: " + payload);
                        writer.println(payload);
                        writer.flush();
                    } catch (Exception e) {
                        Log.e(TAG, "Error writing message to socket", e);
                    }
                } else {
                    Log.w(TAG, "Cannot send, socket is not connected.");
                }
            }
        });
    }

    // Helper Action Methods

    public void sendCreateRoom(String playerName) {
        this.localPlayerName = playerName;
        JSONObject msg = new JSONObject();
        try {
            msg.put("type", "create_room");
            msg.put("player_name", playerName);
            sendJson(msg);
        } catch (JSONException ignored) {}
    }

    public void sendJoinRoom(String roomId, String playerName) {
        this.localPlayerName = playerName;
        this.currentRoomId = roomId;
        JSONObject msg = new JSONObject();
        try {
            msg.put("type", "join_room");
            msg.put("room_id", roomId);
            msg.put("player_name", playerName);
            sendJson(msg);
        } catch (JSONException ignored) {}
    }

    public void sendLeaveRoom(String roomId) {
        JSONObject msg = new JSONObject();
        try {
            msg.put("type", "leave_room");
            msg.put("room_id", roomId != null ? roomId : currentRoomId);
            sendJson(msg);
        } catch (JSONException ignored) {}
    }

    public void sendPlaceShips(String roomId, JSONArray shipsArray) {
        JSONObject msg = new JSONObject();
        try {
            msg.put("type", "place_ships");
            msg.put("room_id", roomId != null ? roomId : currentRoomId);
            msg.put("ships", shipsArray);
            sendJson(msg);
        } catch (JSONException ignored) {}
    }

    public void sendAttack(String roomId, int row, int col) {
        JSONObject msg = new JSONObject();
        try {
            msg.put("type", "attack");
            msg.put("room_id", roomId != null ? roomId : currentRoomId);
            msg.put("row", row);
            msg.put("col", col);
            sendJson(msg);
        } catch (JSONException ignored) {}
    }

    public void sendChat(String roomId, String text) {
        JSONObject msg = new JSONObject();
        try {
            msg.put("type", "chat");
            msg.put("room_id", roomId != null ? roomId : currentRoomId);
            msg.put("message", text);
            sendJson(msg);
        } catch (JSONException ignored) {}
    }

    public void sendPing() {
        JSONObject msg = new JSONObject();
        try {
            msg.put("type", "ping");
            sendJson(msg);
        } catch (JSONException ignored) {}
    }

    /**
     * Gracefully terminates socket and cleans up resources.
     */
    public void disconnect() {
        if (state == ConnectionState.DISCONNECTED) return;
        state = ConnectionState.DISCONNECTED;

        synchronized (socketLock) {
            try {
                if (writer != null) {
                    writer.close();
                    writer = null;
                }
                if (reader != null) {
                    reader.close();
                    reader = null;
                }
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                    socket = null;
                }
            } catch (Exception e) {
                Log.w(TAG, "Error closing socket: " + e.getMessage());
            }
        }
        Log.i(TAG, "Socket disconnected.");
        notifyMainThread(GameNetworkListener::onDisconnected);
    }

    private interface ListenerAction {
        void execute(GameNetworkListener listener);
    }

    private synchronized void notifyMainThread(ListenerAction action) {
        List<GameNetworkListener> snapshot = new ArrayList<>(listeners);
        mainHandler.post(() -> {
            for (GameNetworkListener l : snapshot) {
                try {
                    action.execute(l);
                } catch (Exception e) {
                    Log.e(TAG, "Error in listener callback", e);
                }
            }
        });
    }
}

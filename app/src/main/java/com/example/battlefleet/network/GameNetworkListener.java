package com.example.battlefleet.network;

import org.json.JSONObject;

/**
 * Callback listener interface for incoming TCP server events.
 * All callbacks are guaranteed to be invoked on the Android Main UI Thread.
 */
public interface GameNetworkListener {
    void onConnected();
    void onConnectionFailed(String errorMessage);
    void onDisconnected();

    void onRoomCreated(String roomId, String playerId, String playerName);
    void onRoomJoined(String roomId, String playerId, String opponentName);
    void onPlayerJoined(String opponentId, String opponentName);
    void onPlayerLeft(String message);
    void onOpponentReady(String opponentId, String opponentName);
    void onPlacementConfirmed(String roomId);
    void onGameStart(String roomId, String firstTurn, String currentTurn, String oppName);
    void onAttackResult(String attackerId, String defenderId, int row, int col,
                        String result, boolean sunk, String shipName, String nextTurn, int remainingShips);
    void onGameOver(String winnerId, String winnerName, String loserName,
                    String reason, int duration, int shots, int hits);
    void onChatMessage(String senderName, String message, long timestamp);
    void onOpponentDisconnected(String message);
    void onError(String code, String message);
}

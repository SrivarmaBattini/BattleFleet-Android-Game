package com.example.battlefleet.network;

/**
 * Convenient stub implementation of GameNetworkListener so components can
 * selectively override only relevant callbacks.
 */
public class SimpleGameNetworkListener implements GameNetworkListener {
    @Override public void onConnected() {}
    @Override public void onConnectionFailed(String errorMessage) {}
    @Override public void onDisconnected() {}
    @Override public void onRoomCreated(String roomId, String playerId, String playerName) {}
    @Override public void onRoomJoined(String roomId, String playerId, String opponentName) {}
    @Override public void onPlayerJoined(String opponentId, String opponentName) {}
    @Override public void onPlayerLeft(String message) {}
    @Override public void onOpponentReady(String opponentId, String opponentName) {}
    @Override public void onPlacementConfirmed(String roomId) {}
    @Override public void onGameStart(String roomId, String firstTurn, String currentTurn, String oppName) {}
    @Override public void onAttackResult(String attackerId, String defenderId, int row, int col,
                                         String result, boolean sunk, String shipName, String nextTurn, int remainingShips) {}
    @Override public void onGameOver(String winnerId, String winnerName, String loserName,
                                    String reason, int duration, int shots, int hits) {}
    @Override public void onChatMessage(String senderName, String message, long timestamp) {}
    @Override public void onOpponentDisconnected(String message) {}
    @Override public void onError(String code, String message) {}
}

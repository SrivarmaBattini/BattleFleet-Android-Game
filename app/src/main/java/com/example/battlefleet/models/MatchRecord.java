package com.example.battlefleet.models;

import java.io.Serializable;

/**
 * Data model representing a completed multiplayer battle recorded in local SQLite database.
 */
public class MatchRecord implements Serializable {
    private long id;
    private String roomCode;
    private String playerName;
    private String opponentName;
    private String result; // "WIN" or "LOSS"
    private String startedAt;
    private String completedAt;
    private int durationSeconds;
    private int shotsFired;
    private int hits;
    private double accuracy;

    public MatchRecord() {}

    public MatchRecord(long id, String roomCode, String playerName, String opponentName,
                       String result, String startedAt, String completedAt,
                       int durationSeconds, int shotsFired, int hits, double accuracy) {
        this.id = id;
        this.roomCode = roomCode;
        this.playerName = playerName;
        this.opponentName = opponentName;
        this.result = result;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.durationSeconds = durationSeconds;
        this.shotsFired = shotsFired;
        this.hits = hits;
        this.accuracy = accuracy;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getRoomCode() { return roomCode; }
    public void setRoomCode(String roomCode) { this.roomCode = roomCode; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public String getOpponentName() { return opponentName; }
    public void setOpponentName(String opponentName) { this.opponentName = opponentName; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }

    public String getCompletedAt() { return completedAt; }
    public void setCompletedAt(String completedAt) { this.completedAt = completedAt; }

    public int getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }

    public int getShotsFired() { return shotsFired; }
    public void setShotsFired(int shotsFired) { this.shotsFired = shotsFired; }

    public int getHits() { return hits; }
    public void setHits(int hits) { this.hits = hits; }

    public double getAccuracy() { return accuracy; }
    public void setAccuracy(double accuracy) { this.accuracy = accuracy; }

    public boolean isVictory() {
        return "WIN".equalsIgnoreCase(result);
    }

    public String getFormattedDuration() {
        int minutes = durationSeconds / 60;
        int seconds = durationSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}

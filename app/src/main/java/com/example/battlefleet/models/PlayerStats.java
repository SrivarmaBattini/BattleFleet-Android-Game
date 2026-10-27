package com.example.battlefleet.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregated player war record statistics calculated dynamically from the SQLite database.
 */
public class PlayerStats implements Serializable {
    private int totalMatches;
    private int wins;
    private int losses;
    private int totalShots;
    private int totalHits;
    private double overallAccuracy;
    private double winRate;
    private final List<String> recentResults; // e.g. ["WIN", "LOSS", "WIN"]

    public PlayerStats() {
        this.recentResults = new ArrayList<>();
    }

    public int getTotalMatches() { return totalMatches; }
    public void setTotalMatches(int totalMatches) { this.totalMatches = totalMatches; }

    public int getWins() { return wins; }
    public void setWins(int wins) { this.wins = wins; }

    public int getLosses() { return losses; }
    public void setLosses(int losses) { this.losses = losses; }

    public int getTotalShots() { return totalShots; }
    public void setTotalShots(int totalShots) { this.totalShots = totalShots; }

    public int getTotalHits() { return totalHits; }
    public void setTotalHits(int totalHits) { this.totalHits = totalHits; }

    public double getOverallAccuracy() { return overallAccuracy; }
    public void setOverallAccuracy(double overallAccuracy) { this.overallAccuracy = overallAccuracy; }

    public double getWinRate() { return winRate; }
    public void setWinRate(double winRate) { this.winRate = winRate; }

    public List<String> getRecentResults() { return recentResults; }
    public void addRecentResult(String res) { recentResults.add(res); }
}

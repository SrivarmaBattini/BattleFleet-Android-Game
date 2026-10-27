package com.example.battlefleet.models;

/**
 * State of an individual 10x10 cell on either the player's or opponent's board.
 */
public enum CellState {
    EMPTY,        // Open unexplored water
    SHIP,         // Friendly ship occupying cell (own board only)
    HIT,          // Torpedo struck a ship
    MISS,         // Torpedo struck empty water
    SUNK          // Entire ship destroyed
}

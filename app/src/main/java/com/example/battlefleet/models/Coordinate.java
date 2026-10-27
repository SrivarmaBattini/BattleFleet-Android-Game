package com.example.battlefleet.models;

import androidx.annotation.NonNull;
import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a discrete 0-indexed grid cell position (row 0..9, col 0..9).
 */
public class Coordinate implements Serializable {
    private final int row;
    private final int col;

    public Coordinate(int row, int col) {
        this.row = row;
        this.col = col;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public boolean isValid() {
        return row >= 0 && row < 10 && col >= 0 && col < 10;
    }

    /**
     * Converts coordinate to classic Battleship notation, e.g. (0,0) -> "A1", (3,5) -> "D6".
     */
    public String toNotation() {
        char rowLetter = (char) ('A' + row);
        return "" + rowLetter + (col + 1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Coordinate that = (Coordinate) o;
        return row == that.row && col == that.col;
    }

    @Override
    public int hashCode() {
        return Objects.hash(row, col);
    }

    @NonNull
    @Override
    public String toString() {
        return "(" + row + ", " + col + ")";
    }
}

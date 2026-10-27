package com.example.battlefleet.models;

import java.io.Serializable;

/**
 * Encapsulates the runtime display and logic state of one cell in a 10x10 board.
 */
public class BoardCell implements Serializable {
    private final int row;
    private final int col;
    private CellState state;
    private String shipName; // optional identifier if occupied

    public BoardCell(int row, int col) {
        this.row = row;
        this.col = col;
        this.state = CellState.EMPTY;
        this.shipName = null;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public CellState getState() {
        return state;
    }

    public void setState(CellState state) {
        this.state = state;
    }

    public String getShipName() {
        return shipName;
    }

    public void setShipName(String shipName) {
        this.shipName = shipName;
    }

    public boolean isAttacked() {
        return state == CellState.HIT || state == CellState.MISS || state == CellState.SUNK;
    }

    public void reset() {
        this.state = CellState.EMPTY;
        this.shipName = null;
    }
}

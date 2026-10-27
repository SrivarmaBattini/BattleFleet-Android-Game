package com.example.battlefleet.models;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Manages a 10x10 Battleship board, supporting fleet placement,
 * authoritative JSON serialization, hit/miss updates, and ship sinking detection.
 */
public class GameBoard implements Serializable {
    public static final int GRID_SIZE = 10;

    private final BoardCell[][] cells;
    private final Map<ShipType, Ship> fleet;
    private final Random random;

    public GameBoard() {
        this.cells = new BoardCell[GRID_SIZE][GRID_SIZE];
        this.fleet = new HashMap<>();
        this.random = new Random();

        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                cells[r][c] = new BoardCell(r, c);
            }
        }

        // Initialize standard fleet of 5 ships
        for (ShipType type : ShipType.values()) {
            fleet.put(type, new Ship(type));
        }
    }

    public BoardCell getCell(int row, int col) {
        if (row < 0 || row >= GRID_SIZE || col < 0 || col >= GRID_SIZE) {
            return null;
        }
        return cells[row][col];
    }

    public Ship getShip(ShipType type) {
        return fleet.get(type);
    }

    public List<Ship> getAllShips() {
        return new ArrayList<>(fleet.values());
    }

    public int getPlacedShipsCount() {
        int count = 0;
        for (Ship s : fleet.values()) {
            if (s.isPlaced()) count++;
        }
        return count;
    }

    public boolean isFleetFullyPlaced() {
        return getPlacedShipsCount() == fleet.size();
    }

    public int getRemainingShipsCount() {
        int count = 0;
        for (Ship s : fleet.values()) {
            if (s.isPlaced() && !s.isSunk()) count++;
        }
        return count;
    }

    /**
     * Checks whether a ship of the specified length can be placed starting at (startRow, startCol)
     * along the given orientation without extending past the boundaries or colliding with other placed ships.
     */
    public boolean canPlaceShip(int size, int startRow, int startCol, boolean horizontal, Ship currentShip) {
        if (startRow < 0 || startCol < 0) return false;

        if (horizontal) {
            if (startCol + size > GRID_SIZE || startRow >= GRID_SIZE) return false;
        } else {
            if (startRow + size > GRID_SIZE || startCol >= GRID_SIZE) return false;
        }

        for (int i = 0; i < size; i++) {
            int r = horizontal ? startRow : startRow + i;
            int c = horizontal ? startCol + i : startCol;
            BoardCell cell = cells[r][c];
            // Cell is occupied by another ship
            if (cell.getState() == CellState.SHIP) {
                if (currentShip == null || !currentShip.getName().equals(cell.getShipName())) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Places the specified ship at the given start coordinates and orientation.
     */
    public boolean placeShip(Ship ship, int startRow, int startCol, boolean horizontal) {
        if (!canPlaceShip(ship.getSize(), startRow, startCol, horizontal, ship)) {
            return false;
        }

        // Clear existing coordinates if ship was previously placed
        clearShipFromCells(ship);

        List<Coordinate> coords = new ArrayList<>();
        ship.setHorizontal(horizontal);

        for (int i = 0; i < ship.getSize(); i++) {
            int r = horizontal ? startRow : startRow + i;
            int c = horizontal ? startCol + i : startCol;
            cells[r][c].setState(CellState.SHIP);
            cells[r][c].setShipName(ship.getName());
            coords.add(new Coordinate(r, c));
        }

        ship.setCoordinates(coords);
        return true;
    }

    /**
     * Removes a ship from the grid cells.
     */
    public void removeShip(Ship ship) {
        clearShipFromCells(ship);
        ship.reset();
    }

    private void clearShipFromCells(Ship ship) {
        for (Coordinate coord : ship.getCoordinates()) {
            BoardCell cell = cells[coord.getRow()][coord.getCol()];
            if (ship.getName().equals(cell.getShipName())) {
                cell.reset();
            }
        }
    }

    /**
     * Clears all placed ships and resets cells to EMPTY.
     */
    public void resetFleet() {
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                cells[r][c].reset();
            }
        }
        for (Ship s : fleet.values()) {
            s.reset();
        }
    }

    /**
     * Automatically and randomly places all 5 ships validly without overlapping.
     */
    public void randomizeFleet() {
        resetFleet();
        for (ShipType type : ShipType.values()) {
            Ship ship = fleet.get(type);
            boolean placed = false;
            int attempts = 0;
            while (!placed && attempts < 500) {
                attempts++;
                boolean horizontal = random.nextBoolean();
                int maxR = horizontal ? GRID_SIZE : GRID_SIZE - ship.getSize();
                int maxC = horizontal ? GRID_SIZE - ship.getSize() : GRID_SIZE;
                int r = random.nextInt(maxR);
                int c = random.nextInt(maxC);

                if (canPlaceShip(ship.getSize(), r, c, horizontal, null)) {
                    placeShip(ship, r, c, horizontal);
                    placed = true;
                }
            }
        }
    }

    /**
     * Returns the coordinates preview for a ship at a potential tap location.
     */
    public List<Coordinate> getPlacementPreview(int size, int startRow, int startCol, boolean horizontal) {
        List<Coordinate> preview = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            int r = horizontal ? startRow : startRow + i;
            int c = horizontal ? startCol + i : startCol;
            if (r < GRID_SIZE && c < GRID_SIZE) {
                preview.add(new Coordinate(r, c));
            }
        }
        return preview;
    }

    /**
     * Registers an incoming attack coordinate, updating cell state and checking for ship damage/sinking.
     */
    public String registerAttack(int r, int c, boolean isHit, boolean isSunk, String shipName) {
        BoardCell cell = cells[r][c];
        if (isHit) {
            cell.setState(isSunk ? CellState.SUNK : CellState.HIT);
            // If we know which own ship was hit, update that ship
            for (Ship s : fleet.values()) {
                if (s.registerHit(new Coordinate(r, c))) {
                    if (s.isSunk()) {
                        for (Coordinate sc : s.getCoordinates()) {
                            cells[sc.getRow()][sc.getCol()].setState(CellState.SUNK);
                        }
                    }
                    break;
                }
            }
        } else {
            cell.setState(CellState.MISS);
        }
        return cell.getState().name();
    }

    /**
     * Exports placed fleet to the authoritative JSON structure required by the Python TCP server:
     * [
     *   {"name": "Carrier", "size": 5, "coords": [[r, c], ...]},
     *   ...
     * ]
     */
    public JSONArray exportShipsJson() throws JSONException {
        JSONArray shipsArray = new JSONArray();
        for (Ship ship : fleet.values()) {
            JSONObject shipObj = new JSONObject();
            shipObj.put("name", ship.getName());
            shipObj.put("size", ship.getSize());

            JSONArray coordsArray = new JSONArray();
            for (Coordinate coord : ship.getCoordinates()) {
                JSONArray pt = new JSONArray();
                pt.put(coord.getRow());
                pt.put(coord.getCol());
                coordsArray.put(pt);
            }
            shipObj.put("coords", coordsArray);
            shipsArray.put(shipObj);
        }
        return shipsArray;
    }
}

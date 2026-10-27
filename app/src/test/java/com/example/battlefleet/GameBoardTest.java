package com.example.battlefleet;

import com.example.battlefleet.models.Coordinate;
import com.example.battlefleet.models.GameBoard;
import com.example.battlefleet.models.Ship;
import com.example.battlefleet.models.ShipType;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests verifying authoritative game board logic, ship placement, collision detection,
 * fleet coverage, and JSON protocol serialization.
 */
public class GameBoardTest {

    private GameBoard board;

    @Before
    public void setUp() {
        board = new GameBoard();
    }

    @Test
    public void testInitialFleetIntegrity() {
        List<Ship> ships = board.getAllShips();
        assertEquals("Fleet must contain exactly 5 standard ships", 5, ships.size());

        assertEquals(5, board.getShip(ShipType.CARRIER).getSize());
        assertEquals(4, board.getShip(ShipType.BATTLESHIP).getSize());
        assertEquals(3, board.getShip(ShipType.CRUISER).getSize());
        assertEquals(3, board.getShip(ShipType.SUBMARINE).getSize());
        assertEquals(2, board.getShip(ShipType.DESTROYER).getSize());

        assertEquals(0, board.getPlacedShipsCount());
        assertFalse(board.isFleetFullyPlaced());
    }

    @Test
    public void testValidHorizontalAndVerticalPlacement() {
        Ship carrier = board.getShip(ShipType.CARRIER);
        boolean placedCarrier = board.placeShip(carrier, 0, 0, true);
        assertTrue("Carrier should be placed horizontally at (0,0)", placedCarrier);
        assertTrue(carrier.isPlaced());
        assertEquals(5, carrier.getCoordinates().size());

        Ship battleship = board.getShip(ShipType.BATTLESHIP);
        boolean placedBattleship = board.placeShip(battleship, 1, 0, false);
        assertTrue("Battleship should be placed vertically at (1,0)", placedBattleship);
        assertTrue(battleship.isPlaced());
        assertEquals(4, battleship.getCoordinates().size());

        assertEquals(2, board.getPlacedShipsCount());
    }

    @Test
    public void testOutOfBoundsPlacementRejected() {
        Ship carrier = board.getShip(ShipType.CARRIER); // size 5

        // Col 7 + size 5 = 12 > 10 (out of bounds)
        boolean placed = board.placeShip(carrier, 0, 7, true);
        assertFalse("Horizontal placement exceeding grid width must be rejected", placed);
        assertFalse(carrier.isPlaced());

        // Row 7 + size 5 = 12 > 10 (out of bounds)
        boolean placedV = board.placeShip(carrier, 7, 0, false);
        assertFalse("Vertical placement exceeding grid height must be rejected", placedV);
        assertFalse(carrier.isPlaced());
    }

    @Test
    public void testCollisionDetectionRejected() {
        Ship carrier = board.getShip(ShipType.CARRIER);
        assertTrue(board.placeShip(carrier, 2, 2, true)); // Occupies (2,2) to (2,6)

        Ship battleship = board.getShip(ShipType.BATTLESHIP);
        // Attempt to place vertically crossing (2,4)
        boolean collision = board.placeShip(battleship, 0, 4, false); // Would occupy (0,4), (1,4), (2,4), (3,4)
        assertFalse("Overlapping ship placement must be strictly rejected", collision);
        assertFalse(battleship.isPlaced());
    }

    @Test
    public void testRandomizeFleetValidAndComplete() {
        board.randomizeFleet();
        assertTrue("All 5 ships must be deployed after randomize", board.isFleetFullyPlaced());
        assertEquals(5, board.getPlacedShipsCount());

        int totalCells = 0;
        for (Ship s : board.getAllShips()) {
            totalCells += s.getCoordinates().size();
            assertTrue(s.isPlaced());
        }
        assertEquals("Total ship cells across all vessels must equal 17", 17, totalCells);
    }

    @Test
    public void testHitAndSinkingDetection() {
        Ship destroyer = board.getShip(ShipType.DESTROYER); // size 2
        assertTrue(board.placeShip(destroyer, 5, 5, true)); // (5,5), (5,6)

        assertFalse(destroyer.isSunk());

        // Register hit on first cell
        board.registerAttack(5, 5, true, false, destroyer.getName());
        assertFalse(destroyer.isSunk());
        assertEquals(1, destroyer.getHitsCount());

        // Register hit on second cell -> Sunk
        board.registerAttack(5, 6, true, true, destroyer.getName());
        assertTrue("Destroyer must be sunk after all cells are hit", destroyer.isSunk());
        assertEquals(2, destroyer.getHitsCount());
    }

    @Test
    public void testJsonExportFormat() throws Exception {
        board.randomizeFleet();
        JSONArray jsonArray = board.exportShipsJson();

        assertEquals(5, jsonArray.length());
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject obj = jsonArray.getJSONObject(i);
            assertTrue(obj.has("name"));
            assertTrue(obj.has("size"));
            assertTrue(obj.has("coords"));

            JSONArray coords = obj.getJSONArray("coords");
            assertEquals(obj.getInt("size"), coords.length());

            for (int j = 0; j < coords.length(); j++) {
                JSONArray pt = coords.getJSONArray(j);
                assertEquals(2, pt.length());
                int r = pt.getInt(0);
                int c = pt.getInt(1);
                assertTrue(r >= 0 && r < 10);
                assertTrue(c >= 0 && c < 10);
            }
        }
    }
}

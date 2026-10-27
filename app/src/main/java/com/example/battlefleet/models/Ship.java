package com.example.battlefleet.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents a naval ship deployed on the 10x10 grid.
 */
public class Ship implements Serializable {
    private final ShipType type;
    private boolean isHorizontal;
    private final List<Coordinate> coordinates;
    private final Set<Coordinate> hits;
    private boolean isPlaced;

    public Ship(ShipType type) {
        this.type = type;
        this.isHorizontal = true;
        this.coordinates = new ArrayList<>();
        this.hits = new HashSet<>();
        this.isPlaced = false;
    }

    public ShipType getType() {
        return type;
    }

    public String getName() {
        return type.getDisplayName();
    }

    public int getSize() {
        return type.getSize();
    }

    public boolean isHorizontal() {
        return isHorizontal;
    }

    public void setHorizontal(boolean horizontal) {
        isHorizontal = horizontal;
    }

    public List<Coordinate> getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(List<Coordinate> coords) {
        this.coordinates.clear();
        this.coordinates.addAll(coords);
        this.isPlaced = !coords.isEmpty();
    }

    public boolean isPlaced() {
        return isPlaced;
    }

    public void setPlaced(boolean placed) {
        isPlaced = placed;
    }

    public void reset() {
        coordinates.clear();
        hits.clear();
        isPlaced = false;
    }

    public boolean registerHit(Coordinate coord) {
        if (coordinates.contains(coord)) {
            hits.add(coord);
            return true;
        }
        return false;
    }

    public boolean isSunk() {
        return isPlaced && hits.size() >= coordinates.size() && !coordinates.isEmpty();
    }

    public int getHitsCount() {
        return hits.size();
    }

    public int getRemainingHealth() {
        return Math.max(0, coordinates.size() - hits.size());
    }
}

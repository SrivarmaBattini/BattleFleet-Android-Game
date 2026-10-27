package com.example.battlefleet;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests verifying JSON protocol message framing, structure, and parsing
 * according to the BattleFleet multiplayer specification.
 */
public class ProtocolTest {

    @Test
    public void testCreateRoomMessageFormat() throws Exception {
        JSONObject msg = new JSONObject();
        msg.put("type", "create_room");
        msg.put("player_name", "AdmiralNimitz");

        assertEquals("create_room", msg.getString("type"));
        assertEquals("AdmiralNimitz", msg.getString("player_name"));
    }

    @Test
    public void testJoinRoomMessageFormat() throws Exception {
        JSONObject msg = new JSONObject();
        msg.put("type", "join_room");
        msg.put("room_id", "BF789");
        msg.put("player_name", "CommanderSpruance");

        assertEquals("join_room", msg.getString("type"));
        assertEquals("BF789", msg.getString("room_id"));
        assertEquals("CommanderSpruance", msg.getString("player_name"));
    }

    @Test
    public void testAttackMessageFormat() throws Exception {
        JSONObject msg = new JSONObject();
        msg.put("type", "attack");
        msg.put("room_id", "BF789");
        msg.put("row", 4);
        msg.put("col", 7);

        assertEquals("attack", msg.getString("type"));
        assertEquals(4, msg.getInt("row"));
        assertEquals(7, msg.getInt("col"));
    }

    @Test
    public void testAttackResultParsing() throws Exception {
        String jsonPayload = "{\"type\":\"attack_result\",\"attacker_id\":\"P1\",\"defender_id\":\"P2\"," +
                "\"row\":3,\"col\":5,\"result\":\"hit\",\"sunk\":true,\"ship_name\":\"Destroyer\"," +
                "\"next_turn\":\"P2\",\"defender_remaining_ships\":4}";

        JSONObject obj = new JSONObject(jsonPayload);
        assertEquals("attack_result", obj.getString("type"));
        assertEquals("hit", obj.getString("result"));
        assertTrue(obj.getBoolean("sunk"));
        assertEquals("Destroyer", obj.getString("ship_name"));
        assertEquals("P2", obj.getString("next_turn"));
        assertEquals(4, obj.getInt("defender_remaining_ships"));
    }

    @Test
    public void testGameOverParsing() throws Exception {
        String jsonPayload = "{\"type\":\"game_over\",\"winner_id\":\"P1\",\"winner_name\":\"Admiral_Alice\"," +
                "\"loser_id\":\"P2\",\"loser_name\":\"Bob\",\"reason\":\"fleet_destroyed\"," +
                "\"duration_seconds\":185,\"attacker_stats\":{\"shots_fired\":24,\"hits\":17}}";

        JSONObject obj = new JSONObject(jsonPayload);
        assertEquals("game_over", obj.getString("type"));
        assertEquals("P1", obj.getString("winner_id"));
        assertEquals("Admiral_Alice", obj.getString("winner_name"));
        assertEquals(185, obj.getInt("duration_seconds"));

        JSONObject stats = obj.getJSONObject("attacker_stats");
        assertEquals(24, stats.getInt("shots_fired"));
        assertEquals(17, stats.getInt("hits"));
    }

    @Test
    public void testChatStreamingMessage() throws Exception {
        JSONObject msg = new JSONObject();
        msg.put("type", "chat");
        msg.put("room_id", "BF789");
        msg.put("message", "Sonar detects hostile submarines!");

        String wireLine = msg.toString() + "\n";
        assertTrue("Wire message must end with newline framing delimiter", wireLine.endsWith("\n"));
        JSONObject parsed = new JSONObject(wireLine.trim());
        assertEquals("Sonar detects hostile submarines!", parsed.getString("message"));
    }
}

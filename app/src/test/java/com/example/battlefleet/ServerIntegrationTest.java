package com.example.battlefleet;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/**
 * End-to-end integration test executing against the Python TCP Server.
 * Verifies live TCP handshake, room creation, pairing, fleet deployment, and attack processing.
 */
public class ServerIntegrationTest {

    private static Process serverProcess;
    private static final int TEST_PORT = 8899;

    @BeforeClass
    public static void startServer() throws Exception {
        File projectDir = new File(System.getProperty("user.dir"));
        if (!new File(projectDir, "server").exists() && projectDir.getParentFile() != null) {
            projectDir = projectDir.getParentFile();
        }
        File serverScript = new File(projectDir, "server/battleship_server.py");

        ProcessBuilder pb = new ProcessBuilder("python3", serverScript.getAbsolutePath(), String.valueOf(TEST_PORT));
        pb.redirectErrorStream(true);
        serverProcess = pb.start();

        // Brief delay to ensure socket bind
        Thread.sleep(800);
    }

    @AfterClass
    public static void stopServer() {
        if (serverProcess != null) {
            serverProcess.destroyForcibly();
        }
    }

    @Test
    public void testTwoPlayerMultiplayerLifecycle() throws Exception {
        // Player 1 (Host)
        Socket s1 = new Socket("127.0.0.1", TEST_PORT);
        BufferedReader r1 = new BufferedReader(new InputStreamReader(s1.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter w1 = new PrintWriter(new OutputStreamWriter(s1.getOutputStream(), StandardCharsets.UTF_8), true);

        // Player 2 (Guest)
        Socket s2 = new Socket("127.0.0.1", TEST_PORT);
        BufferedReader r2 = new BufferedReader(new InputStreamReader(s2.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter w2 = new PrintWriter(new OutputStreamWriter(s2.getOutputStream(), StandardCharsets.UTF_8), true);

        // 1. Host creates room
        w1.println("{\"type\":\"create_room\",\"player_name\":\"Alice\"}");
        String resp1 = r1.readLine();
        JSONObject roomCreated = new JSONObject(resp1);
        assertEquals("room_created", roomCreated.getString("type"));
        String roomId = roomCreated.getString("room_id");
        assertNotNull(roomId);

        // 2. Guest joins room
        w2.println("{\"type\":\"join_room\",\"room_id\":\"" + roomId + "\",\"player_name\":\"Bob\"}");
        String resp2 = r2.readLine();
        JSONObject roomJoined = new JSONObject(resp2);
        assertEquals("room_joined", roomJoined.getString("type"));

        // Host receives player_joined
        String hostNotif = r1.readLine();
        JSONObject playerJoined = new JSONObject(hostNotif);
        assertEquals("player_joined", playerJoined.getString("type"));
        assertEquals("Bob", playerJoined.getString("opponent_name"));

        // 3. Place ships
        String fleetPayload = "[{\"name\":\"Carrier\",\"size\":5,\"coords\":[[0,0],[0,1],[0,2],[0,3],[0,4]]}," +
                "{\"name\":\"Battleship\",\"size\":4,\"coords\":[[1,0],[1,1],[1,2],[1,3]]}," +
                "{\"name\":\"Cruiser\",\"size\":3,\"coords\":[[2,0],[2,1],[2,2]]}," +
                "{\"name\":\"Submarine\",\"size\":3,\"coords\":[[3,0],[3,1],[3,2]]}," +
                "{\"name\":\"Destroyer\",\"size\":2,\"coords\":[[4,0],[4,1]]}]";

        w1.println("{\"type\":\"place_ships\",\"room_id\":\"" + roomId + "\",\"ships\":" + fleetPayload + "}");
        JSONObject p1Confirm = new JSONObject(r1.readLine());
        assertEquals("placement_confirmed", p1Confirm.getString("type"));

        JSONObject oppReadyToP2 = new JSONObject(r2.readLine());
        assertEquals("opponent_ready", oppReadyToP2.getString("type"));

        w2.println("{\"type\":\"place_ships\",\"room_id\":\"" + roomId + "\",\"ships\":" + fleetPayload + "}");
        JSONObject p2Confirm = new JSONObject(r2.readLine());
        assertEquals("placement_confirmed", p2Confirm.getString("type"));

        JSONObject oppReadyToP1 = new JSONObject(r1.readLine());
        assertEquals("opponent_ready", oppReadyToP1.getString("type"));

        // Both receive game_start
        JSONObject gs1 = new JSONObject(r1.readLine());
        JSONObject gs2 = new JSONObject(r2.readLine());
        assertEquals("game_start", gs1.getString("type"));
        assertEquals("game_start", gs2.getString("type"));
        String firstTurn = gs1.getString("first_turn");
        assertNotNull(firstTurn);

        // 4. Fire attack from active player
        Socket activeSocket = firstTurn.startsWith("P1") ? s1 : s2;
        PrintWriter activeWriter = firstTurn.startsWith("P1") ? w1 : w2;

        activeWriter.println("{\"type\":\"attack\",\"room_id\":\"" + roomId + "\",\"row\":0,\"col\":0}");
        JSONObject ar1 = new JSONObject(r1.readLine());
        JSONObject ar2 = new JSONObject(r2.readLine());

        assertEquals("attack_result", ar1.getString("type"));
        assertEquals("hit", ar1.getString("result")); // (0,0) hits Carrier!

        s1.close();
        s2.close();
    }
}

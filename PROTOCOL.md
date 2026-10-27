# BattleFleet TCP Protocol Specification

## Framing
- **Transport**: Raw TCP socket (default port `8888`)
- **Encoding**: UTF-8
- **Message format**: One JSON object per line, terminated by `\n`
- **Client reads**: `BufferedReader.readLine()` — one complete message per call
- **Client writes**: `PrintWriter.println()` + `flush()`

---

## Message Types

### Keep-Alive

| Direction | Type | Fields |
|---|---|---|
| Client → Server | `ping` | — |
| Server → Client | `pong` | `timestamp` |

### Room Management

| Direction | Type | Key Fields |
|---|---|---|
| Client → Server | `create_room` | `player_name` |
| Server → Client | `room_created` | `room_id`, `player_id`, `player_name`, `is_host` |
| Client → Server | `join_room` | `room_id`, `player_name` |
| Server → Joiner | `room_joined` | `room_id`, `player_id`, `opponent_id`, `opponent_name`, `is_host` |
| Server → Host | `player_joined` | `room_id`, `opponent_id`, `opponent_name` |
| Client → Server | `leave_room` | `room_id` |
| Server → Opponent | `player_left` | `room_id`, `leaver_name`, `message` |

### Fleet Deployment

| Direction | Type | Key Fields |
|---|---|---|
| Client → Server | `place_ships` | `room_id`, `ships[]` (each: `name`, `size`, `coords[]`) |
| Server → Client | `placement_confirmed` | `room_id`, `status` |
| Server → Opponent | `opponent_ready` | `opponent_id`, `opponent_name` |
| Server → Both | `game_start` | `room_id`, `first_turn`, `current_turn`, `player1`, `player2` |

**Ship placement rules** (enforced by server):
- 5 ships: Carrier(5), Battleship(4), Cruiser(3), Submarine(3), Destroyer(2) = 17 cells
- Straight line (horizontal or vertical), contiguous, non-overlapping, within 0–9

### Battle

| Direction | Type | Key Fields |
|---|---|---|
| Client → Server | `attack` | `room_id`, `row`, `col` |
| Server → Both | `attack_result` | `attacker_id`, `defender_id`, `row`, `col`, `result` (hit/miss), `sunk`, `ship_name`, `next_turn`, `defender_remaining_ships` |
| Server → Both | `game_over` | `winner_id`, `winner_name`, `loser_id`, `loser_name`, `reason`, `duration_seconds`, `attacker_stats`, `defender_stats` |

### Chat

| Direction | Type | Key Fields |
|---|---|---|
| Client → Server | `chat` | `room_id`, `message` |
| Server → Both | `chat_message` | `sender_id`, `sender_name`, `message`, `timestamp` |

### Errors & Disconnects

| Direction | Type | Key Fields |
|---|---|---|
| Server → Client | `error` / `placement_error` | `code`, `message` |
| Server → Client | `opponent_disconnected` | `room_id`, `disconnected_player`, `message` |

**Error codes**: `ROOM_NOT_FOUND`, `ROOM_FULL`, `ROOM_IN_PROGRESS`

---

## Connection Lifecycle

```
Client                              Server
  |--- TCP Connect (port 8888) ------->|
  |--- ping -------------------------->|
  |<-- pong ----------------------------|
  |--- create_room / join_room ------->|
  |<-- room_created / room_joined -----|
  |--- place_ships ------------------->|
  |<-- placement_confirmed ------------|
  |<-- game_start (when both ready) ---|
  |--- attack ------------------------>|
  |<-- attack_result ------------------|
  |    (repeat until fleet destroyed)   |
  |<-- game_over ----------------------|
```

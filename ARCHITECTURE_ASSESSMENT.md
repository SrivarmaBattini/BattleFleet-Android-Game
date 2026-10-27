# Architecture Assessment: BattleFleet

## Overview
BattleFleet is a multiplayer Battleship game consisting of:
1. **Android Client** — Native Java/XML app handling UI, local persistence, and TCP networking.
2. **Python TCP Server** — Authoritative game server managing rooms, ship validation, turn logic, and win conditions.

Communication uses **newline-delimited JSON over raw TCP sockets** (port 8888).

---

## Client Architecture

| Layer | Package | Responsibility |
|---|---|---|
| **Presentation** | `activities/`, `views/`, `adapters/` | 9 Activity screens, custom Canvas `BoardView`, RecyclerView/ListView adapters |
| **Networking** | `network/` | `TcpGameClient` singleton — background socket I/O, JSON framing, UI-thread dispatch via `Handler(Looper.getMainLooper())` |
| **Game Logic** | `models/` | `GameBoard` (10×10 grid), `Ship`, `BoardCell`, `CellState`, `Coordinate`, `ShipType` |
| **Persistence** | `database/`, `utils/` | `SQLiteOpenHelper` + async `MatchHistoryRepository` (Cursor/ContentValues), `PreferencesManager` (SharedPreferences) |

### Key Design Decisions
- **Singleton `TcpGameClient`**: Survives Activity transitions; uses listener pattern (`GameNetworkListener`) so each screen registers/unregisters callbacks in `onResume`/`onPause`.
- **Server-authoritative model**: The client never determines hit/miss/sunk — it sends coordinates and renders whatever the server responds with.
- **`GameBoard` is `Serializable`**: Passed between `ShipPlacementActivity` → `BattleActivity` via Intent extras.

---

## Server Architecture

The Python server (`server/battleship_server.py`) is a multithreaded TCP socket server:

- **Threading model**: One daemon thread per connected client; `threading.Lock` protects shared room state.
- **Room system**: `GameRoom` objects hold two `PlayerConnection` references, game state (`LOBBY` → `PLACEMENT` → `BATTLE` → `FINISHED`), and current turn.
- **Ship validation**: Enforces exact fleet composition (5 ships, 17 cells), straight-line contiguous placement, no overlaps, 0–9 bounds.
- **Attack processing**: Validates turn order, rejects duplicate coordinates, evaluates hits against defender's hidden grid, detects sinking, and triggers `game_over` when all 17 cells are hit.
- **Disconnect handling**: Notifies opponent and cleans up empty rooms.

---

## Data Flow Summary

```
Android Client                          Python Server
     |                                       |
     |-- create_room / join_room ----------->|  Room pairing
     |<- room_created / player_joined -------|
     |                                       |
     |-- place_ships (JSON fleet) ---------->|  Authoritative validation
     |<- placement_confirmed / game_start ---|
     |                                       |
     |-- attack (row, col) ---------------->|  Hit/miss evaluation
     |<- attack_result / game_over ----------|
     |                                       |
     |-- chat (message) ------------------->|  Relay to opponent
     |<- chat_message ----------------------|
```

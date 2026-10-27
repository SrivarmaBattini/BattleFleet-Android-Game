# BattleFleet — Multiplayer Battleship Game (Android + Python Server)

BattleFleet is a multiplayer naval strategy game built with **Java/XML** on Android, powered by a **Python TCP socket server**. Two players connect over a network, deploy fleets on 10×10 grids, and take turns firing torpedoes until one fleet is completely destroyed.

---

## Technology Stack

| Layer | Technology |
|---|---|
| **Platform** | Android (SDK 36, Min SDK 24) |
| **Language** | Java 11 |
| **UI** | XML Layouts + Custom Canvas `BoardView` |
| **Networking** | Raw TCP Sockets, Newline-delimited JSON |
| **Server** | Python 3 (multithreaded TCP) |
| **Database** | SQLite via `SQLiteOpenHelper` |
| **Preferences** | Android `SharedPreferences` |
| **Build** | Gradle (Kotlin DSL) |

---

## Project Structure

```
BATTLEFLEET/
├── app/src/main/java/com/example/battlefleet/
│   ├── MainActivity.java              # HQ Dashboard
│   ├── activities/                     # All game screens (Splash, Settings, Lobby, Placement, Battle, Result, History, Stats, Rules)
│   ├── adapters/                       # RecyclerView & ListView adapters
│   ├── database/                       # SQLiteOpenHelper + Repository (CRUD)
│   ├── models/                         # GameBoard, Ship, BoardCell, CellState, Coordinate, MatchRecord, PlayerStats, ShipType
│   ├── network/                        # TcpGameClient (singleton), GameNetworkListener interface
│   ├── utils/                          # NotificationUtils, PreferencesManager
│   └── views/                          # Custom Canvas BoardView
├── server/
│   ├── battleship_server.py            # Authoritative TCP server
│   ├── battleship_tk_client.py         # Desktop Tkinter test client
│   └── test_server.py                  # Protocol test script
├── ARCHITECTURE_ASSESSMENT.md
├── PROTOCOL.md
└── README.md
```

---

## How to Run

### 1. Start the Server
```bash
python server/battleship_server.py 8888
```

### 2. Build & Install the Android App
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 3. Play
- **Emulator**: Default server address is `10.0.2.2:8888` (routes to host machine).
- **Wi-Fi**: Set the server IP in Settings to your host machine's local IP.
- Player 1 taps **Host Room** → gets a 5-character room code.
- Player 2 taps **Join Room** → enters the code.
- Both place ships → battle begins → take turns firing until a fleet is sunk.

---

## Key Features
- **Server-authoritative**: All placement validation, turn enforcement, hit/miss/sink logic runs on the Python server.
- **Real-time chat**: In-game tactical messaging between players.
- **Match history**: SQLite-backed combat log with filtering by result and date.
- **Career statistics**: Win rate, accuracy, recent form tracker.
- **Notifications**: Local notification on match completion.
- **Share results**: Implicit Intent sharesheet for battle reports.

---

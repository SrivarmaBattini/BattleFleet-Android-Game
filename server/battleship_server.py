#!/usr/bin/env python3
"""
BattleFleet - Authoritative Python TCP Socket Server for Multiplayer Battleship.
Provides room management, matchmaking, authoritative ship placement validation,
turn-based attack processing, hit/miss/sinking evaluation, and win condition detection.
Uses newline-delimited JSON over TCP.
"""

import socket
import threading
import json
import random
import string
import time
import sys
import logging

logging.basicConfig(
    level=logging.INFO,
    format='[%(asctime)s] [%(levelname)s] %(threadName)s: %(message)s',
    datefmt='%H:%M:%S'
)
logger = logging.getLogger("BattleFleetServer")

STANDARD_SHIPS = {
    "Carrier": 5,
    "Battleship": 4,
    "Cruiser": 3,
    "Submarine": 3,
    "Destroyer": 2
}
GRID_SIZE = 10
TOTAL_SHIP_CELLS = sum(STANDARD_SHIPS.values())  # 17 cells


class PlayerConnection:
    def __init__(self, client_socket, address):
        self.socket = client_socket
        self.address = address
        self.player_id = None
        self.player_name = "Player"
        self.room_id = None
        self.is_ready = False
        self.ships = []  # list of {"name": str, "size": int, "coords": [(r, c), ...], "hits": set()}
        self.placed_coords = set()
        self.attacks_made = set()  # set of (r, c)
        self.lock = threading.Lock()
        self.active = True

    def send_json(self, data):
        """Send a JSON object followed by newline to the client."""
        with self.lock:
            if not self.active:
                return
            try:
                msg_str = json.dumps(data) + "\n"
                self.socket.sendall(msg_str.encode('utf-8'))
                logger.debug("Sent to %s: %s", self.player_name, data.get("type"))
            except Exception as e:
                logger.warning("Error sending to %s: %e", self.player_name, e)
                self.active = False


class GameRoom:
    def __init__(self, room_id):
        self.room_id = room_id
        self.host = None       # PlayerConnection
        self.guest = None      # PlayerConnection
        self.state = "LOBBY"   # LOBBY, PLACEMENT, BATTLE, FINISHED
        self.current_turn = None # player_id
        self.winner = None
        self.start_time = None
        self.lock = threading.Lock()

    def get_player(self, player_id):
        if self.host and self.host.player_id == player_id:
            return self.host
        if self.guest and self.guest.player_id == player_id:
            return self.guest
        return None

    def get_opponent(self, player_id):
        if self.host and self.host.player_id == player_id:
            return self.guest
        if self.guest and self.guest.player_id == player_id:
            return self.host
        return None

    def is_full(self):
        return self.host is not None and self.guest is not None

    def broadcast(self, data):
        """Send message to all players in this room."""
        if self.host:
            self.host.send_json(data)
        if self.guest:
            self.guest.send_json(data)


class BattleFleetServer:
    def __init__(self, host='0.0.0.0', port=8888):
        self.host = host
        self.port = port
        self.rooms = {}  # room_id -> GameRoom
        self.rooms_lock = threading.Lock()
        self.server_socket = None
        self.running = False

    def generate_room_id(self):
        with self.rooms_lock:
            for _ in range(100):
                code = ''.join(random.choices(string.ascii_uppercase + string.digits, k=5))
                if code not in self.rooms:
                    return code
            return str(random.randint(10000, 99999))

    def start(self):
        self.server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            self.server_socket.bind((self.host, self.port))
            self.server_socket.listen(10)
            self.running = True
            logger.info("==================================================")
            logger.info("BattleFleet TCP Server listening on %s:%d", self.host, self.port)
            logger.info("Framing: Newline-delimited JSON (\\n)")
            logger.info("==================================================")
        except Exception as e:
            logger.error("Failed to bind server socket on %s:%d - %s", self.host, self.port, e)
            sys.exit(1)

        try:
            while self.running:
                client_sock, addr = self.server_socket.accept()
                logger.info("Accepted connection from %s:%d", addr[0], addr[1])
                conn = PlayerConnection(client_sock, addr)
                client_thread = threading.Thread(
                    target=self.handle_client,
                    args=(conn,),
                    daemon=True,
                    name=f"Client-{addr[0]}:{addr[1]}"
                )
                client_thread.start()
        except KeyboardInterrupt:
            logger.info("Server shutting down via KeyboardInterrupt...")
        finally:
            self.stop()

    def stop(self):
        self.running = False
        if self.server_socket:
            try:
                self.server_socket.close()
            except Exception:
                pass
        logger.info("Server stopped.")

    def handle_client(self, conn):
        buffer = ""
        try:
            while self.running and conn.active:
                chunk = conn.socket.recv(4096)
                if not chunk:
                    logger.info("Client %s disconnected cleanly (EOF)", conn.player_name)
                    break
                buffer += chunk.decode('utf-8', errors='replace')
                while '\n' in buffer:
                    line, buffer = buffer.split('\n', 1)
                    line = line.strip()
                    if not line:
                        continue
                    try:
                        msg = json.loads(line)
                        self.process_message(conn, msg)
                    except json.JSONDecodeError as jde:
                        logger.warning("JSON decode error from %s: %s (raw line: %s)", conn.player_name, jde, line)
                        conn.send_json({"type": "error", "message": "Invalid JSON format"})
        except ConnectionResetError:
            logger.info("Connection reset by client: %s", conn.player_name)
        except Exception as e:
            logger.error("Exception in client loop for %s: %s", conn.player_name, e)
        finally:
            self.cleanup_client(conn)

    def process_message(self, conn, msg):
        msg_type = msg.get("type", "")
        logger.info("Received from %s [%s]: type=%s", conn.player_name, conn.player_id, msg_type)

        if msg_type == "ping":
            conn.send_json({"type": "pong", "timestamp": int(time.time() * 1000)})
            return

        elif msg_type == "create_room":
            self.handle_create_room(conn, msg)

        elif msg_type == "join_room":
            self.handle_join_room(conn, msg)

        elif msg_type == "leave_room":
            self.handle_leave_room(conn, msg)

        elif msg_type == "place_ships":
            self.handle_place_ships(conn, msg)

        elif msg_type == "player_ready":
            self.handle_player_ready(conn, msg)

        elif msg_type == "attack":
            self.handle_attack(conn, msg)

        elif msg_type == "chat":
            self.handle_chat(conn, msg)

        elif msg_type == "rematch_request":
            self.handle_rematch_request(conn, msg)

        else:
            conn.send_json({"type": "error", "message": f"Unknown message type: {msg_type}"})

    def handle_create_room(self, conn, msg):
        player_name = msg.get("player_name", "HostPlayer").strip()
        if not player_name:
            player_name = "HostPlayer"
        conn.player_name = player_name
        conn.player_id = "P1_" + ''.join(random.choices(string.digits, k=4))

        room_id = self.generate_room_id()
        room = GameRoom(room_id)
        room.host = conn
        conn.room_id = room_id

        with self.rooms_lock:
            self.rooms[room_id] = room

        logger.info("Room %s created by %s (%s)", room_id, conn.player_name, conn.player_id)
        conn.send_json({
            "type": "room_created",
            "room_id": room_id,
            "player_id": conn.player_id,
            "player_name": conn.player_name,
            "is_host": True
        })

    def handle_join_room(self, conn, msg):
        room_id = msg.get("room_id", "").strip().upper()
        player_name = msg.get("player_name", "GuestPlayer").strip()
        if not player_name:
            player_name = "GuestPlayer"
        conn.player_name = player_name
        conn.player_id = "P2_" + ''.join(random.choices(string.digits, k=4))

        with self.rooms_lock:
            room = self.rooms.get(room_id)

        if not room:
            conn.send_json({
                "type": "error",
                "code": "ROOM_NOT_FOUND",
                "message": f"Room '{room_id}' not found."
            })
            return

        with room.lock:
            if room.is_full():
                conn.send_json({
                    "type": "error",
                    "code": "ROOM_FULL",
                    "message": f"Room '{room_id}' is already full."
                })
                return
            if room.state != "LOBBY":
                conn.send_json({
                    "type": "error",
                    "code": "ROOM_IN_PROGRESS",
                    "message": "Game already in progress in this room."
                })
                return

            room.guest = conn
            conn.room_id = room_id

        logger.info("Player %s (%s) joined room %s", conn.player_name, conn.player_id, room_id)

        # Notify joiner
        conn.send_json({
            "type": "room_joined",
            "room_id": room_id,
            "player_id": conn.player_id,
            "player_name": conn.player_name,
            "opponent_id": room.host.player_id,
            "opponent_name": room.host.player_name,
            "is_host": False
        })

        # Notify host
        room.host.send_json({
            "type": "player_joined",
            "room_id": room_id,
            "opponent_id": conn.player_id,
            "opponent_name": conn.player_name
        })

    def handle_leave_room(self, conn, msg):
        room_id = conn.room_id
        if not room_id:
            return
        with self.rooms_lock:
            room = self.rooms.get(room_id)
        if not room:
            return

        with room.lock:
            opp = room.get_opponent(conn.player_id)
            if opp:
                opp.send_json({
                    "type": "player_left",
                    "room_id": room_id,
                    "leaver_name": conn.player_name,
                    "message": f"{conn.player_name} left the room."
                })
            # Disband or reset room
            if room.host == conn:
                room.host = None
            elif room.guest == conn:
                room.guest = None

        conn.room_id = None
        conn.is_ready = False
        conn.ships = []
        conn.placed_coords = set()
        conn.attacks_made = set()

        conn.send_json({"type": "room_left", "status": "ok"})
        self.cleanup_empty_room(room_id)

    def validate_ships(self, ships_data):
        """Authoritatively validate standard Battleship fleet placement."""
        if not isinstance(ships_data, list):
            return False, "Ships data must be a list"

        if len(ships_data) != len(STANDARD_SHIPS):
            return False, f"Expected {len(STANDARD_SHIPS)} ships, got {len(ships_data)}"

        expected_names = dict(STANDARD_SHIPS)
        all_coords = set()

        for ship in ships_data:
            name = ship.get("name")
            coords = ship.get("coords")
            if not name or name not in expected_names:
                return False, f"Invalid or unexpected ship name: {name}"
            expected_size = expected_names[name]
            if not isinstance(coords, list) or len(coords) != expected_size:
                return False, f"Ship {name} must have {expected_size} coordinates, got {len(coords) if isinstance(coords, list) else 0}"

            del expected_names[name]

            # Validate each coordinate is within 0..9 and integer
            ship_cells = []
            for pt in coords:
                if not (isinstance(pt, list) and len(pt) == 2):
                    return False, f"Invalid coordinate format in {name}: {pt}"
                r, c = pt[0], pt[1]
                if not (0 <= r < GRID_SIZE and 0 <= c < GRID_SIZE):
                    return False, f"Coordinate ({r}, {c}) for {name} is out of bounds (0-9)"
                cell = (r, c)
                if cell in all_coords:
                    return False, f"Ship overlap at coordinate ({r}, {c})"
                all_coords.add(cell)
                ship_cells.append(cell)

            # Validate contiguous and straight line (horizontal or vertical)
            rows = [pt[0] for pt in ship_cells]
            cols = [pt[1] for pt in ship_cells]
            is_horizontal = (len(set(rows)) == 1)
            is_vertical = (len(set(cols)) == 1)

            if not (is_horizontal or is_vertical):
                return False, f"Ship {name} must be placed in a straight line"

            if is_horizontal:
                cols.sort()
                for i in range(len(cols) - 1):
                    if cols[i+1] - cols[i] != 1:
                        return False, f"Ship {name} coordinates must be contiguous"
            else:
                rows.sort()
                for i in range(len(rows) - 1):
                    if rows[i+1] - rows[i] != 1:
                        return False, f"Ship {name} coordinates must be contiguous"

        if len(all_coords) != TOTAL_SHIP_CELLS:
            return False, f"Total fleet coverage must be {TOTAL_SHIP_CELLS} cells"

        return True, "Valid"

    def handle_place_ships(self, conn, msg):
        room_id = conn.room_id
        if not room_id:
            conn.send_json({"type": "error", "message": "Not in any room"})
            return

        with self.rooms_lock:
            room = self.rooms.get(room_id)
        if not room:
            conn.send_json({"type": "error", "message": "Room not found"})
            return

        ships_data = msg.get("ships", [])
        is_valid, reason = self.validate_ships(ships_data)
        if not is_valid:
            logger.warning("Ship placement invalid for %s: %s", conn.player_name, reason)
            conn.send_json({
                "type": "placement_error",
                "message": reason
            })
            return

        # Store validated ships on player connection
        conn.ships = []
        conn.placed_coords = set()
        for s in ships_data:
            coords_tuples = [tuple(pt) for pt in s["coords"]]
            conn.ships.append({
                "name": s["name"],
                "size": len(coords_tuples),
                "coords": coords_tuples,
                "hits": set()
            })
            conn.placed_coords.update(coords_tuples)

        conn.is_ready = True
        logger.info("Player %s in room %s confirmed ship placement", conn.player_name, room_id)

        conn.send_json({
            "type": "placement_confirmed",
            "room_id": room_id,
            "status": "ok"
        })

        # Notify opponent that this player is ready
        with room.lock:
            opp = room.get_opponent(conn.player_id)
            if opp:
                opp.send_json({
                    "type": "opponent_ready",
                    "opponent_id": conn.player_id,
                    "opponent_name": conn.player_name
                })

            # If both players are placed and ready, start the battle!
            if room.host and room.guest and room.host.is_ready and room.guest.is_ready:
                room.state = "BATTLE"
                room.start_time = time.time()
                # Randomize or designate host as first turn
                first_turn = random.choice([room.host.player_id, room.guest.player_id])
                room.current_turn = first_turn

                logger.info("Both players ready in room %s. Starting battle! First turn: %s", room_id, first_turn)
                room.broadcast({
                    "type": "game_start",
                    "room_id": room_id,
                    "first_turn": first_turn,
                    "current_turn": first_turn,
                    "player1": {"id": room.host.player_id, "name": room.host.player_name},
                    "player2": {"id": room.guest.player_id, "name": room.guest.player_name}
                })

    def handle_player_ready(self, conn, msg):
        # Fallback or confirmation of readiness
        if not conn.ships or len(conn.placed_coords) != TOTAL_SHIP_CELLS:
            conn.send_json({"type": "error", "message": "Cannot be ready before valid ship placement"})
            return
        conn.is_ready = True

    def handle_attack(self, conn, msg):
        room_id = conn.room_id
        if not room_id:
            conn.send_json({"type": "error", "message": "Not in any room"})
            return

        with self.rooms_lock:
            room = self.rooms.get(room_id)
        if not room:
            conn.send_json({"type": "error", "message": "Room not found"})
            return

        with room.lock:
            if room.state != "BATTLE":
                conn.send_json({"type": "error", "message": "Battle is not in progress"})
                return

            if room.current_turn != conn.player_id:
                conn.send_json({"type": "error", "message": "Not your turn"})
                return

            try:
                row = int(msg.get("row"))
                col = int(msg.get("col"))
            except (ValueError, TypeError):
                conn.send_json({"type": "error", "message": "Invalid attack coordinates"})
                return

            if not (0 <= row < GRID_SIZE and 0 <= col < GRID_SIZE):
                conn.send_json({"type": "error", "message": "Attack coordinates out of bounds"})
                return

            target = (row, col)
            if target in conn.attacks_made:
                conn.send_json({"type": "error", "message": "Cell already attacked"})
                return

            conn.attacks_made.add(target)
            opp = room.get_opponent(conn.player_id)
            if not opp:
                conn.send_json({"type": "error", "message": "Opponent not found"})
                return

            # Authoritatively determine Hit or Miss against opponent ships
            is_hit = False
            sunk_ship = None
            hit_ship_obj = None

            for s in opp.ships:
                if target in s["coords"]:
                    is_hit = True
                    s["hits"].add(target)
                    hit_ship_obj = s
                    if len(s["hits"]) == len(s["coords"]):
                        sunk_ship = s["name"]
                    break

            # Calculate opponent remaining ships
            opp_remaining_ships = sum(1 for s in opp.ships if len(s["hits"]) < len(s["coords"]))

            # Check Game Over condition
            total_opp_hits = sum(len(s["hits"]) for s in opp.ships)
            is_game_over = (total_opp_hits >= TOTAL_SHIP_CELLS) or (opp_remaining_ships == 0)

            # Determine next turn: alternate turns if game not over
            if is_game_over:
                room.state = "FINISHED"
                room.winner = conn.player_id
                next_turn = None
            else:
                next_turn = opp.player_id
                room.current_turn = next_turn

            attack_result_msg = {
                "type": "attack_result",
                "room_id": room_id,
                "attacker_id": conn.player_id,
                "defender_id": opp.player_id,
                "row": row,
                "col": col,
                "result": "hit" if is_hit else "miss",
                "sunk": (sunk_ship is not None),
                "ship_name": sunk_ship if sunk_ship else "",
                "next_turn": next_turn,
                "defender_remaining_ships": opp_remaining_ships
            }

            logger.info("Attack in room %s by %s at (%d, %d) -> %s (sunk: %s, next: %s)",
                        room_id, conn.player_name, row, col, "HIT" if is_hit else "MISS",
                        sunk_ship, next_turn)

            room.broadcast(attack_result_msg)

            if is_game_over:
                duration_sec = int(time.time() - (room.start_time or time.time()))
                game_over_msg = {
                    "type": "game_over",
                    "room_id": room_id,
                    "winner_id": conn.player_id,
                    "winner_name": conn.player_name,
                    "loser_id": opp.player_id,
                    "loser_name": opp.player_name,
                    "reason": "fleet_destroyed",
                    "duration_seconds": duration_sec,
                    "attacker_stats": {
                        "shots_fired": len(conn.attacks_made),
                        "hits": sum(len(s["hits"]) for s in opp.ships)
                    },
                    "defender_stats": {
                        "shots_fired": len(opp.attacks_made),
                        "hits": sum(len(s["hits"]) for s in conn.ships)
                    }
                }
                logger.info("GAME OVER in room %s! Winner: %s (%s)", room_id, conn.player_name, conn.player_id)
                room.broadcast(game_over_msg)

    def handle_chat(self, conn, msg):
        room_id = conn.room_id or msg.get("room_id")
        if not room_id:
            logger.warning("[CHAT] Ignored: no room_id found for connection %s", conn.player_id)
            return
        if not conn.room_id:
            conn.room_id = room_id

        with self.rooms_lock:
            room = self.rooms.get(room_id)
        if not room:
            logger.warning("[CHAT] Ignored: room %s not active", room_id)
            return

        chat_text = str(msg.get("message", "")).strip()
        if not chat_text or len(chat_text) > 200:
            return

        sender_name = conn.player_name or "Player"
        sender_id = conn.player_id or "unknown"

        chat_msg = {
            "type": "chat_message",
            "room_id": room_id,
            "sender_id": sender_id,
            "sender_name": sender_name,
            "message": chat_text,
            "timestamp": int(time.time() * 1000)
        }
        logger.info("[CHAT] Room %s | %s: %s", room_id, sender_name, chat_text)
        with room.lock:
            opp = room.get_opponent(conn.player_id)
            if opp:
                opp.send_json(chat_msg)
            # Also echo back confirmation to sender
            conn.send_json(chat_msg)

    def handle_rematch_request(self, conn, msg):
        room_id = conn.room_id
        if not room_id:
            return
        with self.rooms_lock:
            room = self.rooms.get(room_id)
        if not room:
            return

        with room.lock:
            conn.is_ready = False
            conn.ships = []
            conn.placed_coords = set()
            conn.attacks_made = set()
            opp = room.get_opponent(conn.player_id)
            if opp:
                opp.send_json({
                    "type": "rematch_offered",
                    "offered_by": conn.player_name
                })
            room.state = "PLACEMENT"
            room.broadcast({
                "type": "rematch_start",
                "room_id": room_id
            })

    def cleanup_client(self, conn):
        conn.active = False
        room_id = conn.room_id
        if room_id:
            with self.rooms_lock:
                room = self.rooms.get(room_id)
            if room:
                with room.lock:
                    opp = room.get_opponent(conn.player_id)
                    if opp and opp.active:
                        opp.send_json({
                            "type": "opponent_disconnected",
                            "room_id": room_id,
                            "disconnected_player": conn.player_name,
                            "message": f"Opponent {conn.player_name} disconnected."
                        })
                    if room.host == conn:
                        room.host = None
                    elif room.guest == conn:
                        room.guest = None
                self.cleanup_empty_room(room_id)

        try:
            conn.socket.close()
        except Exception:
            pass

    def cleanup_empty_room(self, room_id):
        with self.rooms_lock:
            room = self.rooms.get(room_id)
            if room and room.host is None and room.guest is None:
                del self.rooms[room_id]
                logger.info("Room %s cleaned up (empty)", room_id)


if __name__ == "__main__":
    port = 8888
    if len(sys.argv) > 1:
        try:
            port = int(sys.argv[1])
        except ValueError:
            pass
    server = BattleFleetServer(host='0.0.0.0', port=port)
    server.start()

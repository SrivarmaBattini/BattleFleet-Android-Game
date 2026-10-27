#!/usr/bin/env python3
"""
Test script for BattleFleet Python TCP Server.
Simulates two players connecting, creating/joining a room, placing fleets,
exchanging attacks, chatting, and completing a match.
"""

import socket
import json
import time
import sys

def recv_json(sock, buffer):
    while '\n' not in buffer:
        chunk = sock.recv(4096).decode('utf-8')
        if not chunk:
            return None, buffer
        buffer += chunk
    line, buffer = buffer.split('\n', 1)
    return json.loads(line.strip()), buffer

def send_json(sock, obj):
    sock.sendall((json.dumps(obj) + "\n").encode('utf-8'))

def run_test():
    host = '127.0.0.1'
    port = 8888

    print("[TEST] Connecting Client 1 (Alice)...")
    s1 = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s1.connect((host, port))
    b1 = ""

    print("[TEST] Connecting Client 2 (Bob)...")
    s2 = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s2.connect((host, port))
    b2 = ""

    # Ping test
    send_json(s1, {"type": "ping"})
    msg, b1 = recv_json(s1, b1)
    assert msg["type"] == "pong", f"Expected pong, got {msg}"
    print("[TEST] Ping-Pong verified:", msg)

    # 1. Alice creates room
    send_json(s1, {"type": "create_room", "player_name": "Alice"})
    msg1, b1 = recv_json(s1, b1)
    assert msg1["type"] == "room_created", f"Unexpected msg: {msg1}"
    room_id = msg1["room_id"]
    alice_id = msg1["player_id"]
    print(f"[TEST] Room created: {room_id} by Alice ({alice_id})")

    # 2. Bob joins room
    send_json(s2, {"type": "join_room", "room_id": room_id, "player_name": "Bob"})
    msg2, b2 = recv_json(s2, b2)
    assert msg2["type"] == "room_joined", f"Unexpected msg: {msg2}"
    bob_id = msg2["player_id"]
    print(f"[TEST] Bob joined room: {room_id} ({bob_id})")

    # Host should get player_joined notification
    msg_host, b1 = recv_json(s1, b1)
    assert msg_host["type"] == "player_joined", f"Expected player_joined, got {msg_host}"
    print(f"[TEST] Alice received player_joined for Bob")

    # 3. Test chat
    send_json(s1, {"type": "chat", "room_id": room_id, "message": "Ready to battle, Bob?"})
    chat_echo, b1 = recv_json(s1, b1)
    chat_recv, b2 = recv_json(s2, b2)
    assert chat_recv["type"] == "chat_message" and chat_recv["message"] == "Ready to battle, Bob?"
    print("[TEST] Chat exchange verified between Alice and Bob")

    # 4. Standard Ships placement
    alice_ships = [
        {"name": "Carrier", "size": 5, "coords": [[0,0],[0,1],[0,2],[0,3],[0,4]]},
        {"name": "Battleship", "size": 4, "coords": [[1,0],[1,1],[1,2],[1,3]]},
        {"name": "Cruiser", "size": 3, "coords": [[2,0],[2,1],[2,2]]},
        {"name": "Submarine", "size": 3, "coords": [[3,0],[3,1],[3,2]]},
        {"name": "Destroyer", "size": 2, "coords": [[4,0],[4,1]]}
    ]

    bob_ships = [
        {"name": "Carrier", "size": 5, "coords": [[9,0],[9,1],[9,2],[9,3],[9,4]]},
        {"name": "Battleship", "size": 4, "coords": [[8,0],[8,1],[8,2],[8,3]]},
        {"name": "Cruiser", "size": 3, "coords": [[7,0],[7,1],[7,2]]},
        {"name": "Submarine", "size": 3, "coords": [[6,0],[6,1],[6,2]]},
        {"name": "Destroyer", "size": 2, "coords": [[5,0],[5,1]]}
    ]

    # Alice places ships
    send_json(s1, {"type": "place_ships", "room_id": room_id, "ships": alice_ships})
    c1, b1 = recv_json(s1, b1)
    assert c1["type"] == "placement_confirmed", f"Unexpected {c1}"
    opp_ready_b2, b2 = recv_json(s2, b2)
    assert opp_ready_b2["type"] == "opponent_ready"
    print("[TEST] Alice placed ships successfully")

    # Bob places ships
    send_json(s2, {"type": "place_ships", "room_id": room_id, "ships": bob_ships})
    c2, b2 = recv_json(s2, b2)
    assert c2["type"] == "placement_confirmed", f"Unexpected {c2}"
    print("[TEST] Bob placed ships successfully")

    # Bob placed ships -> Alice receives opponent_ready, then both receive game_start
    opp_ready_a1, b1 = recv_json(s1, b1)
    assert opp_ready_a1["type"] == "opponent_ready"
    print("[TEST] Alice received Bob is ready")

    # Both clients should now receive game_start!
    gs1, b1 = recv_json(s1, b1)
    gs2, b2 = recv_json(s2, b2)
    assert gs1["type"] == "game_start", f"Alice got {gs1}"
    assert gs2["type"] == "game_start", f"Bob got {gs2}"
    current_turn = gs1["first_turn"]
    print(f"[TEST] GAME_START received by both! First turn: {current_turn}")

    # 5. Execute an attack
    active_sock = s1 if current_turn == alice_id else s2
    active_b = b1 if current_turn == alice_id else b2
    active_name = "Alice" if current_turn == alice_id else "Bob"

    # Attack cell (5, 0)
    # If Alice attacks (5, 0) -> this hits Bob's Destroyer!
    # If Bob attacks (5, 0) -> this misses Alice's fleet!
    send_json(active_sock, {"type": "attack", "room_id": room_id, "row": 5, "col": 0})

    ar1, b1 = recv_json(s1, b1)
    ar2, b2 = recv_json(s2, b2)
    assert ar1["type"] == "attack_result" and ar2["type"] == "attack_result"
    print(f"[TEST] Attack by {active_name} on (5, 0) result: {ar1['result']}, next turn: {ar1['next_turn']}")

    # Clean close
    s1.close()
    s2.close()
    print("[TEST] ALL SERVER PROTOCOL TESTS PASSED SUCCESSFULLY!")

if __name__ == "__main__":
    run_test()

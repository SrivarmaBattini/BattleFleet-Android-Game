#!/usr/bin/env python3
"""
Reference Desktop Tkinter Battleship Client for BattleFleet TCP Server.
Allows testing and demonstration of the multiplayer protocol from a desktop GUI.
"""

import tkinter as tk
from tkinter import messagebox, ttk
import socket
import threading
import json
import time

class BattleshipTkClient:
    def __init__(self, root):
        self.root = root
        self.root.title("BattleFleet - Desktop Tactical Client")
        self.root.geometry("820x680")
        self.root.configure(bg="#0B132B")

        self.sock = None
        self.buffer = ""
        self.running = False
        self.my_id = None
        self.my_name = "DesktopCommander"
        self.room_id = None
        self.is_my_turn = False
        self.opponent_name = "Opponent"

        # Fleet definition
        self.ships_def = [
            ("Carrier", 5),
            ("Battleship", 4),
            ("Cruiser", 3),
            ("Submarine", 3),
            ("Destroyer", 2)
        ]
        self.placed_ships = []
        self.placed_coords = set()

        self.setup_ui()

    def setup_ui(self):
        # Header / Connection Frame
        top_frame = tk.Frame(self.root, bg="#1C2541", padx=10, pady=8)
        top_frame.pack(fill=tk.X, padx=10, pady=5)

        tk.Label(top_frame, text="Commander:", fg="#E0E1DD", bg="#1C2541", font=("Helvetica", 10, "bold")).pack(side=tk.LEFT)
        self.entry_name = tk.Entry(top_frame, width=12)
        self.entry_name.insert(0, "Admiral_Tk")
        self.entry_name.pack(side=tk.LEFT, padx=5)

        tk.Label(top_frame, text="Host:", fg="#E0E1DD", bg="#1C2541").pack(side=tk.LEFT, padx=(10, 2))
        self.entry_host = tk.Entry(top_frame, width=12)
        self.entry_host.insert(0, "127.0.0.1")
        self.entry_host.pack(side=tk.LEFT)

        tk.Label(top_frame, text="Port:", fg="#E0E1DD", bg="#1C2541").pack(side=tk.LEFT, padx=(10, 2))
        self.entry_port = tk.Entry(top_frame, width=6)
        self.entry_port.insert(0, "8888")
        self.entry_port.pack(side=tk.LEFT)

        self.btn_connect = tk.Button(top_frame, text="Connect", bg="#48CAE4", fg="#000", command=self.connect_server)
        self.btn_connect.pack(side=tk.LEFT, padx=10)

        self.lbl_status = tk.Label(top_frame, text="Status: Disconnected", fg="#FFB703", bg="#1C2541")
        self.lbl_status.pack(side=tk.RIGHT)

        # Room actions frame
        room_frame = tk.Frame(self.root, bg="#1C2541", padx=10, pady=5)
        room_frame.pack(fill=tk.X, padx=10, pady=2)

        self.btn_create = tk.Button(room_frame, text="Create Room", state=tk.DISABLED, command=self.create_room)
        self.btn_create.pack(side=tk.LEFT, padx=5)

        tk.Label(room_frame, text="Room Code:", fg="#E0E1DD", bg="#1C2541").pack(side=tk.LEFT, padx=(15, 2))
        self.entry_room = tk.Entry(room_frame, width=8)
        self.entry_room.pack(side=tk.LEFT)

        self.btn_join = tk.Button(room_frame, text="Join Room", state=tk.DISABLED, command=self.join_room)
        self.btn_join.pack(side=tk.LEFT, padx=5)

        self.btn_quick_place = tk.Button(room_frame, text="Deploy Fleet", state=tk.DISABLED, bg="#06D6A0", fg="#000", command=self.deploy_default_fleet)
        self.btn_quick_place.pack(side=tk.RIGHT, padx=5)

        # Grids Container
        boards_frame = tk.Frame(self.root, bg="#0B132B")
        boards_frame.pack(fill=tk.BOTH, expand=True, padx=10, pady=5)

        # Left: Opponent Radar (Attack Grid)
        radar_box = tk.LabelFrame(boards_frame, text="TARGETING RADAR (OPPONENT)", fg="#48CAE4", bg="#1C2541", font=("Helvetica", 10, "bold"))
        radar_box.pack(side=tk.LEFT, fill=tk.BOTH, expand=True, padx=5)

        self.opp_buttons = {}
        for r in range(10):
            for c in range(10):
                btn = tk.Button(radar_box, text="·", width=2, height=1, bg="#0B132B", fg="#64DFDF",
                                command=lambda row=r, col=c: self.on_radar_click(row, col))
                btn.grid(row=r, column=c, padx=1, pady=1)
                self.opp_buttons[(r, c)] = btn

        # Right: My Fleet Grid
        fleet_box = tk.LabelFrame(boards_frame, text="OWN FLEET STATUS", fg="#06D6A0", bg="#1C2541", font=("Helvetica", 10, "bold"))
        fleet_box.pack(side=tk.RIGHT, fill=tk.BOTH, expand=True, padx=5)

        self.my_buttons = {}
        for r in range(10):
            for c in range(10):
                btn = tk.Button(fleet_box, text="·", width=2, height=1, bg="#0B132B", fg="#8D99AE", state=tk.DISABLED)
                btn.grid(row=r, column=c, padx=1, pady=1)
                self.my_buttons[(r, c)] = btn

        # Turn & Log bar
        bottom_frame = tk.Frame(self.root, bg="#1C2541", padx=10, pady=5)
        bottom_frame.pack(fill=tk.X, padx=10, pady=5)

        self.lbl_turn = tk.Label(bottom_frame, text="Turn: Waiting to start", fg="#FFB703", bg="#1C2541", font=("Helvetica", 11, "bold"))
        self.lbl_turn.pack(side=tk.LEFT)

        # Comms frame
        comms_frame = tk.Frame(self.root, bg="#1C2541", padx=10, pady=4)
        comms_frame.pack(fill=tk.X, padx=10, pady=2)
        tk.Label(comms_frame, text="Tactical Comms:", fg="#48CAE4", bg="#1C2541", font=("Helvetica", 9, "bold")).pack(side=tk.LEFT)
        self.entry_chat = tk.Entry(comms_frame, width=35, bg="#0B132B", fg="#FFF", insertbackground="#FFF")
        self.entry_chat.pack(side=tk.LEFT, padx=6)
        self.entry_chat.bind("<Return>", self.send_chat)
        self.btn_send_chat = tk.Button(comms_frame, text="Transmit", bg="#48CAE4", fg="#000", font=("Helvetica", 9, "bold"), command=self.send_chat)
        self.btn_send_chat.pack(side=tk.LEFT)

        # Log box
        self.txt_log = tk.Text(self.root, height=4, bg="#0B132B", fg="#E0E1DD", font=("Courier", 9))
        self.txt_log.pack(fill=tk.X, padx=10, pady=5)

    def log(self, text):
        self.txt_log.insert(tk.END, f"{text}\n")
        self.txt_log.see(tk.END)

    def connect_server(self):
        host = self.entry_host.get().strip()
        port = int(self.entry_port.get().strip())
        self.my_name = self.entry_name.get().strip() or "Admiral_Tk"

        try:
            self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.sock.connect((host, port))
            self.running = True
            self.lbl_status.config(text=f"Connected to {host}:{port}", fg="#06D6A0")
            self.btn_create.config(state=tk.NORMAL)
            self.btn_join.config(state=tk.NORMAL)
            self.btn_connect.config(state=tk.DISABLED)
            self.log(f"[SYS] Connected to server {host}:{port}")

            threading.Thread(target=self.receive_loop, daemon=True).start()
        except Exception as e:
            messagebox.showerror("Connection Error", str(e))
            self.lbl_status.config(text="Connection Failed", fg="#EF476F")

    def send_json(self, data):
        if self.sock and self.running:
            try:
                line = json.dumps(data) + "\n"
                self.sock.sendall(line.encode('utf-8'))
            except Exception as e:
                self.log(f"[ERR] Send error: {e}")

    def receive_loop(self):
        while self.running:
            try:
                chunk = self.sock.recv(4096).decode('utf-8')
                if not chunk:
                    break
                self.buffer += chunk
                while '\n' in self.buffer:
                    line, self.buffer = self.buffer.split('\n', 1)
                    if line.strip():
                        msg = json.loads(line.strip())
                        self.root.after(0, self.handle_message, msg)
            except Exception as e:
                break
        self.root.after(0, lambda: self.lbl_status.config(text="Disconnected", fg="#EF476F"))

    def handle_message(self, msg):
        mtype = msg.get("type")
        self.log(f"[RECV] {mtype}")

        if mtype == "room_created":
            self.room_id = msg["room_id"]
            self.my_id = msg["player_id"]
            self.lbl_status.config(text=f"Room: {self.room_id} (Host)", fg="#06D6A0")
            self.btn_quick_place.config(state=tk.NORMAL)
            self.log(f"[ROOM] Created room code: {self.room_id}. Share this with Player 2!")

        elif mtype == "room_joined":
            self.room_id = msg["room_id"]
            self.my_id = msg["player_id"]
            self.opponent_name = msg.get("opponent_name", "Host")
            self.lbl_status.config(text=f"Room: {self.room_id} vs {self.opponent_name}", fg="#06D6A0")
            self.btn_quick_place.config(state=tk.NORMAL)
            self.log(f"[ROOM] Joined room {self.room_id} against {self.opponent_name}!")

        elif mtype == "player_joined":
            self.opponent_name = msg.get("opponent_name", "Guest")
            self.lbl_status.config(text=f"Room: {self.room_id} vs {self.opponent_name}", fg="#06D6A0")
            self.log(f"[ROOM] Opponent {self.opponent_name} joined!")

        elif mtype == "placement_confirmed":
            self.log("[FLEET] Fleet deployment confirmed by server! Waiting for opponent...")
            self.lbl_turn.config(text="Fleet Deployed. Waiting for opponent...")

        elif mtype == "game_start":
            self.log("[BATTLE] GAME COMMENCED! All fleets deployed!")
            turn = msg.get("current_turn")
            self.update_turn(turn)

        elif mtype == "attack_result":
            attacker = msg.get("attacker_id")
            r, c = msg.get("row"), msg.get("col")
            res = msg.get("result")
            sunk = msg.get("sunk")
            ship_name = msg.get("ship_name", "")
            next_t = msg.get("next_turn")

            if attacker == self.my_id:
                # We attacked opponent
                btn = self.opp_buttons.get((r, c))
                if res == "hit":
                    btn.config(text="✕", bg="#EF476F", fg="#FFF")
                    self.log(f"[RADAR] DIRECT HIT at ({r},{c})! {f'{ship_name} SUNK!' if sunk else ''}")
                else:
                    btn.config(text="○", bg="#118AB2", fg="#FFF")
                    self.log(f"[RADAR] MISS at ({r},{c})")
            else:
                # Opponent attacked us
                btn = self.my_buttons.get((r, c))
                if res == "hit":
                    btn.config(text="💥", bg="#EF476F", fg="#FFF")
                    self.log(f"[FLEET] ALERT! Our ship was HIT at ({r},{c})! {f'{ship_name} SUNK!' if sunk else ''}")
                else:
                    btn.config(text="💧", bg="#118AB2", fg="#FFF")
                    self.log(f"[FLEET] Enemy fire missed at ({r},{c})")

            self.update_turn(next_t)

        elif mtype == "game_over":
            winner_id = msg.get("winner_id")
            winner_name = msg.get("winner_name")
            if winner_id == self.my_id:
                messagebox.showinfo("VICTORY", f"CONGRATULATIONS!\nYou have destroyed the enemy fleet!\nWinner: {winner_name}")
            else:
                messagebox.showinfo("DEFEAT", f"YOUR FLEET WAS DESTROYED!\nWinner: {winner_name}")
            self.lbl_turn.config(text=f"Match Ended. Winner: {winner_name}", fg="#FFD166")

        elif mtype == "chat_message":
            sender = msg.get("sender_name", "Player")
            text = msg.get("message", "")
            self.log(f"[COMMS] {sender}: {text}")

        elif mtype == "error":
            messagebox.showwarning("Server Message", msg.get("message", "Error"))

    def send_chat(self, event=None):
        text = self.entry_chat.get().strip()
        if not text or not self.sock:
            return
        self.send_json({"type": "chat", "room_id": self.room_id, "message": text})
        self.entry_chat.delete(0, tk.END)

    def update_turn(self, turn_id):
        self.is_my_turn = (turn_id == self.my_id)
        if self.is_my_turn:
            self.lbl_turn.config(text="YOUR TURN - FIRE ON TARGETING RADAR!", fg="#06D6A0")
        else:
            self.lbl_turn.config(text="OPPONENT'S TURN - WAITING...", fg="#FFB703")

    def create_room(self):
        self.send_json({"type": "create_room", "player_name": self.my_name})

    def join_room(self):
        code = self.entry_room.get().strip().upper()
        if not code:
            messagebox.showwarning("Input Error", "Please enter room code")
            return
        self.send_json({"type": "join_room", "room_id": code, "player_name": self.my_name})

    def deploy_default_fleet(self):
        ships = [
            {"name": "Carrier", "size": 5, "coords": [[0,0],[0,1],[0,2],[0,3],[0,4]]},
            {"name": "Battleship", "size": 4, "coords": [[2,0],[2,1],[2,2],[2,3]]},
            {"name": "Cruiser", "size": 3, "coords": [[4,0],[4,1],[4,2]]},
            {"name": "Submarine", "size": 3, "coords": [[6,0],[6,1],[6,2]]},
            {"name": "Destroyer", "size": 2, "coords": [[8,0],[8,1]]}
        ]
        # Render on own grid
        for s in ships:
            for r, c in s["coords"]:
                self.my_buttons[(r, c)].config(text="■", bg="#073B4C", fg="#06D6A0")
        self.send_json({"type": "place_ships", "room_id": self.room_id, "ships": ships})
        self.btn_quick_place.config(state=tk.DISABLED)

    def on_radar_click(self, r, c):
        if not self.is_my_turn:
            messagebox.showwarning("Turn Error", "It is not your turn yet!")
            return
        btn = self.opp_buttons[(r, c)]
        if btn["text"] != "·":
            messagebox.showwarning("Target Error", "Cell already targeted!")
            return
        self.send_json({"type": "attack", "room_id": self.room_id, "row": r, "col": c})


if __name__ == "__main__":
    root = tk.Tk()
    app = BattleshipTkClient(root)
    root.mainloop()

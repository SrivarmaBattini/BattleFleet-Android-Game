package com.example.battlefleet.activities;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.battlefleet.R;
import com.example.battlefleet.models.Coordinate;
import com.example.battlefleet.models.GameBoard;
import com.example.battlefleet.models.Ship;
import com.example.battlefleet.models.ShipType;
import com.example.battlefleet.network.SimpleGameNetworkListener;
import com.example.battlefleet.network.TcpGameClient;
import com.example.battlefleet.views.BoardView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.List;


public class ShipPlacementActivity extends AppCompatActivity {

    private BoardView boardView;
    private GameBoard gameBoard;
    private TextView tvCounter;
    private TextView tvOrientation;
    private MaterialButton btnConfirm;

    private MaterialButton btnCarrier;
    private MaterialButton btnBattleship;
    private MaterialButton btnCruiser;
    private MaterialButton btnSubmarine;
    private MaterialButton btnDestroyer;

    private ShipType selectedShipType = ShipType.CARRIER;
    private boolean isHorizontal = true;

    private TcpGameClient tcpClient;
    private AlertDialog waitingDialog;

    private final SimpleGameNetworkListener placementListener = new SimpleGameNetworkListener() {
        @Override
        public void onPlacementConfirmed(String roomId) {
            showWaitingDialog("Fleet deployed! Waiting for opponent to position ships...");
        }

        @Override
        public void onOpponentReady(String opponentId, String opponentName) {
            Toast.makeText(ShipPlacementActivity.this, opponentName + " has confirmed fleet deployment!", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onGameStart(String roomId, String firstTurn, String currentTurn, String oppName) {
            if (waitingDialog != null && waitingDialog.isShowing()) {
                waitingDialog.dismiss();
            }

            Intent intent = new Intent(ShipPlacementActivity.this, BattleActivity.class);
            intent.putExtra(BattleActivity.EXTRA_GAME_BOARD, gameBoard);
            intent.putExtra(BattleActivity.EXTRA_FIRST_TURN, firstTurn);
            intent.putExtra(BattleActivity.EXTRA_CURRENT_TURN, currentTurn);
            intent.putExtra(BattleActivity.EXTRA_OPPONENT_NAME, oppName);
            intent.putExtra(BattleActivity.EXTRA_ROOM_ID, roomId);
            startActivity(intent);
            finish();
        }

        @Override
        public void onError(String code, String message) {
            if (waitingDialog != null && waitingDialog.isShowing()) {
                waitingDialog.dismiss();
            }
            Toast.makeText(ShipPlacementActivity.this, "Placement error: " + message, Toast.LENGTH_LONG).show();
        }

        @Override
        public void onOpponentDisconnected(String message) {
            if (waitingDialog != null && waitingDialog.isShowing()) {
                waitingDialog.dismiss();
            }
            Toast.makeText(ShipPlacementActivity.this, message, Toast.LENGTH_LONG).show();
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ship_placement);

        tcpClient = TcpGameClient.getInstance();
        gameBoard = new GameBoard();

        initViews();
        updateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        tcpClient.addListener(placementListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        tcpClient.removeListener(placementListener);
    }

    private void initViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar_placement);
        toolbar.setNavigationOnClickListener(v -> confirmExit());

        boardView = findViewById(R.id.board_placement);
        boardView.setMode(BoardView.Mode.PLACEMENT);
        boardView.setBoard(gameBoard);

        tvCounter = findViewById(R.id.tv_placement_counter);
        tvOrientation = findViewById(R.id.tv_orientation_badge);
        btnConfirm = findViewById(R.id.btn_confirm_deployment);

        btnCarrier = findViewById(R.id.btn_ship_carrier);
        btnBattleship = findViewById(R.id.btn_ship_battleship);
        btnCruiser = findViewById(R.id.btn_ship_cruiser);
        btnSubmarine = findViewById(R.id.btn_ship_submarine);
        btnDestroyer = findViewById(R.id.btn_ship_destroyer);

        // Ship select buttons
        btnCarrier.setOnClickListener(v -> selectShip(ShipType.CARRIER));
        btnBattleship.setOnClickListener(v -> selectShip(ShipType.BATTLESHIP));
        btnCruiser.setOnClickListener(v -> selectShip(ShipType.CRUISER));
        btnSubmarine.setOnClickListener(v -> selectShip(ShipType.SUBMARINE));
        btnDestroyer.setOnClickListener(v -> selectShip(ShipType.DESTROYER));

        // Orientation toggle
        findViewById(R.id.btn_rotate_orientation).setOnClickListener(v -> {
            isHorizontal = !isHorizontal;
            tvOrientation.setText(isHorizontal ? "HORIZONTAL" : "VERTICAL");
        });

        // Auto-Deploy (Randomize)
        findViewById(R.id.btn_randomize_placement).setOnClickListener(v -> {
            gameBoard.randomizeFleet();
            boardView.invalidate();
            updateUI();
            Toast.makeText(this, "Fleet auto-deployed in tactical formation!", Toast.LENGTH_SHORT).show();
        });

        // Reset Fleet
        findViewById(R.id.btn_reset_placement).setOnClickListener(v -> {
            gameBoard.resetFleet();
            boardView.invalidate();
            updateUI();
        });

        // Grid tap listener
        boardView.setOnCellClickListener((row, col) -> handleGridCellClick(row, col));

        // Confirm & Deploy
        btnConfirm.setOnClickListener(v -> submitFleetPlacement());
    }

    private void selectShip(ShipType type) {
        this.selectedShipType = type;
        updateShipButtons();
    }

    private void handleGridCellClick(int row, int col) {
        Ship ship = gameBoard.getShip(selectedShipType);
        if (ship == null) return;

        boolean canPlace = gameBoard.canPlaceShip(ship.getSize(), row, col, isHorizontal, ship);
        if (canPlace) {
            gameBoard.placeShip(ship, row, col, isHorizontal);
            boardView.invalidate();
            advanceToNextUnplacedShip();
            updateUI();
        } else {
            Toast.makeText(this, "Cannot place " + ship.getName() + " here (collision or out of bounds)", Toast.LENGTH_SHORT).show();
        }
    }

    private void advanceToNextUnplacedShip() {
        for (ShipType type : ShipType.values()) {
            Ship s = gameBoard.getShip(type);
            if (s != null && !s.isPlaced()) {
                selectedShipType = type;
                break;
            }
        }
    }

    private void updateUI() {
        int placed = gameBoard.getPlacedShipsCount();
        tvCounter.setText(placed + " / 5 SHIPS POSITIONED");
        btnConfirm.setEnabled(placed == 5);

        updateShipButtons();
    }

    private void updateShipButtons() {
        highlightShipButton(btnCarrier, ShipType.CARRIER);
        highlightShipButton(btnBattleship, ShipType.BATTLESHIP);
        highlightShipButton(btnCruiser, ShipType.CRUISER);
        highlightShipButton(btnSubmarine, ShipType.SUBMARINE);
        highlightShipButton(btnDestroyer, ShipType.DESTROYER);
    }

    private void highlightShipButton(MaterialButton btn, ShipType type) {
        Ship s = gameBoard.getShip(type);
        boolean isPlaced = (s != null && s.isPlaced());
        boolean isSelected = (selectedShipType == type);

        if (isSelected) {
            btn.setStrokeColorResource(R.color.gold_rank);
            btn.setTextColor(ContextCompat.getColor(this, R.color.gold_rank));
        } else if (isPlaced) {
            btn.setStrokeColorResource(R.color.ship_deployed);
            btn.setTextColor(ContextCompat.getColor(this, R.color.ship_deployed));
        } else {
            btn.setStrokeColorResource(R.color.radar_cyan);
            btn.setTextColor(ContextCompat.getColor(this, R.color.radar_cyan));
        }
    }

    private void submitFleetPlacement() {
        if (!gameBoard.isFleetFullyPlaced()) {
            Toast.makeText(this, "All 5 ships must be deployed before battle!", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONArray shipsArray = gameBoard.exportShipsJson();
            tcpClient.sendPlaceShips(null, shipsArray);
            showWaitingDialog("Submitting fleet coordinates to server...");
        } catch (JSONException e) {
            Toast.makeText(this, "JSON error exporting fleet: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showWaitingDialog(String msg) {
        if (waitingDialog != null && waitingDialog.isShowing()) {
            waitingDialog.setMessage(msg);
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Fleet Ready");
        builder.setMessage(msg);
        builder.setCancelable(false);
        waitingDialog = builder.create();
        waitingDialog.show();
    }

    private void confirmExit() {
        new AlertDialog.Builder(this)
                .setTitle("Abort Placement")
                .setMessage("Exit fleet placement and return to lobby?")
                .setPositiveButton("Exit", (dialog, which) -> finish())
                .setNegativeButton("Stay", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        confirmExit();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (waitingDialog != null && waitingDialog.isShowing()) {
            waitingDialog.dismiss();
        }
    }
}

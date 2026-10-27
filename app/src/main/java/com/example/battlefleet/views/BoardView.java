package com.example.battlefleet.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.battlefleet.R;
import com.example.battlefleet.models.BoardCell;
import com.example.battlefleet.models.CellState;
import com.example.battlefleet.models.Coordinate;
import com.example.battlefleet.models.GameBoard;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom Canvas-rendered 10x10 Battleship tactical grid view.
 * Provides high-performance, responsive graphics for Fleet Deployment and Target Radar.
 */
public class BoardView extends View {

    public enum Mode {
        PLACEMENT,       // Interactively placing own ships
        TARGET_RADAR,    // Attacking opponent fleet (fog of war)
        FLEET_DEFENSE    // Viewing own fleet status under attack
    }

    public interface OnCellClickListener {
        void onCellClick(int row, int col);
    }

    private static final int GRID_SIZE = 10;
    private Mode mode = Mode.TARGET_RADAR;
    private GameBoard board;
    private OnCellClickListener cellClickListener;

    // Preview state during placement
    private final List<Coordinate> previewCoords = new ArrayList<>();
    private boolean isPreviewValid = true;

    // Paints
    private Paint gridLinePaint;
    private Paint cellBgPaint;
    private Paint shipPaint;
    private Paint hitPaint;
    private Paint missPaint;
    private Paint previewPaint;
    private Paint labelPaint;
    private Paint markerPaint;

    // Dimensions
    private float cellSize = 0f;
    private float labelMargin = 0f;
    private final RectF cellRect = new RectF();

    public BoardView(Context context) {
        super(context);
        init(context);
    }

    public BoardView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public BoardView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        gridLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridLinePaint.setColor(ContextCompat.getColor(context, R.color.grid_cell_stroke));
        gridLinePaint.setStyle(Paint.Style.STROKE);
        gridLinePaint.setStrokeWidth(2f);

        cellBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cellBgPaint.setColor(ContextCompat.getColor(context, R.color.grid_cell_bg));
        cellBgPaint.setStyle(Paint.Style.FILL);

        shipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shipPaint.setColor(ContextCompat.getColor(context, R.color.ship_deployed));
        shipPaint.setStyle(Paint.Style.FILL);

        hitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hitPaint.setColor(ContextCompat.getColor(context, R.color.hit_red));
        hitPaint.setStyle(Paint.Style.FILL);

        missPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        missPaint.setColor(ContextCompat.getColor(context, R.color.miss_blue));
        missPaint.setStyle(Paint.Style.FILL);

        previewPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        previewPaint.setStyle(Paint.Style.FILL);

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(ContextCompat.getColor(context, R.color.text_muted));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(26f);

        markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        markerPaint.setColor(Color.WHITE);
        markerPaint.setTextAlign(Paint.Align.CENTER);
        markerPaint.setFakeBoldText(true);
    }

    public void setBoard(GameBoard board) {
        this.board = board;
        invalidate();
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        invalidate();
    }

    public void setOnCellClickListener(OnCellClickListener listener) {
        this.cellClickListener = listener;
    }

    public void setPlacementPreview(List<Coordinate> coords, boolean isValid) {
        previewCoords.clear();
        if (coords != null) {
            previewCoords.addAll(coords);
        }
        this.isPreviewValid = isValid;
        invalidate();
    }

    public void clearPreview() {
        previewCoords.clear();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        // Make it square based on the smaller dimension
        int size = Math.min(width, height);
        if (size == 0) size = width > 0 ? width : height;
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        labelMargin = Math.max(22f, w * 0.05f);
        labelPaint.setTextSize(labelMargin * 0.75f);
        markerPaint.setTextSize((w - labelMargin) / (GRID_SIZE * 1.5f));
        cellSize = (w - labelMargin) / GRID_SIZE;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (cellSize <= 0) return;

        // Draw Row & Column Headers (A..J and 1..10)
        for (int i = 0; i < GRID_SIZE; i++) {
            // Column numbers along the top
            String colStr = String.valueOf(i + 1);
            float cx = labelMargin + (i + 0.5f) * cellSize;
            float cy = labelMargin * 0.75f;
            canvas.drawText(colStr, cx, cy, labelPaint);

            // Row letters along the left
            String rowStr = String.valueOf((char) ('A' + i));
            float rx = labelMargin * 0.45f;
            float ry = labelMargin + (i + 0.65f) * cellSize;
            canvas.drawText(rowStr, rx, ry, labelPaint);
        }

        // Draw 10x10 Grid Cells
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                float left = labelMargin + c * cellSize + 2f;
                float top = labelMargin + r * cellSize + 2f;
                float right = left + cellSize - 4f;
                float bottom = top + cellSize - 4f;
                cellRect.set(left, top, right, bottom);

                // Default empty cell background
                canvas.drawRoundRect(cellRect, 6f, 6f, cellBgPaint);
                canvas.drawRoundRect(cellRect, 6f, 6f, gridLinePaint);

                if (board != null) {
                    BoardCell cell = board.getCell(r, c);
                    if (cell != null) {
                        drawCellContent(canvas, cell, cellRect);
                    }
                }

                // Draw placement preview if active
                Coordinate currentCoord = new Coordinate(r, c);
                if (previewCoords.contains(currentCoord)) {
                    previewPaint.setColor(isPreviewValid ?
                            ContextCompat.getColor(getContext(), R.color.ship_preview_valid) :
                            ContextCompat.getColor(getContext(), R.color.ship_preview_invalid));
                    canvas.drawRoundRect(cellRect, 6f, 6f, previewPaint);
                }
            }
        }
    }

    private void drawCellContent(Canvas canvas, BoardCell cell, RectF rect) {
        CellState state = cell.getState();

        // In TARGET_RADAR mode, we don't show unattacked enemy ships (fog of war)
        if (mode == Mode.TARGET_RADAR && state == CellState.SHIP) {
            state = CellState.EMPTY;
        }

        switch (state) {
            case SHIP:
                canvas.drawRoundRect(rect, 8f, 8f, shipPaint);
                // Draw inner hull highlight
                break;

            case HIT:
                canvas.drawRoundRect(rect, 8f, 8f, hitPaint);
                // Draw explosion 'X'
                canvas.drawText("✕", rect.centerX(), rect.centerY() + (markerPaint.getTextSize() * 0.35f), markerPaint);
                break;

            case SUNK:
                canvas.drawRoundRect(rect, 8f, 8f, hitPaint);
                canvas.drawText("💥", rect.centerX(), rect.centerY() + (markerPaint.getTextSize() * 0.35f), markerPaint);
                break;

            case MISS:
                canvas.drawRoundRect(rect, 8f, 8f, missPaint);
                // Draw water splash dot
                canvas.drawCircle(rect.centerX(), rect.centerY(), rect.width() * 0.22f, markerPaint);
                break;

            case EMPTY:
            default:
                break;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float x = event.getX() - labelMargin;
            float y = event.getY() - labelMargin;

            if (x >= 0 && y >= 0) {
                int col = (int) (x / cellSize);
                int row = (int) (y / cellSize);

                if (row >= 0 && row < GRID_SIZE && col >= 0 && col < GRID_SIZE) {
                    if (cellClickListener != null) {
                        cellClickListener.onCellClick(row, col);
                    }
                    return true;
                }
            }
        }
        return true;
    }
}

package com.example.battlefleet.activities;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.battlefleet.R;
import com.example.battlefleet.adapters.MatchHistoryAdapter;
import com.example.battlefleet.database.MatchHistoryRepository;
import com.example.battlefleet.models.MatchRecord;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;


public class MatchHistoryActivity extends AppCompatActivity {

    private RecyclerView rvHistory;
    private MatchHistoryAdapter adapter;
    private View layoutEmpty;
    private Spinner spinnerFilter;
    private MaterialButton btnDateFilter;
    private ImageView btnClearDate;

    private MatchHistoryRepository historyRepo;
    private String currentResultFilter = "ALL";
    private String currentDateFilter = null; // "YYYY-MM-DD"

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_match_history);

        historyRepo = MatchHistoryRepository.getInstance(this);

        initViews();
        setupFilters();
        loadMatches();
    }

    private void initViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar_history);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvHistory = findViewById(R.id.rv_match_history);
        rvHistory.setLayoutManager(new LinearLayoutManager(this));

        adapter = new MatchHistoryAdapter(this::showMatchDetailDialog);
        rvHistory.setAdapter(adapter);

        layoutEmpty = findViewById(R.id.layout_empty_history);
        spinnerFilter = findViewById(R.id.spinner_result_filter);
        btnDateFilter = findViewById(R.id.btn_filter_date);
        btnClearDate = findViewById(R.id.btn_clear_date_filter);

        btnDateFilter.setOnClickListener(v -> showDatePicker());
        btnClearDate.setOnClickListener(v -> {
            currentDateFilter = null;
            btnDateFilter.setText("Date");
            btnClearDate.setVisibility(View.GONE);
            loadMatches();
        });
    }

    private void setupFilters() {
        String[] options = {"All Matches", "Victories (WIN)", "Defeats (LOSS)"};
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, options);
        spinnerFilter.setAdapter(spinnerAdapter);

        spinnerFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 1: currentResultFilter = "WIN"; break;
                    case 2: currentResultFilter = "LOSS"; break;
                    default: currentResultFilter = "ALL"; break;
                }
                loadMatches();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    /**
     * Demonstrates Unit 3 Syllabus: DatePickerDialog for match date selection.
     */
    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    currentDateFilter = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth);
                    btnDateFilter.setText(currentDateFilter);
                    btnClearDate.setVisibility(View.VISIBLE);
                    loadMatches();
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH));
        dialog.show();
    }

    private void loadMatches() {
        historyRepo.getMatchesFiltered(currentResultFilter, currentDateFilter,
                new MatchHistoryRepository.Callback<List<MatchRecord>>() {
                    @Override
                    public void onComplete(List<MatchRecord> result) {
                        if (result == null || result.isEmpty()) {
                            layoutEmpty.setVisibility(View.VISIBLE);
                            rvHistory.setVisibility(View.GONE);
                            adapter.setMatches(null);
                        } else {
                            layoutEmpty.setVisibility(View.GONE);
                            rvHistory.setVisibility(View.VISIBLE);
                            adapter.setMatches(result);
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        layoutEmpty.setVisibility(View.VISIBLE);
                        rvHistory.setVisibility(View.GONE);
                    }
                });
    }

    private void showMatchDetailDialog(MatchRecord record) {
        String detailMsg = String.format(Locale.getDefault(),
                "Room Code: #%s\n" +
                        "Commander: %s\n" +
                        "Opponent: %s\n" +
                        "Outcome: %s\n" +
                        "Date & Time: %s\n" +
                        "Duration: %s\n" +
                        "Firing Accuracy: %.1f%%\n" +
                        "Direct Hits: %d / %d Shots",
                record.getRoomCode(),
                record.getPlayerName(),
                record.getOpponentName(),
                record.getResult(),
                record.getCompletedAt(),
                record.getFormattedDuration(),
                record.getAccuracy(),
                record.getHits(),
                record.getShotsFired());

        new AlertDialog.Builder(this)
                .setTitle("Combat Engagement Details")
                .setMessage(detailMsg)
                .setPositiveButton("Close", null)
                .setNegativeButton("Delete Entry", (dialog, which) -> {
                    historyRepo.deleteMatch(record.getId(), new MatchHistoryRepository.Callback<Boolean>() {
                        @Override
                        public void onComplete(Boolean success) {
                            Toast.makeText(MatchHistoryActivity.this, "Record removed from archives", Toast.LENGTH_SHORT).show();
                            loadMatches();
                        }

                        @Override
                        public void onError(Exception e) {
                            Toast.makeText(MatchHistoryActivity.this, "Error deleting record", Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .show();
    }
}

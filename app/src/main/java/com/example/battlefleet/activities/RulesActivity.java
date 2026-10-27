package com.example.battlefleet.activities;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.battlefleet.R;
import com.example.battlefleet.adapters.RulesAdapter;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;


public class RulesActivity extends AppCompatActivity {

    private ListView lvRules;
    private TextView tvDrillReminder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rules);

        initViews();
        setupRulesList();
    }

    private void initViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar_rules);
        toolbar.setNavigationOnClickListener(v -> finish());

        lvRules = findViewById(R.id.lv_rules);
        tvDrillReminder = findViewById(R.id.tv_drill_reminder);

        findViewById(R.id.btn_set_reminder_time).setOnClickListener(v -> showTimePicker());
    }

    /**
     * Demonstrates Unit 3 Syllabus: TimePickerDialog implementation.
     */
    private void showTimePicker() {
        Calendar cal = Calendar.getInstance();
        TimePickerDialog dialog = new TimePickerDialog(this,
                (view, hourOfDay, minute) -> {
                    String formatted = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute);
                    tvDrillReminder.setText("Daily Combat Drill Scheduled: " + formatted);
                    Toast.makeText(RulesActivity.this, "Combat readiness drill set for " + formatted, Toast.LENGTH_SHORT).show();
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                true);
        dialog.show();
    }

    /**
     * Demonstrates Unit 2 Syllabus: ListView with ArrayAdapter.
     */
    private void setupRulesList() {
        List<RulesAdapter.RuleItem> items = new ArrayList<>();

        items.add(new RulesAdapter.RuleItem(
                "1. Objective & Naval Combat Overview",
                "BattleFleet is a turn-based multiplayer naval strategy game played on a 10x10 coordinate grid. " +
                        "Two commanders place their 5 fleet vessels secretly. Players alternate firing torpedoes to locate " +
                        "and destroy the enemy fleet. The first commander to sink all 17 enemy ship segments wins the war."
        ));

        items.add(new RulesAdapter.RuleItem(
                "2. Standard Fleet Vessel Specifications",
                "• Aircraft Carrier: 5 grid cells (Flagship)\n" +
                        "• Battleship: 4 grid cells (Heavy armor)\n" +
                        "• Cruiser: 3 grid cells (Frontline escort)\n" +
                        "• Submarine: 3 grid cells (Submerged stealth)\n" +
                        "• Destroyer: 2 grid cells (Rapid patrol)\n" +
                        "Total Fleet Coverage: 17 hit points. Ships must be straight, contiguous, and non-overlapping."
        ));

        items.add(new RulesAdapter.RuleItem(
                "3. TCP Client-Server Architecture",
                "Networking is powered by native Java TCP Sockets communicating with an authoritative Python TCP Server. " +
                        "All messages are framed using Newline-delimited JSON (\\n). Sockets run strictly on background threads, " +
                        "and incoming events are safely marshaled to the Android Main UI Thread via Handler(Looper.getMainLooper())."
        ));

        items.add(new RulesAdapter.RuleItem(
                "4. Android Emulator Setup (10.0.2.2)",
                "When running the Python server on your development laptop and testing via the official Android Emulator, " +
                        "configure the host address to '10.0.2.2' with port 8888. '10.0.2.2' is the Android Emulator's default " +
                        "loopback alias routing directly to 127.0.0.1 on the host development machine."
        ));

        items.add(new RulesAdapter.RuleItem(
                "5. Cross-Laptop Demonstration Setup",
                "To play across two physical laptops or devices on the same Wi-Fi:\n" +
                        "1. Run 'python3 server/battleship_server.py 8888' on the host laptop.\n" +
                        "2. Find the host laptop's local IP (e.g. 192.168.1.50) using 'ifconfig' or 'ipconfig'.\n" +
                        "3. In BattleFleet Settings, set the Server Host to the host IP on both devices.\n" +
                        "4. Ensure firewalls allow incoming traffic on TCP port 8888."
        ));

        items.add(new RulesAdapter.RuleItem(
                "6. Academic Mobile Development Syllabus Mapping",
                "• Unit 1: Android Architecture, Java 11, Manifest, ART runtime, Gradle.\n" +
                        "• Unit 2: XML layouts, Custom BoardView Canvas, ListView + ArrayAdapter (this screen), " +
                        "RecyclerView + Custom ViewHolder, Material Toolbar, Options Menus.\n" +
                        "• Unit 3: Explicit Intents (navigation), Intent Extras, Implicit Intents (Sharesheet), " +
                        "Local Notifications (NotificationManager + NotificationChannel), DatePickerDialog, TimePickerDialog, AlertDialogs, Toasts.\n" +
                        "• Unit 4: Direct SQLiteOpenHelper, Cursor traversal, ContentValues, CRUD operations, aggregations, and SharedPreferences."
        ));

        RulesAdapter adapter = new RulesAdapter(this, items);
        lvRules.setAdapter(adapter);
    }
}

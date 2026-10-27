package com.example.battlefleet.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Manages player settings and TCP connection parameters via Android SharedPreferences.
 * Demonstrates Unit 3 & 4 syllabus: Local persistent preferences with validation.
 */
public class PreferencesManager {
    private static final String PREF_NAME = "battlefleet_prefs";

    public static final String KEY_PLAYER_NAME = "player_name";
    public static final String KEY_SERVER_HOST = "server_host";
    public static final String KEY_SERVER_PORT = "server_port";
    public static final String KEY_SOUND_ENABLED = "sound_enabled";
    public static final String KEY_VIBRATION_ENABLED = "vibration_enabled";

    // Defaults
    public static final String DEFAULT_NAME = "Commander";
    // 10.0.2.2 is the official Android Emulator loopback alias for the development host machine.
    public static final String DEFAULT_HOST = "10.0.2.2";
    public static final int DEFAULT_PORT = 8888;

    private final SharedPreferences prefs;

    public PreferencesManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public String getPlayerName() {
        return prefs.getString(KEY_PLAYER_NAME, DEFAULT_NAME);
    }

    public void setPlayerName(String name) {
        prefs.edit().putString(KEY_PLAYER_NAME, name.trim().isEmpty() ? DEFAULT_NAME : name.trim()).apply();
    }

    public String getServerHost() {
        return prefs.getString(KEY_SERVER_HOST, DEFAULT_HOST);
    }

    public void setServerHost(String host) {
        prefs.edit().putString(KEY_SERVER_HOST, host.trim().isEmpty() ? DEFAULT_HOST : host.trim()).apply();
    }

    public int getServerPort() {
        return prefs.getInt(KEY_SERVER_PORT, DEFAULT_PORT);
    }

    public void setServerPort(int port) {
        if (port <= 0 || port > 65535) port = DEFAULT_PORT;
        prefs.edit().putInt(KEY_SERVER_PORT, port).apply();
    }

    public boolean isSoundEnabled() {
        return prefs.getBoolean(KEY_SOUND_ENABLED, true);
    }

    public void setSoundEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply();
    }

    public boolean isVibrationEnabled() {
        return prefs.getBoolean(KEY_VIBRATION_ENABLED, true);
    }

    public void setVibrationEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply();
    }

    public void resetDefaults() {
        prefs.edit().clear().apply();
    }
}

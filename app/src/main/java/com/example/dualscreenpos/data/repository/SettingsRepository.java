package com.example.dualscreenpos.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsRepository {

    private static SettingsRepository instance;
    private final SharedPreferences prefs;

    private SettingsRepository(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences("pos_settings", Context.MODE_PRIVATE);
    }

    public static void init(Context ctx) {
        if (instance == null) {
            instance = new SettingsRepository(ctx);
        }
    }

    public static SettingsRepository getInstance() {
        return instance;
    }

    public String getBaseUrl() {
        return prefs.getString("base_url", "http://10.0.2.2:8000");
    }

    public void setBaseUrl(String url) {
        prefs.edit().putString("base_url", url).apply();
    }


    public boolean isMockMode() {
        return prefs.getBoolean("mock_mode", false);
    }

    public void setMockMode(boolean enabled) {
        prefs.edit().putBoolean("mock_mode", enabled).apply();
    }

}

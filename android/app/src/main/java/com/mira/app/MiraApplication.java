package com.mira.app;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Applies the user's saved appearance choice before any activity is created, so
 * the theme does not flip after launch.
 */
public class MiraApplication extends Application {

    static final String PREFS = "mira_prefs";
    static final String PREF_THEME = "theme_mode";
    static final String PREF_EFFORT = "ai_effort";

    static final int THEME_SYSTEM = 0;
    static final int THEME_LIGHT = 1;
    static final int THEME_DARK = 2;

    @Override
    public void onCreate() {
        super.onCreate();

        int mode = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getInt(PREF_THEME, THEME_SYSTEM);

        AppCompatDelegate.setDefaultNightMode(nightModeFor(mode));
    }

    static int nightModeFor(int mode) {
        switch (mode) {
            case THEME_LIGHT:
                return AppCompatDelegate.MODE_NIGHT_NO;
            case THEME_DARK:
                return AppCompatDelegate.MODE_NIGHT_YES;
            default:
                return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
    }
}

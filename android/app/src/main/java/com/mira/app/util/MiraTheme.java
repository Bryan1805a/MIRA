package com.mira.app.util;

import android.content.Context;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.color.DynamicColors;
import com.mira.app.MiraApplication;

/**
 * Applies Material You colours when the user opted in. Android 12+ derives the
 * palette from the wallpaper; older versions and disabled preferences keep the
 * purple brand palette untouched.
 */
public final class MiraTheme {

    private MiraTheme() {
    }

    public static void apply(AppCompatActivity activity) {
        boolean enabled = activity
                .getSharedPreferences(
                        MiraApplication.PREFS,
                        Context.MODE_PRIVATE
                )
                .getBoolean(MiraApplication.PREF_DYNAMIC_COLOR, false);

        if (enabled) {
            DynamicColors.applyToActivityIfAvailable(activity);
        }
    }
}

package com.mira.app.util;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public final class WindowInsetsHelper {

    private WindowInsetsHelper() {
    }

    public static void apply(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(
                root,
                (view, windowInsets) -> {
                    Insets systemBars = windowInsets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                    );
                    Insets ime = windowInsets.getInsets(
                            WindowInsetsCompat.Type.ime()
                    );

                    view.setPadding(
                            systemBars.left,
                            systemBars.top,
                            systemBars.right,
                            Math.max(systemBars.bottom, ime.bottom)
                    );

                    return windowInsets;
                }
        );

        ViewCompat.requestApplyInsets(root);
    }
}
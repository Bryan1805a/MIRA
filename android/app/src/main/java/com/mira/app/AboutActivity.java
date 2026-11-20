package com.mira.app;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.mira.app.databinding.ActivityAboutBinding;
import com.mira.app.util.WindowInsetsHelper;

/**
 * Static About screen. It exists so the privacy story is demonstrable in the app
 * rather than only in the documentation: what memory holds, what it never holds,
 * and which backend this build talks to.
 */
public class AboutActivity extends AppCompatActivity {

    private ActivityAboutBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityAboutBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WindowInsetsHelper.apply(binding.aboutRoot);

        binding.aboutToolbar.setNavigationOnClickListener(
                view -> getOnBackPressedDispatcher().onBackPressed()
        );

        binding.aboutVersion.setText(
                getString(
                        R.string.about_version,
                        BuildConfig.VERSION_NAME
                )
        );

        binding.aboutBuild.setText(
                getString(
                        R.string.about_build_body,
                        BuildConfig.BUILD_TYPE,
                        BuildConfig.API_BASE_URL
                )
        );
    }
}

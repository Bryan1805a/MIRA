package com.mira.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.os.Build;
import android.content.SharedPreferences;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.mira.app.data.AuthCallback;
import com.mira.app.data.AuthRepository;
import com.mira.app.data.session.TokenStore;
import com.mira.app.databinding.ActivitySettingsBinding;
import com.mira.app.util.MiraTheme;
import com.mira.app.util.WindowInsetsHelper;

/**
 * Settings hub. Memory lives here (not in the navigation drawer) so the drawer
 * stays a history switcher, matching the Gemini mobile model.
 */
public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;
    private TokenStore tokenStore;
    private AuthRepository authRepository;

    private int themeMode = MiraApplication.THEME_SYSTEM;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        MiraTheme.apply(this);

        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowInsetsHelper.apply(binding.getRoot());

        tokenStore = TokenStore.get(this);

        if (!tokenStore.hasSession()) {
            openLogin();
            return;
        }

        authRepository = new AuthRepository(this);

        binding.settingsToolbar.setNavigationOnClickListener(
                view -> finish()
        );

        String email = tokenStore.email();
        binding.settingsEmail.setText(
                email == null || email.isBlank()
                        ? getString(R.string.session_unknown_account)
                        : email
        );

        renderDisplayName();
        binding.displayNameRow.setOnClickListener(
                view -> showDisplayNameDialog()
        );

        binding.memoryRow.setOnClickListener(
                view -> startActivity(
                        new Intent(this, MemoryActivity.class)
                )
        );

        binding.appearanceRow.setOnClickListener(
                view -> showAppearanceDialog()
        );

        setUpDynamicColor();

        binding.aboutValue.setText(BuildConfig.VERSION_NAME);
        binding.aboutRow.setOnClickListener(
                view -> startActivity(new Intent(this, AboutActivity.class))
        );

        binding.signOutButton.setOnClickListener(
                view -> logout()
        );

        renderAppearance();
    }

    private String savedDisplayName() {
        String value = getSharedPreferences(
                MiraApplication.PREFS,
                MODE_PRIVATE
        ).getString(MiraApplication.PREF_DISPLAY_NAME, "");

        return value == null ? "" : value.trim();
    }

    private void renderDisplayName() {
        String name = savedDisplayName();

        binding.displayNameValue.setText(
                name.isEmpty()
                        ? getString(R.string.display_name_unset)
                        : name
        );
    }

    private void showDisplayNameDialog() {
        View content = getLayoutInflater().inflate(
                R.layout.dialog_display_name,
                null,
                false
        );
        TextInputEditText input = content.findViewById(
                R.id.displayNameInput
        );

        input.setText(savedDisplayName());
        input.setSelection(
                input.getText() == null ? 0 : input.getText().length()
        );

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.display_name_dialog_title)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(
                        android.R.string.ok,
                        (dialog, which) -> {
                            String name = input.getText() == null
                                    ? ""
                                    : input.getText().toString().trim();

                            getSharedPreferences(
                                    MiraApplication.PREFS,
                                    MODE_PRIVATE
                            )
                                    .edit()
                                    .putString(
                                            MiraApplication.PREF_DISPLAY_NAME,
                                            name
                                    )
                                    .apply();

                            renderDisplayName();
                        }
                )
                .show();
    }

    private void setUpDynamicColor() {
        boolean supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;

        binding.dynamicColorRow.setVisibility(
                supported ? View.VISIBLE : View.GONE
        );

        if (!supported) {
            return;
        }

        final SharedPreferences preferences = getSharedPreferences(
                MiraApplication.PREFS,
                MODE_PRIVATE
        );

        binding.dynamicColorSwitch.setChecked(
                preferences.getBoolean(
                        MiraApplication.PREF_DYNAMIC_COLOR,
                        false
                )
        );

        binding.dynamicColorSwitch.setOnCheckedChangeListener(
                (button, checked) -> {
                    preferences.edit()
                            .putBoolean(
                                    MiraApplication.PREF_DYNAMIC_COLOR,
                                    checked
                            )
                            .apply();

                    /* Colours are resolved per activity, so re-inflate this one. */
                    recreate();
                }
        );

        binding.dynamicColorRow.setOnClickListener(
                view -> binding.dynamicColorSwitch.toggle()
        );
    }

    private void renderAppearance() {
        themeMode = getSharedPreferences(
                MiraApplication.PREFS,
                MODE_PRIVATE
        ).getInt(MiraApplication.PREF_THEME, MiraApplication.THEME_SYSTEM);

        binding.appearanceValue.setText(themeLabel(themeMode));
    }

    private void showAppearanceDialog() {
        CharSequence[] options = {
                getString(R.string.settings_theme_system),
                getString(R.string.settings_theme_light),
                getString(R.string.settings_theme_dark)
        };

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_appearance)
                .setSingleChoiceItems(options, themeMode, (dialog, which) -> {
                    applyTheme(which);
                    dialog.dismiss();
                })
                .show();
    }

    private void applyTheme(int mode) {
        getSharedPreferences(MiraApplication.PREFS, MODE_PRIVATE)
                .edit()
                .putInt(MiraApplication.PREF_THEME, mode)
                .apply();

        themeMode = mode;
        binding.appearanceValue.setText(themeLabel(mode));

        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                MiraApplication.nightModeFor(mode)
        );
    }

    private int themeLabel(int mode) {
        switch (mode) {
            case MiraApplication.THEME_LIGHT:
                return R.string.settings_theme_light;
            case MiraApplication.THEME_DARK:
                return R.string.settings_theme_dark;
            default:
                return R.string.settings_theme_system;
        }
    }

    private void logout() {
        authRepository.logout(new AuthCallback<Void>() {
            @Override
            public void onSuccess(Void value) {
                openLogin();
            }

            @Override
            public void onError(String message, int statusCode) {
                openLogin();
            }
        });
    }

    private void openLogin() {
        tokenStore.clear();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );
        startActivity(intent);
        finish();
    }
}

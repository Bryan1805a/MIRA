package com.mira.app;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.mira.app.data.AuthCallback;
import com.mira.app.data.AuthRepository;
import com.mira.app.data.session.TokenStore;
import com.mira.app.databinding.ActivitySettingsBinding;
import com.mira.app.util.WindowInsetsHelper;

/**
 * Settings hub. Memory lives here (not in the navigation drawer) so the drawer
 * stays a history switcher, matching the Gemini mobile model.
 */
public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;
    private TokenStore tokenStore;
    private AuthRepository authRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

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

        binding.memoryRow.setOnClickListener(
                view -> startActivity(
                        new Intent(this, MemoryActivity.class)
                )
        );

        binding.signOutButton.setOnClickListener(
                view -> logout()
        );
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

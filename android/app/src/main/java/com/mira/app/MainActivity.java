package com.mira.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.content.Intent;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.mira.app.data.AuthCallback;
import com.mira.app.data.AuthRepository;
import com.mira.app.data.model.CurrentUserResponse;
import com.mira.app.data.session.TokenStore;
import com.mira.app.databinding.ActivityMainBinding;
import com.mira.app.util.WindowInsetsHelper;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private AuthRepository authRepository;
    private TokenStore tokenStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowInsetsHelper.apply(binding.getRoot());

        tokenStore = TokenStore.get(this);
        authRepository = new AuthRepository(this);

        if (!tokenStore.hasSession()) {
            openLogin();
            return;
        }

        String email = tokenStore.email();

        if (email != null && !email.isBlank()) {
            binding.emailText.setText(
                    getString(R.string.signed_in_as, email)
            );
        }

        binding.statusText.setText(R.string.checking_session);
        binding.logoutButton.setOnClickListener(view -> logout());

        verifySession();
    }

    private void verifySession() {
        authRepository.me(new AuthCallback<CurrentUserResponse>() {
            @Override
            public void onSuccess(CurrentUserResponse value) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                if (value.getEmail() != null) {
                    binding.emailText.setText(
                            getString(
                                    R.string.signed_in_as,
                                    value.getEmail()
                            )
                    );
                }

                binding.statusText.setText(R.string.session_active);
            }

            @Override
            public void onError(String message, int statusCode) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                if (statusCode == 401) {
                    tokenStore.clear();
                    openLogin();
                    return;
                }

                binding.statusText.setText(R.string.session_offline);
            }
        });

        binding.conversationsButton.setOnClickListener(
            view -> startActivity(
                    new Intent(this, ConversationsActivity.class)
            )
        );
    }

    private void logout() {
        binding.logoutButton.setEnabled(false);
        binding.statusText.setText(R.string.signing_out);

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
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );
        startActivity(intent);
        finish();
    }
}
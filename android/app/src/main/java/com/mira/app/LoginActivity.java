package com.mira.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.mira.app.data.AuthCallback;
import com.mira.app.data.AuthRepository;
import com.mira.app.data.model.SessionResponse;
import com.mira.app.data.session.TokenStore;
import com.mira.app.databinding.ActivityLoginBinding;
import com.mira.app.util.WindowInsetsHelper;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private AuthRepository authRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowInsetsHelper.apply(binding.getRoot());

        authRepository = new AuthRepository(this);

        if (TokenStore.get(this).hasSession()) {
            openMain();
            return;
        }

        binding.loginButton.setOnClickListener(
                view -> attemptLogin()
        );

        binding.registerLink.setOnClickListener(view ->
                startActivity(new Intent(this, RegisterActivity.class))
        );
    }

    private void attemptLogin() {
        String email = textOf(binding.emailInput);
        String password = textOf(binding.passwordInput);

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            showError(getString(R.string.error_required));
            return;
        }

        binding.errorText.setVisibility(View.GONE);
        setLoading(true);

        authRepository.login(
                email,
                password,
                new AuthCallback<SessionResponse>() {
                    @Override
                    public void onSuccess(SessionResponse value) {
                        openMain();
                    }

                    @Override
                    public void onError(String message, int statusCode) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        setLoading(false);
                        showError(message);
                    }
                }
        );
    }

    private void openMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean loading) {
        binding.loginProgress.setVisibility(
                loading ? View.VISIBLE : View.GONE
        );
        binding.loginButton.setEnabled(!loading);
        binding.registerLink.setEnabled(!loading);
    }

    private void showError(String message) {
        binding.errorText.setText(message);
        binding.errorText.setVisibility(View.VISIBLE);
    }

    private String textOf(TextView view) {
        return view.getText() == null
                ? ""
                : view.getText().toString().trim();
    }
}
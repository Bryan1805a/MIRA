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
import com.mira.app.databinding.ActivityRegisterBinding;
import com.mira.app.util.WindowInsetsHelper;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private AuthRepository authRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowInsetsHelper.apply(binding.getRoot());

        authRepository = new AuthRepository(this);

        binding.registerButton.setOnClickListener(
                view -> attemptRegister()
        );

        binding.loginLink.setOnClickListener(view -> finish());
    }

    private void attemptRegister() {
        String email = textOf(binding.emailInput);
        String password = textOf(binding.passwordInput);
        String confirmation = textOf(binding.confirmPasswordInput);

        if (TextUtils.isEmpty(email)
                || TextUtils.isEmpty(password)
                || TextUtils.isEmpty(confirmation)) {
            showError(getString(R.string.error_required));
            return;
        }

        if (password.length() < 12) {
            showError(getString(R.string.error_password_length));
            return;
        }

        if (!password.equals(confirmation)) {
            showError(getString(R.string.error_password_mismatch));
            return;
        }

        binding.errorText.setVisibility(View.GONE);
        setLoading(true);

        authRepository.registerAndLogin(
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
        binding.registerProgress.setVisibility(
                loading ? View.VISIBLE : View.GONE
        );
        binding.registerButton.setEnabled(!loading);
        binding.loginLink.setEnabled(!loading);
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
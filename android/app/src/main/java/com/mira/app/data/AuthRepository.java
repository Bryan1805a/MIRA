package com.mira.app.data;

import android.content.Context;

import com.google.gson.Gson;
import com.mira.app.data.model.ApiError;
import com.mira.app.data.model.CurrentUserResponse;
import com.mira.app.data.model.LoginRequest;
import com.mira.app.data.model.LogoutRequest;
import com.mira.app.data.model.RegisterRequest;
import com.mira.app.data.model.RegisterResponse;
import com.mira.app.data.model.SessionResponse;
import com.mira.app.data.remote.ApiClient;
import com.mira.app.data.remote.MiraApi;
import com.mira.app.data.session.TokenStore;

import java.io.IOException;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthRepository {

    private final MiraApi api;
    private final TokenStore tokenStore;
    private final Gson gson = new Gson();

    public AuthRepository(Context context) {
        this.api = ApiClient.api(context);
        this.tokenStore = TokenStore.get(context);
    }

    public void login(
            String email,
            String password,
            AuthCallback<SessionResponse> callback
    ) {
        api.login(new LoginRequest(email, password))
                .enqueue(new Callback<SessionResponse>() {
                    @Override
                    public void onResponse(
                            Call<SessionResponse> call,
                            Response<SessionResponse> response
                    ) {
                        SessionResponse session = response.body();

                        if (response.isSuccessful() && session != null) {
                            tokenStore.saveSession(session);
                            callback.onSuccess(session);
                            return;
                        }

                        callback.onError(
                                readError(response),
                                response.code()
                        );
                    }

                    @Override
                    public void onFailure(
                            Call<SessionResponse> call,
                            Throwable throwable
                    ) {
                        callback.onError(
                                "Unable to reach MIRA.",
                                0
                        );
                    }
                });
    }

    public void registerAndLogin(
            String email,
            String password,
            AuthCallback<SessionResponse> callback
    ) {
        api.register(new RegisterRequest(email, password))
                .enqueue(new Callback<RegisterResponse>() {
                    @Override
                    public void onResponse(
                            Call<RegisterResponse> call,
                            Response<RegisterResponse> response
                    ) {
                        if (!response.isSuccessful()) {
                            callback.onError(
                                    readError(response),
                                    response.code()
                            );
                            return;
                        }

                        login(email, password, callback);
                    }

                    @Override
                    public void onFailure(
                            Call<RegisterResponse> call,
                            Throwable throwable
                    ) {
                        callback.onError(
                                "Unable to reach MIRA.",
                                0
                        );
                    }
                });
    }

    public void me(AuthCallback<CurrentUserResponse> callback) {
        api.me().enqueue(new Callback<CurrentUserResponse>() {
            @Override
            public void onResponse(
                    Call<CurrentUserResponse> call,
                    Response<CurrentUserResponse> response
            ) {
                CurrentUserResponse user = response.body();

                if (response.isSuccessful() && user != null) {
                    callback.onSuccess(user);
                    return;
                }

                callback.onError(
                        readError(response),
                        response.code()
                );
            }

            @Override
            public void onFailure(
                    Call<CurrentUserResponse> call,
                    Throwable throwable
            ) {
                callback.onError(
                        "Unable to reach MIRA.",
                        0
                );
            }
        });
    }

    public void logout(AuthCallback<Void> callback) {
        String refreshToken = tokenStore.refreshToken();

        if (refreshToken == null || refreshToken.isBlank()) {
            tokenStore.clear();
            callback.onSuccess(null);
            return;
        }

        api.logout(new LogoutRequest(refreshToken))
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(
                            Call<Void> call,
                            Response<Void> response
                    ) {
                        tokenStore.clear();
                        callback.onSuccess(null);
                    }

                    @Override
                    public void onFailure(
                            Call<Void> call,
                            Throwable throwable
                    ) {
                        /*
                         * Clear locally even if the network call failed.
                         * The user asked to sign out.
                         */
                        tokenStore.clear();
                        callback.onSuccess(null);
                    }
                });
    }

    private String readError(Response<?> response) {
        try (ResponseBody body = response.errorBody()) {
            if (body != null) {
                ApiError error = gson.fromJson(
                        body.charStream(),
                        ApiError.class
                );

                if (error != null
                        && error.getMessage() != null
                        && !error.getMessage().isBlank()) {
                    return error.getMessage();
                }
            }
        } catch (Exception ignored) {
            // Fall through to the generic message.
        }

        return "Request failed (" + response.code() + ").";
    }
}
package com.mira.app.data.remote;

import com.mira.app.data.model.CurrentUserResponse;
import com.mira.app.data.model.LoginRequest;
import com.mira.app.data.model.LogoutRequest;
import com.mira.app.data.model.RefreshRequest;
import com.mira.app.data.model.RegisterRequest;
import com.mira.app.data.model.RegisterResponse;
import com.mira.app.data.model.SessionResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface MiraApi {

    @POST("api/v1/auth/register")
    Call<RegisterResponse> register(@Body RegisterRequest request);

    @POST("api/v1/auth/login")
    Call<SessionResponse> login(@Body LoginRequest request);

    @POST("api/v1/auth/refresh")
    Call<SessionResponse> refresh(@Body RefreshRequest request);

    @POST("api/v1/auth/logout")
    Call<Void> logout(@Body LogoutRequest request);

    @GET("api/v1/me")
    Call<CurrentUserResponse> me();
}
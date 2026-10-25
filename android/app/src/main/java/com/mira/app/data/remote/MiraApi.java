package com.mira.app.data.remote;

import com.mira.app.data.model.CurrentUserResponse;
import com.mira.app.data.model.LoginRequest;
import com.mira.app.data.model.LogoutRequest;
import com.mira.app.data.model.RefreshRequest;
import com.mira.app.data.model.RegisterRequest;
import com.mira.app.data.model.RegisterResponse;
import com.mira.app.data.model.SessionResponse;
import com.mira.app.data.model.ConversationPageResponse;
import com.mira.app.data.model.ConversationResponse;
import com.mira.app.data.model.CreateConversationRequest;
import com.mira.app.data.model.MessagePageResponse;
import com.mira.app.data.model.RetryResponse;
import com.mira.app.data.model.SendMessageRequest;
import com.mira.app.data.model.SendMessageResponse;

import retrofit2.http.Header;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

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

    @GET("api/v1/conversations")
    Call<ConversationPageResponse> conversations(
            @Query("limit") int limit,
            @Query("cursor") String cursor
    );

    @POST("api/v1/conversations")
    Call<ConversationResponse> createConversation(
            @Body CreateConversationRequest request
    );

    @DELETE("api/v1/conversations/{conversationId}")
    Call<Void> deleteConversation(
            @Path("conversationId") String conversationId
    );

        @GET("api/v1/conversations/{conversationId}/messages")
    Call<MessagePageResponse> messages(
            @Path("conversationId") String conversationId,
            @Query("limit") int limit,
            @Query("cursor") String cursor
    );

    @POST("api/v1/conversations/{conversationId}/messages")
    Call<SendMessageResponse> sendMessage(
            @Path("conversationId") String conversationId,
            @Header("Idempotency-Key") String idempotencyKey,
            @Body SendMessageRequest request
    );

    @POST("api/v1/conversations/{conversationId}/responses/{responseId}/retry")
    Call<RetryResponse> retryMessage(
            @Path("conversationId") String conversationId,
            @Path("responseId") String responseId,
            @Header("Idempotency-Key") String idempotencyKey
    );
}

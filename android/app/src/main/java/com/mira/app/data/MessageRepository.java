package com.mira.app.data;

import android.content.Context;

import com.mira.app.data.model.MessagePageResponse;
import com.mira.app.data.model.RetryResponse;
import com.mira.app.data.model.SendMessageRequest;
import com.mira.app.data.model.SendMessageResponse;
import com.mira.app.data.remote.ApiClient;
import com.mira.app.data.remote.MiraApi;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MessageRepository {

    private static final int PAGE_SIZE = 50;

    private final MiraApi api;

    public MessageRepository(Context context) {
        this.api = ApiClient.api(context);
    }

    public void list(
            String conversationId,
            String cursor,
            ApiCallback<MessagePageResponse> callback
    ) {
        api.messages(conversationId, PAGE_SIZE, cursor)
                .enqueue(new Callback<MessagePageResponse>() {
                    @Override
                    public void onResponse(
                            Call<MessagePageResponse> call,
                            Response<MessagePageResponse> response
                    ) {
                        MessagePageResponse page = response.body();

                        if (response.isSuccessful() && page != null) {
                            callback.onSuccess(page);
                            return;
                        }

                        callback.onError(
                                ApiErrors.message(response),
                                response.code()
                        );
                    }

                    @Override
                    public void onFailure(
                            Call<MessagePageResponse> call,
                            Throwable throwable
                    ) {
                        callback.onError(
                                "Unable to reach MIRA.",
                                0
                        );
                    }
                });
    }

    public void send(
            String conversationId,
            String content,
            String idempotencyKey,
            ApiCallback<SendMessageResponse> callback
    ) {
        api.sendMessage(
                        conversationId,
                        idempotencyKey,
                        new SendMessageRequest(content)
                )
                .enqueue(new Callback<SendMessageResponse>() {
                    @Override
                    public void onResponse(
                            Call<SendMessageResponse> call,
                            Response<SendMessageResponse> response
                    ) {
                        SendMessageResponse body = response.body();

                        if (response.isSuccessful() && body != null) {
                            callback.onSuccess(body);
                            return;
                        }

                        callback.onError(
                                ApiErrors.message(response),
                                response.code()
                        );
                    }

                    @Override
                    public void onFailure(
                            Call<SendMessageResponse> call,
                            Throwable throwable
                    ) {
                        callback.onError(
                                "Unable to reach MIRA.",
                                0
                        );
                    }
                });
    }

    public void retry(
            String conversationId,
            String responseId,
            String idempotencyKey,
            ApiCallback<RetryResponse> callback
    ) {
        api.retryMessage(
                        conversationId,
                        responseId,
                        idempotencyKey
                )
                .enqueue(new Callback<RetryResponse>() {
                    @Override
                    public void onResponse(
                            Call<RetryResponse> call,
                            Response<RetryResponse> response
                    ) {
                        RetryResponse body = response.body();

                        if (response.isSuccessful() && body != null) {
                            callback.onSuccess(body);
                            return;
                        }

                        callback.onError(
                                ApiErrors.message(response),
                                response.code()
                        );
                    }

                    @Override
                    public void onFailure(
                            Call<RetryResponse> call,
                            Throwable throwable
                    ) {
                        callback.onError(
                                "Unable to reach MIRA.",
                                0
                        );
                    }
                });
    }
}
package com.mira.app.data;

import android.content.Context;

import com.mira.app.data.model.ConversationPageResponse;
import com.mira.app.data.model.ConversationResponse;
import com.mira.app.data.model.CreateConversationRequest;
import com.mira.app.data.remote.ApiClient;
import com.mira.app.data.remote.MiraApi;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ConversationRepository {

    private static final int PAGE_SIZE = 20;

    private final MiraApi api;

    public ConversationRepository(Context context) {
        this.api = ApiClient.api(context);
    }

    public void list(
            String cursor,
            ApiCallback<ConversationPageResponse> callback
    ) {
        api.conversations(PAGE_SIZE, cursor)
                .enqueue(new Callback<ConversationPageResponse>() {
                    @Override
                    public void onResponse(
                            Call<ConversationPageResponse> call,
                            Response<ConversationPageResponse> response
                    ) {
                        ConversationPageResponse page = response.body();

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
                            Call<ConversationPageResponse> call,
                            Throwable throwable
                    ) {
                        callback.onError(
                                "Unable to reach MIRA.",
                                0
                        );
                    }
                });
    }

    public void create(
            String title,
            ApiCallback<ConversationResponse> callback
    ) {
        api.createConversation(new CreateConversationRequest(title))
                .enqueue(new Callback<ConversationResponse>() {
                    @Override
                    public void onResponse(
                            Call<ConversationResponse> call,
                            Response<ConversationResponse> response
                    ) {
                        ConversationResponse conversation = response.body();

                        if (response.isSuccessful()
                                && conversation != null) {
                            callback.onSuccess(conversation);
                            return;
                        }

                        callback.onError(
                                ApiErrors.message(response),
                                response.code()
                        );
                    }

                    @Override
                    public void onFailure(
                            Call<ConversationResponse> call,
                            Throwable throwable
                    ) {
                        callback.onError(
                                "Unable to reach MIRA.",
                                0
                        );
                    }
                });
    }

    public void delete(
            String conversationId,
            ApiCallback<Void> callback
    ) {
        api.deleteConversation(conversationId)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(
                            Call<Void> call,
                            Response<Void> response
                    ) {
                        if (response.isSuccessful()) {
                            callback.onSuccess(null);
                            return;
                        }

                        callback.onError(
                                ApiErrors.message(response),
                                response.code()
                        );
                    }

                    @Override
                    public void onFailure(
                            Call<Void> call,
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
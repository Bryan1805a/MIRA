package com.mira.app.data;

import android.content.Context;

import com.mira.app.data.model.MemoryCandidateListResponse;
import com.mira.app.data.model.MemorySettingsResponse;
import com.mira.app.data.model.StyleProfileResponse;
import com.mira.app.data.model.UpdateMemorySettingsRequest;
import com.mira.app.data.model.UpdateStyleProfileRequest;
import com.mira.app.data.remote.ApiClient;
import com.mira.app.data.remote.MiraApi;

import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MemoryRepository {

    private final MiraApi api;

    public MemoryRepository(Context context) {
        this.api = ApiClient.api(context);
    }

    public void getStyle(ApiCallback<StyleProfileResponse> callback) {
        enqueue(api.getStyleProfile(), callback);
    }

    public void replace(
            int expectedVersion,
            Map<String, String> profile,
            ApiCallback<StyleProfileResponse> callback
    ) {
        enqueue(
                api.replaceStyleProfile(
                        new UpdateStyleProfileRequest(
                                expectedVersion,
                                profile
                        )
                ),
                callback
        );
    }

    public void updateSettings(
            boolean enabled,
            ApiCallback<MemorySettingsResponse> callback
    ) {
        enqueue(
                api.updateMemorySettings(
                        new UpdateMemorySettingsRequest(enabled)
                ),
                callback
        );
    }

    public void purge(ApiCallback<Void> callback) {
        enqueue(api.purgeStyleProfile(), callback);
    }

    public void listCandidates(
            ApiCallback<MemoryCandidateListResponse> callback
    ) {
        enqueue(api.listMemoryCandidates(), callback);
    }

    public void acceptCandidate(
            String candidateId,
            ApiCallback<StyleProfileResponse> callback
    ) {
        enqueue(
                api.acceptMemoryCandidate(candidateId),
                callback
        );
    }

    public void rejectCandidate(
            String candidateId,
            ApiCallback<Void> callback
    ) {
        enqueue(
                api.rejectMemoryCandidate(candidateId),
                callback
        );
    }

    private <T> void enqueue(
            Call<T> call,
            ApiCallback<T> callback
    ) {
        call.enqueue(new Callback<T>() {
            @Override
            public void onResponse(
                    Call<T> call,
                    Response<T> response
            ) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                    return;
                }

                callback.onError(
                        ApiErrors.message(response),
                        response.code()
                );
            }

            @Override
            public void onFailure(
                    Call<T> call,
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
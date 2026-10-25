package com.mira.app.data;

import com.google.gson.Gson;
import com.mira.app.data.model.ApiError;

import okhttp3.ResponseBody;
import retrofit2.Response;

public final class ApiErrors {

    private static final Gson GSON = new Gson();

    private ApiErrors() {
    }

    public static String message(Response<?> response) {
        try (ResponseBody body = response.errorBody()) {
            if (body != null) {
                ApiError error = GSON.fromJson(
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
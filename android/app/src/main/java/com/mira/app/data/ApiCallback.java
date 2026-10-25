package com.mira.app.data;

public interface ApiCallback<T> {

    void onSuccess(T value);

    /**
     * @param statusCode HTTP status, or 0 for a network failure.
     */
    void onError(String message, int statusCode);
}
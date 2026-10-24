package com.mira.app.data.model;

public class ApiError {

    private String error;
    private String message;
    private String requestId;

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public String getRequestId() {
        return requestId;
    }
}
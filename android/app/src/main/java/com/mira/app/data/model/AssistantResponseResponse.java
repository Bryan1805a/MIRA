package com.mira.app.data.model;

public class AssistantResponseResponse {

    private String id;
    private ResponseStatus status;
    private String content;
    private String createdAt;

    public String getId() {
        return id;
    }

    public ResponseStatus getStatus() {
        return status;
    }

    public String getContent() {
        return content;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
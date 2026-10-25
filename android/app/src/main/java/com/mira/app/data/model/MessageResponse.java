package com.mira.app.data.model;

public class MessageResponse {

    private String id;
    private MessageRole role;
    private String content;
    private String createdAt;
    private ResponseMetadata response;

    public MessageResponse() {
    }

    public MessageResponse(
            String id,
            MessageRole role,
            String content,
            String createdAt,
            ResponseMetadata response
    ) {
        this.id = id;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
        this.response = response;
    }

    public String getId() {
        return id;
    }

    public MessageRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public ResponseMetadata getResponse() {
        return response;
    }

    public static class ResponseMetadata {

        private String id;
        private ResponseStatus status;
        private int attemptCount;

        public ResponseMetadata() {
        }

        public ResponseMetadata(
                String id,
                ResponseStatus status,
                int attemptCount
        ) {
            this.id = id;
            this.status = status;
            this.attemptCount = attemptCount;
        }

        public String getId() {
            return id;
        }

        public ResponseStatus getStatus() {
            return status;
        }

        public int getAttemptCount() {
            return attemptCount;
        }
    }
}
package com.mira.app.data.model;

public class SendMessageResponse {

    private MessageResponse userMessage;
    private AssistantResponseResponse assistantResponse;

    public MessageResponse getUserMessage() {
        return userMessage;
    }

    public AssistantResponseResponse getAssistantResponse() {
        return assistantResponse;
    }
}
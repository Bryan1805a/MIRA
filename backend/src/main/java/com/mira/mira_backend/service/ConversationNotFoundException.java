package com.mira.mira_backend.service;

public class ConversationNotFoundException extends RuntimeException {

    public ConversationNotFoundException() {
        super("Conversation not found.");
    }
}
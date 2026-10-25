package com.mira.app.data.model;

import java.util.List;

public class ConversationPageResponse {

    private List<ConversationResponse> items;
    private String nextCursor;

    public List<ConversationResponse> getItems() {
        return items;
    }

    public String getNextCursor() {
        return nextCursor;
    }
}
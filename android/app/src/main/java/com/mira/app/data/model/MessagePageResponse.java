package com.mira.app.data.model;

import java.util.List;

public class MessagePageResponse {

    private List<MessageResponse> items;
    private String nextCursor;

    public List<MessageResponse> getItems() {
        return items;
    }

    public String getNextCursor() {
        return nextCursor;
    }
}
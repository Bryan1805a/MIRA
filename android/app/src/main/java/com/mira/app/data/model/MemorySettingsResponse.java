package com.mira.app.data.model;

public class MemorySettingsResponse {

    private boolean enabled;
    private int version;
    private String updatedAt;

    public boolean isEnabled() {
        return enabled;
    }

    public int getVersion() {
        return version;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }
}
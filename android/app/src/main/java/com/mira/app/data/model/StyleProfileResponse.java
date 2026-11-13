package com.mira.app.data.model;

import java.util.Map;

public class StyleProfileResponse {

    private boolean enabled;
    private int version;
    private Map<String, String> profile;
    private String updatedAt;

    public boolean isEnabled() {
        return enabled;
    }

    public int getVersion() {
        return version;
    }

    public Map<String, String> getProfile() {
        return profile;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }
}
package com.mira.app.data.model;

import java.util.Map;

public class UpdateStyleProfileRequest {

    private final int expectedVersion;
    private final Map<String, String> profile;

    public UpdateStyleProfileRequest(
            int expectedVersion,
            Map<String, String> profile
    ) {
        this.expectedVersion = expectedVersion;
        this.profile = profile;
    }
}
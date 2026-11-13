package com.mira.app.data.model;

public class MemoryCandidateResponse {

    private String id;
    private String field;
    private String value;
    private String evidence;
    private int evidenceCount;
    private String createdAt;

    public String getId() {
        return id;
    }

    public String getField() {
        return field;
    }

    public String getValue() {
        return value;
    }

    public String getEvidence() {
        return evidence;
    }

    public int getEvidenceCount() {
        return evidenceCount;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
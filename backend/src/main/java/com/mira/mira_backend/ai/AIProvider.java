package com.mira.mira_backend.ai;

public interface AIProvider {

    AICompletion generate(AIRequest request)
            throws AIProviderException;
}
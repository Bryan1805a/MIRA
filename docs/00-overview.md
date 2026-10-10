# MIRA — Project Overview

**Full Name:** Memory-Informed Response Agent  
**Project Status:** Planning  
**Document Version:** 0.1.0  
**Last Updated:** 2026-10-10

## 1. Product Vision

MIRA is a personalized AI conversational assistant designed to provide responses that adapt to a user's preferred communication style across multiple conversation sessions.

Rather than relying exclusively on the current conversation, MIRA maintains a persistent user style profile that can inform response generation in future sessions.

MIRA aims to make AI interactions feel more consistent, natural, and personalized without requiring users to repeatedly explain how they prefer to communicate.

## 2. Problem Statement

AI assistants may not consistently preserve a user's preferred response style across independent conversation sessions.

Users who prefer particular explanation styles, levels of detail, tones, or learning approaches may need to repeatedly communicate these preferences.

MIRA addresses this problem through persistent conversational style memory and context-aware response generation.

## 3. Product Goals

- Provide a native Android application for AI conversations.
- Integrate an external large language model through a secure backend.
- Maintain conversation history across sessions.
- Build and update a persistent user style profile.
- Use stored preferences to inform responses in new sessions.
- Support image understanding and PDF-based question answering, subject to model and API capabilities.
- Provide users with control over their stored personalization data.
- Maintain reliable documentation, automated tests, and reproducible deployment procedures.

## 4. Non-Goals

The initial release will not attempt to:

- Train or fine-tune a foundation model.
- Reproduce the full capabilities of commercial AI platforms.
- Build a general-purpose autonomous agent.
- Guarantee perfect inference of a user's personality or preferences.
- Implement a complex multi-agent architecture without a demonstrated need.
- Support every document format or every possible multimodal input.

## 5. Target Users

The initial target user is an individual who regularly uses AI assistants and values consistent communication preferences across conversations.

The first release will prioritize a single-user experience with secure account isolation and an architecture that can support multiple registered users.

## 6. Core Features

### 6.1. Conversational Chat
Users can create conversations, exchange messages with the AI assistant, and revisit previous sessions.

### 6.2. Persistent Style Memory
MIRA extracts candidate communication preferences from conversations, validates them, and merges appropriate updates into a persistent user profile.

### 6.3. Image Understanding
Users can submit supported images and ask questions about their contents.

### 6.4. PDF Understanding
Users can provide PDF documents and ask questions about their contents within supported document and context limits.

### 6.5. Memory Management
Users can inspect, modify, disable, or delete their stored style preferences.

### 6.6. Account and Data Isolation
Each user can access only their own conversations, files, and personalization data.

## 7. Proposed Technology Stack

| Component | Proposed Technology |
|---|---|
| Mobile application | Android, Java, Gradle |
| Backend | Java, Spring Boot, Maven |
| Relational database | PostgreSQL |
| Database hosting | Neon PostgreSQL |
| AI provider | DeepSeek API |
| API communication | HTTPS, REST, JSON |
| Source control | Git |

Technology choices remain subject to documented technical validation before implementation.

## 8. Design Principles

1. **Memory informs responses; it does not define the user.**
2. Explicit user preferences take precedence over uncertain inferences.
3. Current instructions take precedence over conflicting historical preferences.
4. Users retain control over their personalization data.
5. Secrets and provider credentials remain on the backend.
6. Every significant architectural decision should be documented.
7. Features must be testable and demonstrable on a real Android device.
8. Complexity must be justified by a clear product or engineering requirement.

## 9. Initial Success Criteria

The initial release will be considered successful when:

- A user can register, authenticate, and use the application on a physical Android device.
- Messages and conversation history persist in PostgreSQL.
- A user's style profile can be updated and reused in a new session.
- Memory updates do not silently overwrite newer information with stale results.
- Users can inspect and control their stored style preferences.
- Supported image and PDF inputs can be processed through the backend.
- Unauthorized access to another user's data is prevented and tested.
- Automated tests cover critical business logic and API behavior.
- A new developer can follow the documentation to configure and run the project.

## 10. Open Decisions

The following decisions must be resolved before their corresponding implementations begin:

- Authentication mechanism and account lifecycle.
- Exact DeepSeek model identifiers, API endpoints, and multimodal capabilities.
- Memory schema, update policy, and conflict resolution.
- File storage strategy and retention policy.
- Database migration and testing strategy.
- Deployment environment, operational limits, and cost constraints.

## 11. Scope Management

Features will be prioritized according to user value, technical feasibility, implementation cost, and the six-week academic delivery constraint, if applicable.

Any scope change affecting architecture, security, data integrity, or delivery commitments must be reflected in the relevant requirements and design documents.
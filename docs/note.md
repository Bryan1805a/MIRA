sequenceDiagram
    participant U as User
    participant A as Android
    participant B as Backend
    participant D as PostgreSQL
    participant L as DeepSeek

    U->>A: Send message
    A->>B: POST message
    B->>D: Load profile and history
    D-->>B: Relevant context
    B->>L: Prompt + context
    L-->>B: Assistant response
    B->>D: Save user and assistant messages
    B-->>A: Response
    A-->>U: Display answer
    B->>L: Extract style update
    L-->>B: Structured memory candidate
    B->>B: Validate and merge
    B->>D: Save updated profile
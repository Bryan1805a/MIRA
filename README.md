# MIRA

**Memory-Informed Response Agent** — a personal AI chat application designed to provide conversational continuity through user-controlled communication-style memory.

## Project Goals

- Build a native Android chat application.
- Develop a Java Spring Boot backend.
- Integrate the DeepSeek API without exposing provider credentials to the client.
- Store conversations and user-approved communication preferences in PostgreSQL.
- Give users control over viewing, editing, disabling, and deleting persistent memory.

## Architecture

- **Android:** Java, Gradle, Android Views/XML
- **Backend:** Java, Spring Boot, Maven
- **Database:** PostgreSQL
- **AI Provider:** DeepSeek API
- **API:** Versioned REST endpoints under `/api/v1`

MIRA uses a modular monolith architecture. The backend owns authentication, conversation persistence, memory lifecycle management, and communication with the AI provider.

## Repository Structure

- `android/` — Android application
- `backend/` — Spring Boot backend
- `docs/` — Requirements, architecture, domain model, API contracts, security, testing, and deployment documentation

## Development Status

Currently in Phase 0: repository preparation and application skeleton setup.

See `docs/09-phase-0-checklist.md` for the initial implementation checklist.
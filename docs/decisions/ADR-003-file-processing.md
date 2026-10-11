# ADR-003: Multimodal File Processing Phasing Strategy

| Attribute | Value |
| :--- | :--- |
| **Status** | Accepted |
| **Deciders** | Bryan, MIRA Architecture Team |
| **Date** | 2026-10-10 |
| **Technical Story** | [docs/01-requirements.md](file:///C:/Users/Bryan/Documents/MIRA/docs/01-requirements.md), [docs/02-architecture.md](file:///C:/Users/Bryan/Documents/MIRA/docs/02-architecture.md) |

---

## Context and Problem Statement

The initial MIRA product vision included user aspirations for image understanding and PDF-based question answering. However, multimodal file ingestion introduces substantial engineering overhead:
* Handling multi-part binary uploads and object storage infrastructure (e.g., S3 / Cloud Storage).
* PDF text extraction, document chunking, and context window limits.
* Provider-specific multimodal vision APIs, variable latency, and increased token costs.
* Complex failure and timeout modes for multi-megabyte payloads.

We need to decide whether to implement multimodal file processing in the Phase 1 (MVP) release or defer it to Phase 2.

## Decision Drivers

* **MVP Core Value Focus:** MIRA's primary competitive differentiator is persistent communication-style memory, not generic document parsing.
* **Delivery Timeline:** Ensuring a rock-solid, demonstrable system within the initial project milestones.
* **System Stability & Reliability:** Ensuring that the text chat engine, idempotent retry handling, and memory concurrency guards are thoroughly verified before introducing binary media pipelines.
* **Cost Predictability:** Preventing unexpected provider bills from high-token multimodal API calls.

## Considered Options

1. **Option 1: Include Image and PDF Processing in the Initial MVP Scope**
2. **Option 2: Defer Multimodal Processing to Phase 2 (Text-First MVP)**
3. **Option 3: Support Image Ingestion Only, Deferring PDF Processing**

---

## Decision Outcome

**Chosen Option: Option 2 — Defer Multimodal Processing to Phase 2 (Text-First MVP)**.

### Rationale

1. **Stabilize Core Differentiator First:** The core value proposition of MIRA is the **persistent communication-style memory engine**. Delivering rock-solid authentication, session rotation, idempotent messaging, and OCC-backed memory persistence provides immense user value without the operational complexity of binary asset management.
2. **Clean Evolutionary Path:** By implementing an abstracted `AIProvider` interface and extensible relational schemas, the backend architecture is cleanly decoupled. In Phase 2, attachment tables and multimodal provider adapters can be added via backward-compatible Flyway migrations without disrupting existing conversation models.
3. **Predictable Development & Testing:** Isolating MVP scope to text conversations enables 100% deterministic testing using `MockAIProvider` and Testcontainers.

---

## Pros and Cons of the Options

### Option 2: Defer to Phase 2 (Chosen)
* **Good:** Guarantees timely delivery of a robust, fully tested MVP.
* **Good:** Keeps infrastructure simple (no cloud object storage or multipart upload buckets required in Phase 1).
* **Good:** Focuses engineering resources on perfecting the memory lifecycle, concurrency guards, and mobile UI.
* **Bad:** Users cannot upload images or PDFs in the initial release.

### Option 1: Include Multimodal in MVP
* **Good:** Broader initial feature set.
* **Bad:** Dilutes engineering focus across complex binary storage, PDF chunking, and multimodal token billing.
* **Bad:** Substantially increases the risk of delivery delays and production bugs.

### Option 3: Image Only
* **Good:** Partial multimodal capability.
* **Bad:** Still requires binary storage buckets, image compression on Android, and vision API mapping without addressing document parsing.

---

## Consequences & Follow-up Actions

* The MVP schema (`MESSAGE`) remains text-focused and clean.
* Multimodal requirements in `01-requirements.md` are formally marked as Phase 2 scope.
* Phase 2 design will specify cloud object storage (e.g., S3/Cloud Storage) and document processing workflows when text chat stability is proven.

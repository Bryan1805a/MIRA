# ADR-005: Android Release Signing & Artifact Strategy

| Attribute | Value |
| :--- | :--- |
| **Status** | Accepted |
| **Deciders** | Bryan, MIRA Architecture Team |
| **Date** | 2026-11-14 |
| **Technical Story** | [12-phase-3-checklist.md](../12-phase-3-checklist.md) |

---

## Context and Problem Statement

MIRA's Android client has only been built in debug mode. The release build type currently contains a placeholder API URL and has no signing configuration, so it cannot be installed as a production artifact or uploaded to an app store. The release build also enables R8 optimization, which needs explicit keep rules for Retrofit, Gson, OkHttp, and AndroidX Security.

We need a repeatable, CI-compatible release process that:

1. Keeps the signing key and passwords out of source control.
2. Produces a signed APK for direct installation and a signed AAB for Play Store distribution.
3. Makes the backend base URL configurable per environment.
4. Keeps local development builds working without release secrets.
5. Fails clearly when release secrets are missing.

## Decision Drivers

* **Secret isolation:** The keystore and passwords must never be committed or baked into CI logs.
* **Reproducibility:** The same command must produce the same artifact on a developer machine and in CI.
* **Local ergonomics:** `assembleDebug` must work with no release configuration.
* **Store readiness:** Play Store distribution requires an AAB signed with a stable upload key.
* **Optimization safety:** R8 must not break Retrofit/Gson reflection or AndroidX Security.

## Considered Options

1. **Commit a keystore to the repository.**
2. **Environment variables or an untracked `keystore.properties`, with signing only enabled when present.**
3. **Use Play App Signing exclusively and never hold a local keystore.**

## Decision Outcome

**Chosen Option: Option 2 — environment variables or an untracked `keystore.properties`, with signing only enabled when all values are present.**

### Implementation Details

- `android/app/build.gradle.kts` reads release signing values in this order:
  1. Environment variables: `MIRA_KEYSTORE_FILE`, `MIRA_KEYSTORE_PASSWORD`, `MIRA_KEY_ALIAS`, `MIRA_KEY_PASSWORD`.
  2. An untracked `android/keystore.properties` with keys `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.
- If any required value is missing, the release variant is built unsigned rather than failing debug builds. The release CI workflow explicitly checks for all four secrets and fails before building if any are absent.
- The keystore file itself is excluded by `*.jks`, `*.keystore`, and `keystore.properties` in the repository `.gitignore`.
- The release workflow decodes `MIRA_KEYSTORE_BASE64` into `$RUNNER_TEMP` for the duration of the job; it is never written into the workspace.
- The release backend URL is supplied through `MIRA_API_BASE_URL` (or `-PmiraApiBaseUrl`); the default remains a placeholder.
- Version code and name are supplied through `MIRA_VERSION_CODE` / `MIRA_VERSION_NAME` (or Gradle properties). The tag workflow derives the name from the `v*` tag and the code from the workflow run number.
- The tag workflow produces both `app-release.apk` and `app-release.aab`, verifies the APK with `apksigner`, and uploads both as artifacts.
- R8 keep rules live in `android/app/proguard-rules.pro`.

## Pros and Cons of the Options

### Option 1: Commit the keystore
- **Bad:** Anyone with repository access can sign updates; a leak is unrecoverable without rotating the app identity.
- **Bad:** Violates the project's zero-secret-check-in rule.

### Option 2: Environment variables / untracked properties (Chosen)
- **Good:** Secrets stay in GitHub Secrets or the developer's environment.
- **Good:** Local debug builds need no secrets.
- **Good:** CI and local release builds use the same code path.
- **Bad:** Contributors need the keystore and passwords to produce a release locally; documented in the README.

### Option 3: Play App Signing only
- **Good:** Google manages the app signing key.
- **Bad:** An upload key is still required; local release installs still need a signature.
- **Bad:** Adds external dependency before the first store release.

## Consequences & Follow-up Actions

- Create the release keystore once and back it up offline (password manager + encrypted backup).
- Add `MIRA_KEYSTORE_BASE64`, `MIRA_KEYSTORE_PASSWORD`, `MIRA_KEY_ALIAS`, and `MIRA_KEY_PASSWORD` as GitHub Actions secrets.
- Add `MIRA_API_BASE_URL` as a workflow input or repository variable for the target environment.
- Run the `Android Release` workflow on a `v*` tag before the first store upload.
- If the upload key is lost, follow the Play Console upload-key reset process; Play App Signing protects the installed app identity.

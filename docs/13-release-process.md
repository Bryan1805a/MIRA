# MIRA — Release Process

| Attribute | Value |
| :--- | :--- |
| **Document ID** | MIRA-REL-001 |
| **Classification** | Internal Engineering Specification |
| **Status** | Active Baseline |
| **Version** | 1.0.0 |
| **Last Updated** | 2026-11-14 |
| **Owner** | MIRA Core Engineering Team |

---

## 1. Versioning

MIRA uses **Semantic Versioning** (`MAJOR.MINOR.PATCH`) with Git tags in the
form `vMAJOR.MINOR.PATCH`.

| Change type | Version bump | Example |
| :--- | :--- | :--- |
| Breaking API or schema change | MAJOR | `v2.0.0` |
| New backward-compatible capability | MINOR | `v1.2.0` |
| Backward-compatible fix | PATCH | `v1.2.1` |

- The Android `versionName` is derived from the tag without the `v` prefix.
- The Android `versionCode` defaults to the GitHub Actions run number.
- The backend is deployed from `main`; it does not currently expose a public
  version endpoint. The Git tag and release notes are the authoritative record.
- Flyway migrations are forward-only. Never edit an applied migration; add a
  new `V_n` migration instead.

---

## 2. Release Channels

| Channel | Source | Artifact |
| :--- | :--- | :--- |
| Development | Feature branches / PRs | CI-built debug APK, backend test reports |
| Production backend | `main` via Render auto-deploy | Docker image built by Render |
| Android release | `v*` tag via `Android Release` workflow | Signed APK and AAB artifacts |

Production backend: `https://mira-backend-094w.onrender.com`

---

## 3. Release Checklist

### 3.1 Before Tagging

- [ ] `main` is clean and both GitHub Actions workflows are green.
- [ ] `CHANGELOG.md` has an entry for the release.
- [ ] `MIRA_API_BASE_URL` repository variable points at the production backend.
- [ ] The four Android signing secrets are configured:
  `MIRA_KEYSTORE_BASE64`, `MIRA_KEYSTORE_PASSWORD`, `MIRA_KEY_ALIAS`,
  `MIRA_KEY_PASSWORD`.
- [ ] The backend `/actuator/health` and `/actuator/health/readiness` return
  `UP`.
- [ ] Database backups are current (Neon).
- [ ] No secrets or keystores are staged (`git status`).

### 3.2 Tag and Build

```powershell
git switch main
git pull --ff-only

git tag -a v1.2.0 -m "MIRA v1.2.0"
git push origin v1.2.0
```

The `Android Release` workflow:

1. Verifies all signing secrets are present.
2. Decodes the keystore into `$RUNNER_TEMP`.
3. Builds `app-release.apk` and `app-release.aab`.
4. Verifies the APK signature with `apksigner`.
5. Uploads both artifacts to the workflow run.

### 3.3 After the Workflow

- [ ] Download the signed APK and install it on a physical device.
- [ ] Confirm the app reaches `MIRA_API_BASE_URL` over HTTPS.
- [ ] Run the smoke flow: register → login → conversation → send message →
  open memory settings.
- [ ] Verify the backend logs show no prompts, tokens, or passwords.
- [ ] Publish the GitHub release with the changelog entry and artifact links.
- [ ] Record the release in the operations log.

### 3.4 Hotfix

1. Branch from the release tag (`hotfix/v1.2.1`).
2. Apply the minimal fix and a regression test.
3. Merge to `main` after CI passes.
4. Tag `v1.2.1` and repeat section 3.2.
5. If the schema changed, use expand-and-contract and verify the previous app
   version remains compatible.

---

## 4. Rollback

- **Backend:** In Render, roll back to the previous healthy deploy. Flyway is
  forward-only, so the older application must remain compatible with the
  current schema.
- **Android:** Stop the staged rollout and publish a higher `versionCode` with
  the fix. Never attempt to reuse a published version code.
- **Database:** Restore a Neon branch only after confirming the application
  rollback is insufficient. See the backup/restore drill in
  [08-deployment-plan.md](./08-deployment-plan.md#52-backup--restore-drill).

---

## 5. Release History

| Tag | Date | Summary | Artifacts |
| :--- | :--- | :--- | :--- |
| `v1.1.0` | 2026-11-14 | Memory client and release signing | Signed APK/AAB |
| `v1.1.1` | 2026-11-14 | Container packaging and Render deployment | Signed APK/AAB |
| `v1.2.0` | 2026-11-14 | Phase 3 hardening, observability, release process (broken release build; use v1.2.1) | Signed APK/AAB |
| `v1.2.1` | 2026-11-14 | R8 keep-rule fix for the release startup crash | Signed APK/AAB |

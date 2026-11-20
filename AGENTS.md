# AGENTS.md

## 0. Purpose of this file
This repository contains an Android application. Any AI agent or automation
assistant working in this repository must read this entire file before making
CI/CD, build, signing, versioning, UI typography, or release changes, and must
follow it.

This file is a reusable, portable standard. It is NOT the memory of any
specific repository. When copied into a new repository, treat sections 1-15 as
the target state and section 16 as an empty living record to be filled after
the first verified run.

The goal is to create a safe, repeatable Android release pipeline with GitHub
Actions that:
1. Builds automatically when code is pushed.
2. Builds release artifacts for multiple Android CPU architectures.
3. Enforces Persian typography standards (Vazirmatn) when Persian text exists.
4. Creates a GitHub Release with a clear version name.
5. Ensures the new release can update an already installed previous release.
6. **Never leaks signing secrets, especially in public repositories.**
7. Automates signing through secure channels (GitHub Secrets via GitHub CLI).

If any instruction in this file conflicts with security, signing safety, or
Android update rules, stop and explain the conflict instead of guessing.

---

## 0.5. CRITICAL MANDATE: Post-Fix Prompt Evolution & Maintenance
- **Immediate Review on Issue Resolution**: Whenever the user reports an
  issue, bug, CI/CD failure, or deployment error, after resolving the issue,
  you MUST inspect this `AGENTS.md` file.
- **Prompt Gap Analysis**: Check whether the root cause and its solution are
  already covered here.
- **Proactive Prompt Update**:
  - If the issue or rule is missing, you MUST immediately append a clear,
    actionable standard or rule to this file.
  - If an existing rule is incomplete or inaccurate, you MUST update and
    refine it.
  - You may only append or refine rules. You MUST NOT rewrite, summarize, or
    reformat the whole file, and you MUST NOT modify section 16 recorded
    values except as explicitly allowed by section 16 itself.
- **Goal**: Maintain this file as a comprehensive, self-healing, master
  reference standard.

---

## 0.6. Repository Visibility & Fresh-Start Policy (MANDATORY FIRST STEP)
This repository is **PUBLIC from day one**. Therefore:

1. Run: `gh repo view --json isPrivate --jq .isPrivate` (requires `gh` CLI).
2. Store the result and apply the signing policy:
   - **Public repository (this project's state)**: ONLY Path A (GitHub
     Secrets) is allowed. Any attempt to commit keystore material to the
     repository is **FORBIDDEN** and constitutes a critical security
     vulnerability.
   - **Private repository**: Path A is preferred; Path C (committed stable
     fallback) is allowed as a convenience fallback.
3. **Fresh-Start Rule**: Since this project launches as a brand-new public
   repository, there is NO legacy committed keystore, NO prior rotation
   history, and NO migration needed. Path B (manual handoff) and Path C
   (committed fallback) are **permanently disabled** for this repository.
4. If visibility cannot be determined, the agent MUST assume **Public** and
   apply the stricter rules.

---

## 1. Non-negotiable Android update rules
For a new Android build to install over an old build, all of the following
must be true:
1. `applicationId` must remain unchanged.
2. The APK/AAB must be signed with the same signing key/certificate as the
   previously installed app.
3. The new `versionCode` must be strictly greater than the old `versionCode`.
4. If the previous app was installed from Google Play using Play App Signing,
   direct GitHub APK updates may require the final app signing key, not only
   the upload key. If this situation is detected, document it and ask the
   maintainer.
5. If the previous app was installed as a debug build, a release build signed
   with a different key will not update it unless the same debug key is used.

The agent must never do anything that breaks these rules unless explicitly
instructed by a human maintainer.

---

## 2. Agent mission
The agent must implement or repair the following pipeline:
- Trigger on push to the main branch and on version tags.
- Build release APKs for relevant ABIs.
- Build an AAB if the project supports it.
- Sign release artifacts with a stable release keystore stored in GitHub
  Secrets (never committed).
- Generate a monotonic `versionCode`.
- Generate a human-readable `versionName`.
- Apply Vazirmatn font for Persian/RTL text.
- Automate the generation and injection of signing secrets securely via
  `gh secret set`.
- Publish ONLY release artifacts to GitHub Releases as stable releases.
- Ensure the workflow fails clearly if signing configuration is missing.

The agent should prefer small, reviewable changes and must not introduce
unrelated refactors.

---

## 3. Repository discovery checklist
Before changing anything, the agent must inspect the repository and identify:
1. The Android application module, usually `app`.
2. Whether Gradle files use Kotlin DSL or Groovy DSL.
3. Whether Jetpack Compose or XML layouts are used.
4. Existing `applicationId`.
5. Existing `versionCode` and `versionName` strategy.
6. Existing signing configurations.
7. Existing product flavors.
8. Existing GitHub Actions workflows.
9. Existing `.gitignore` rules for keystores and secrets.
10. Whether `gradlew` exists, is executable, and its jar passes integrity
    checks (see 15.1 / 15.3).
11. Whether native libraries exist, and for which ABIs.
12. **Repository visibility (Public/Private) — see 0.6.**

If multiple application modules exist, the agent must ask which module should
be released unless the repository clearly indicates one.

---

## 4. UI & Typography Standards (Persian Localization)
Whenever the application includes Persian/Farsi text or supports RTL layouts,
the agent MUST integrate and use the **Vazirmatn** font as the primary
typeface to ensure excellent readability and correct layout flow.

### 4.1. Jetpack Compose Implementation
If using Compose, the agent must add Vazirmatn font files (`.ttf` or `.otf`)
to `app/src/main/res/font/` (e.g., `vazirmatn_regular.ttf`,
`vazirmatn_bold.ttf`, `vazirmatn_medium.ttf`) and configure the `Typography`:

```kotlin
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val VazirmatnFontFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold)
)

val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = VazirmatnFontFamily, fontWeight = FontWeight.Bold, fontSize = 57.sp),
    headlineLarge = TextStyle(fontFamily = VazirmatnFontFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp),
    titleLarge = TextStyle(fontFamily = VazirmatnFontFamily, fontWeight = FontWeight.Medium, fontSize = 22.sp),
    bodyLarge = TextStyle(fontFamily = VazirmatnFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = VazirmatnFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = VazirmatnFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    // Apply to all necessary text styles
)
```

### 4.2. XML Layout Implementation
If using XML, the agent must create a custom `TextView` style or use
`android:fontFamily="@font/vazirmatn_regular"` in `themes.xml` as the default
font family for the application theme.

### 4.3. RTL Enforcement
Ensure `android:supportsRtl="true"` is in `AndroidManifest.xml` and layouts
use `start`/`end` instead of `left`/`right`.

### 4.4. Global Coverage Requirement
ALL `Text` composables must render with Vazirmatn, including ones that set an
ad-hoc `fontSize` without a Typography style. The agent MUST additionally set
the default text style at the theme root so inheritance covers ad-hoc texts:

```kotlin
CompositionLocalProvider(
    LocalTextStyle provides TextStyle(fontFamily = VazirmatnFontFamily)
) { content() }
```

### 4.5. Binary Asset Protocol (human-in-the-loop, base64 edition)
1. **Assumption:** in web IDEs (Google AI Studio), the agent can NEVER commit
   a valid binary `.ttf` (see 15.5): direct `.ttf` commits are silently
   re-encoded as text and crash the app at first font render (Cartino crash
   incident, 2026-08-07). Asking the maintainer to upload `.ttf` files is
   therefore a BROKEN protocol and MUST NOT be used.
2. The agent **MUST NOT silently skip** font integration and **MUST NOT ship
   `FontFamily.Default`** while Persian text exists. This is an uncertainty
   per section 13: **stop and ask the maintainer.**
3. The agent MUST issue a precise "Font Request (base64 edition)", e.g.:
   > «برای تایپوگرافی فارسی به فونت **وزیرمتن (Vazirmatn)** نیاز است. لطفاً
   > نسخه رسمی را دانلود کنید و روی سیستم خودتان سه فایل متنی base64 بسازید:
   > `base64 -w 0 vazirmatn_regular.ttf > vazirmatn_regular.b64` (و همین‌طور
   > برای medium و bold). سپس **فقط فایل‌های `.b64`** را در مسیر
   > `app/fontsrc/` آپلود کنید (هرگز `.ttf`) و پیام «fonts placed» بدهید.»
4. After confirmation, the agent MUST wire the crash-proof pipeline:
   - `decodeVazirmatnFonts` Gradle task (before `preBuild`) decoding
     `app/fontsrc/*.b64` into `app/src/main/res/font/*.ttf`;
   - `.gitignore` line `app/src/main/res/font/*.ttf` (decoded binaries are
     never committed);
   - `VazirmatnFontFamily` + full Typography (4.1) and global `LocalTextStyle`
     coverage (4.4);
   - verify the build resolves `R.font.*` and the preview renders Vazirmatn.
5. Downloadable-font XML remains a fallback ONLY with explicit maintainer
   approval; any deviation must be reported per section 14 — never silently.

**RED RULE:** silently skipping section 4, or leaving `FontFamily.Default`
while Persian text exists, is a policy violation equal to a security bypass.

---

## 5. Versioning policy
The project uses two Android version fields:
- `versionName`: human-readable version, such as `1.2.3`.
- `versionCode`: internal integer used by Android for updates. It must always
  increase.

### 5.1. `versionName`
- If the workflow is triggered by a Git tag like `v1.2.3`, use `1.2.3` as
  `versionName`.
- If the workflow is triggered by a push to `main` or `master`, use a
  continuous version such as: `0.1.<commit-count>` (e.g., `0.1.182`).

### 5.2. `versionCode`
Use a monotonic integer formula based on commit count plus a base offset:
```text
versionCode = BASE_VERSION_CODE + commit-count
```
Default example: `BASE_VERSION_CODE: "100000"`.

If the project already has a known production `versionCode`, the agent must
choose `BASE_VERSION_CODE` high enough so that all future generated values are
greater than the current production value.

Rules:
- `versionCode` must be an integer.
- `versionCode` must not exceed `2100000000`.
- The agent must never reduce `versionCode`.

---

## 6. Gradle requirements
The agent must modify the Android application module Gradle configuration to
support CI-driven versioning and signing. Do not enable minification,
shrinking, or obfuscation if the project is not already configured for it.

### 6.1. Accept CI-provided version values
The build must accept:
```bash
./gradlew assembleRelease -PversionCode=123 -PversionName=1.0.0
```

Kotlin DSL reference:
```kotlin
val ciVersionCode = (findProperty("versionCode") as String?)?.toIntOrNull()
val ciVersionName = (findProperty("versionName") as String?)

android {
    defaultConfig {
        if (ciVersionCode != null) {
            versionCode = ciVersionCode
        }
        if (!ciVersionName.isNullOrBlank()) {
            versionName = ciVersionName
        }
    }
}
```

For Groovy DSL, implement the equivalent behavior.

### 6.2. Release signing from environment variables (Secrets only)
Required environment variables:
`SIGNING_STORE_FILE`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`,
`SIGNING_KEY_PASSWORD`.

Kotlin DSL reference:
```kotlin
val hasReleaseSigning = !System.getenv("SIGNING_STORE_FILE").isNullOrBlank()

android {
    signingConfigs {
        maybeCreate("release").apply {
            val storePath = System.getenv("SIGNING_STORE_FILE")
            if (!storePath.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            } else {
                // IMPORTANT: For public repos, DO NOT fall back to debug keystore.
                // Fail at EXECUTION TIME ONLY for release-producing tasks (e.g., via a
                // VerifyReleaseSigningTask wired to preReleaseBuild/assembleRelease/bundleRelease),
                // NOT during Gradle configuration so debug builds, unit tests, and IDE/web previews
                // remain functional without release secrets.
                throw GradleException(
                    "Release signing is missing. Set SIGNING_STORE_FILE, " +
                    "SIGNING_STORE_PASSWORD, SIGNING_KEY_ALIAS, SIGNING_KEY_PASSWORD."
                )
            }
        }
    }
    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}
```

**Execution-Time Guard Rule (Refinement to 6.2):** Missing release signing
variables MUST NOT throw during Gradle configuration time, because doing so
breaks debug builds, unit tests, and web/IDE previews. Instead, leave
`buildTypes.release.signingConfig` unset when variables are absent and fail at
**task execution time** (e.g., via a configuration-cache-safe verification task
attached to `preReleaseBuild`, `assembleRelease`, `bundleRelease`, and split
release packaging tasks) before any release APK/AAB is produced. Fallback to
`debug.keystore` remains strictly forbidden.

### 6.3. ABI splits
Default ABI set: `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`. Also generate a
universal APK.

Kotlin DSL reference:
```kotlin
android {
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }
}
```

If the project contains native libraries only for some ABIs, adjust the
include list to match supported ABIs.

---

## 7. Automated Secret Provisioning & Keystore Generation
**CRITICAL MANDATE:** The user should not have to manually generate keystores
or encode them. The agent MUST automate this process securely.

If the project does not have a configured release keystore, the agent MUST
execute the following steps:

### Step 1: Generate Keystore Locally
Use the local JDK `keytool` and `openssl` to generate a strong release
keystore in a temporary directory OUTSIDE any repository clone (e.g., `cd ~`):

```bash
export MSYS_NO_PATHCONV=1  # Required on Windows Git Bash to prevent path mangling of -subj
STORE_PASS=$(openssl rand -hex 16)
KEY_PASS="$STORE_PASS"  # Single password avoids BadPaddingException

openssl req -x509 -newkey rsa:2048 -keyout key.pem -out cert.pem -days 10000 -nodes \
  -subj "/CN=Android Release/OU=Dev/O=MyOrg/L=City/ST=State/C=IR"

openssl pkcs12 -export -out release.keystore -inkey key.pem -in cert.pem \
  -name release_key -passout "pass:$STORE_PASS"
```

### Step 2: Encode to Base64
```bash
base64 -w 0 release.keystore > keystore_base64.txt
```

### Step 3: Inject Secrets via GitHub CLI (Path A — ONLY allowed path for this public repo)
`gh` (GitHub CLI) MUST be installed and authenticated. Execute:

```bash
gh secret set KEYSTORE_BASE64  -R OWNER/REPO < keystore_base64.txt
gh secret set KEYSTORE_PASSWORD -R OWNER/REPO -b"$STORE_PASS"
gh secret set KEY_ALIAS         -R OWNER/REPO -b"release_key"
gh secret set KEY_PASSWORD      -R OWNER/REPO -b"$STORE_PASS"
gh secret list -R OWNER/REPO
```

If successful, **immediately**:
1. Move `release.keystore` and a copy of `$STORE_PASS` to a secure password
   manager or encrypted drive (this is the ONLY backup — losing it means
   losing the ability to update installed users). Store the key and the
   password in SEPARATE locations.
2. Delete all temporary local files:
   ```bash
   rm -f release.keystore keystore_base64.txt key.pem cert.pem PASS.txt
   ```

### ⛔ Forbidden Paths (for this public repository)
- **Path B (Manual Handoff)**: Creating `__SECRETS_TO_COPY.txt` is forbidden
  for new public projects. Use `gh secret set` directly.
- **Path C (Committed Stable Fallback)**: Committing `ci.keystore.base64`
  and `ci-signing.env` to a public repository is a **CRITICAL SECURITY
  VULNERABILITY** — any person on Earth can extract the key and sign
  malicious APKs under your identity. **Absolutely forbidden.**

---

## 7.5. Fresh-Start Launch Checklist (for first-ever release)
Before tagging `v1.0.0` in a brand-new public repository, the agent MUST:
1. Confirm `applicationId` is final and will never change.
2. Confirm the release keystore is backed up in a password manager.
3. Confirm all 4 GitHub Secrets are set via `gh secret list`.
4. Run a dry-run: `gh workflow run android-release.yml` and watch with
   `gh run watch` to verify the workflow succeeds end-to-end.
5. Confirm `.gitignore` contains: `*.keystore`, `*.jks`, `*.pem`,
   `keystore_base64.txt`, `ci.keystore.base64`, `ci-signing.env`,
   `__SECRETS_TO_COPY.txt`, `local.properties`.
6. Confirm commit history contains ZERO traces of keystore material (if this
   is a truly fresh repo, this is automatic).
7. Tag `v1.0.0` only after the dry-run succeeds with a green "production
   mode" log line.

---

## 8. GitHub Actions requirements
The agent must create or update this workflow file:
`.github/workflows/android-release.yml`

The following YAML is the verified reference implementation. Implement it
verbatim; only adapt artifact paths if the application module is not `app`.

```yaml
name: Android Build and Release
on:
  push:
    branches: [main, master]
    tags: ["v*"]
  workflow_dispatch:
permissions:
  contents: write
concurrency:
  group: android-release-${{ github.ref }}
  cancel-in-progress: false
env:
  JAVA_VERSION: "17"
  BASE_VERSION_CODE: "100000"
jobs:
  build-and-release:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout repository
        uses: actions/checkout@v4
        with:
          fetch-depth: 0
      - name: Set up JDK
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: ${{ env.JAVA_VERSION }}
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          validate-wrappers: false
      - name: Prepare signing configuration
        run: |
          set -euo pipefail
          if [ -n "${KEYSTORE_BASE64:-}" ]; then
            echo "Using GitHub Secrets keystore (production mode)."
            echo "$KEYSTORE_BASE64" | base64 -d > "$RUNNER_TEMP/release.keystore"
            echo "SIGNING_STORE_FILE=$RUNNER_TEMP/release.keystore" >> "$GITHUB_ENV"
            echo "SIGNING_STORE_PASSWORD=$KEYSTORE_PASSWORD" >> "$GITHUB_ENV"
            echo "SIGNING_KEY_ALIAS=$KEY_ALIAS" >> "$GITHUB_ENV"
            echo "SIGNING_KEY_PASSWORD=$KEY_PASSWORD" >> "$GITHUB_ENV"
          else
            echo "::error::GitHub Secrets are required for this public repository."
            echo "::error::Set KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD via 'gh secret set'."
            echo "::error::Committing keystore material to a public repo is FORBIDDEN."
            exit 1
          fi
        env:
          KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
      - name: Calculate version
        id: version
        run: |
          set -euo pipefail
          COMMITS=$(git rev-list --count HEAD)
          VERSION_CODE=$((BASE_VERSION_CODE + COMMITS))
          if [[ "$GITHUB_REF" == refs/tags/v* ]]; then
            TAG="${GITHUB_REF#refs/tags/}"
            VERSION_NAME="${TAG#v}"
          else
            VERSION_NAME="0.1.${COMMITS}"
          fi
          echo "version_code=$VERSION_CODE" >> "$GITHUB_OUTPUT"
          echo "version_name=$VERSION_NAME" >> "$GITHUB_OUTPUT"
        env:
          BASE_VERSION_CODE: ${{ env.BASE_VERSION_CODE }}
      - name: Build release artifacts
        run: |
          set -euo pipefail
          GRADLE_CMD=""
          if [ -f ./gradlew ]; then
            chmod +x ./gradlew
            if ./gradlew --version >/dev/null 2>&1; then
              GRADLE_CMD="./gradlew"
            else
              echo "Committed Gradle wrapper is broken - falling back to system Gradle."
            fi
          fi
          if [ -z "$GRADLE_CMD" ]; then
            GRADLE_CMD="gradle"
          fi
          echo "Using Gradle command: $GRADLE_CMD"
          "$GRADLE_CMD" assembleRelease bundleRelease \
            -PversionCode=${{ steps.version.outputs.version_code }} \
            -PversionName=${{ steps.version.outputs.version_name }} \
            --stacktrace
      - name: Verify release artifacts exist
        run: |
          set -euo pipefail
          find app/build/outputs -type f \( -name "*.apk" -o -name "*.aab" \) | sort
          find app/build/outputs -type f \( -name "*.apk" -o -name "*.aab" \) | grep -q . || {
            echo "No APK or AAB release artifacts found."
            exit 1
          }
      - name: Publish GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          tag_name: v${{ steps.version.outputs.version_name }}
          target_commitish: ${{ github.sha }}
          name: v${{ steps.version.outputs.version_name }}
          generate_release_notes: true
          prerelease: false
          files: |
            app/build/outputs/apk/release/*.apk
            app/build/outputs/bundle/release/*.aab
```

Rules:
- The agent must adapt artifact paths if the application module is not `app`
  or if product flavors change output paths.
- The final workflow MUST ONLY upload `release` artifacts. Debug artifacts are
  strictly forbidden from release attachments.
- The workflow MUST NOT read any keystore material from the repository.
  Signing must come exclusively from GitHub Secrets.
- Do not regress: `validate-wrappers: false`, the Gradle health-check
  launcher, the signing priority order, and `prerelease: false` are mandatory
  (see section 15).

---

## 9. Secrets and security requirements
The agent must require these GitHub Actions repository secrets:
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

The agent must never:
- Commit a keystore file.
- Commit a `.jks`, `.keystore`, or `.pem` private key file.
- Commit base64-encoded keystore content (`ci.keystore.base64`).
- Commit signing environment files (`ci-signing.env`).
- Print secret values in logs.
- Echo base64 keystore content.
- Store secrets in `gradle.properties`, `local.properties`, or source files.
- Use a production keystore in pull request workflows.
- Create fake secrets and pretend they are valid.
- Disable secret validation to make a workflow pass.
- Generate a new keystore inside the CI workflow at build time (this would
  break update compatibility for all installed users).

The agent must add or verify `.gitignore` entries such as:
```gitignore
*.keystore
*.jks
*.pem
keystore.properties
local.properties
__SECRETS_TO_COPY.txt
release.keystore
keystore_base64.txt
key.pem
cert.pem
ci.keystore.base64
ci-signing.env
```

---

## 10. Pull request safety
The release workflow must not require secrets for pull requests from forks.
If the repository needs CI checks for pull requests, the agent should create
or maintain a separate workflow such as `.github/workflows/android-ci.yml`
that only performs non-release builds (e.g., `gradle assembleDebug` with the
same fallback launcher as section 8).

---

## 11. Verification checklist
After implementing changes, the agent must verify:
1. Vazirmatn font is integrated and applied to Typography/Themes (if Persian
   text exists).
2. The Gradle build files parse correctly.
3. Gradle execution follows 15.1/15.3: wrapper is optional and must pass a
   `./gradlew --version` health-check; system `gradle` fallback is always
   available.
4. The GitHub Actions YAML is valid.
5. `versionCode` is monotonic and never reduced.
6. Release signing uses environment variables with the priority order from
   15.2.
7. The workflow fails clearly if no signing configuration exists.
8. `.gitignore` prevents committing secrets.
9. Existing `applicationId` has not changed.
10. ABI outputs are generated as expected.
11. Secrets are provisioned via Path A only (`gh secret set`), and the chosen
    path is reported to the maintainer.
12. **Repository visibility is detected and respected (section 0.6).**
13. **Zero keystore material, base64-encoded or otherwise, exists in the
    repository tree or any commit in history.**
14. **Zero binary assets (e.g., `.ttf`) exist in the repository tree; font
    binaries are materialized at build time per 4.5/15.5.**

---

## 12. Definition of Done
The task is complete when:
- [ ] Pushing to `main` or `master` builds the Android app automatically.
- [ ] Every push to `main` publishes a stable GitHub Release that becomes
      `Latest`.
- [ ] Version tags starting with `v` create releases named after the tag.
- [ ] Release APKs are built for the selected ABIs plus a universal APK.
- [ ] Artifacts are uploaded to a GitHub Release (NO debug APKs).
- [ ] The release uses a version name derived from tag or commit count.
- [ ] The `versionCode` is higher than previous builds.
- [ ] The app is signed with one stable keystore stored in GitHub Secrets.
- [ ] Vazirmatn font is correctly applied to Persian/RTL texts.
- [ ] Existing installed app can update if the same keystore and
      applicationId are used.
- [ ] No secret is present in source control or logs.
- [ ] The workflow fails clearly when no signing configuration exists.
- [ ] **Zero keystore material is committed to the repository.**

---

## 13. Behavior when uncertain
If the agent encounters any of the following, it must stop and ask a human
maintainer:
- Existing app already published with unknown signing key.
- Existing `versionCode` higher than the proposed future values.
- Multiple application modules with unclear release target.
- Product flavors with different application IDs.
- Evidence that changing the keystore would affect real users.
- Unclear whether Google Play App Signing is used.
- **Ambiguity about repository visibility (Public vs Private).**
- **Evidence that a private-repo Path C keystore has ever been committed to
  a public fork or public clone of this repository.**
- **Any prior keystore material in commit history of this repository** —
  requires the leak remediation sequence in 15.4 before any release.
- **Inability to add a required binary asset (e.g., font) in a web IDE** —
  follow the base64 protocol in 4.5; never silently skip.

Do not guess in security-critical or release-critical situations.

---

## 14. Final agent summary requirement
After completing the work, the agent must output a summary containing:
1. Files changed/created (including UI/Typography updates).
2. Status of Secret Provisioning (Path A confirmed; B and C forbidden).
3. Current versioning policy.
4. Confirmation that Vazirmatn is applied (or not applicable).
5. **Detected repository visibility (Public) and signing policy applied.**
6. Any warnings about existing users, signing, or versionCode continuity.
7. Confirmation that commit history is clean (no leaked material).

---

## 15. Hardened CI Rules

### 15.1 Gradle Wrapper Rule
- This repository may or may not contain the Gradle wrapper (`gradlew`,
  `gradle/wrapper/*`).
- Workflows MUST NOT assume `./gradlew` exists. Always use the health-check
  launcher:
  `if [ -f ./gradlew ]; then chmod +x ./gradlew; if ./gradlew --version >/dev/null 2>&1; then GRADLE_CMD="./gradlew"; else GRADLE_CMD="gradle"; fi; else GRADLE_CMD="gradle"; fi`
- Keep an existing valid wrapper if present. Only commit wrapper files from a
  trusted environment where the jar integrity can be verified; never from a
  web IDE.

### 15.2 No Throwaway Keys Rule
- CI must NEVER generate a new keystore at build time. A per-run random key
  breaks update compatibility forever.
- Signing priority: (1) GitHub Secrets `KEYSTORE_BASE64` etc., (2) otherwise
  **FAIL**. No committed fallback is permitted in this public repository.
- Once a signing identity is established in a repository, it is FROZEN: never
  rotate, regenerate, or delete it, **except when a leak is confirmed (see
  15.4).**
- If a previous release was signed with a lost throwaway key, warn the
  maintainer that installed users must reinstall once; all future releases
  must use one stable key.

### 15.3 Binary Files Rule (web-IDE limitation)
- NEVER commit binary build tooling files (e.g.,
  `gradle/wrapper/gradle-wrapper.jar`) from a web environment; they get
  corrupted and fail GitHub's wrapper validation.
- CI must build with the runner's system Gradle when the wrapper is absent or
  broken.
- Keep `validate-wrappers: false` in `gradle/actions/setup-gradle`.

### 15.4 Public Repository Security Rule
- In a **public repository**, committing keystore material to Git is a
  CRITICAL security vulnerability equivalent to publishing the signing key
  to the Internet. Even after `git rm`, the key remains in commit history
  and can be extracted by anyone.
- Required remediation sequence when a leak is detected in a public repo:
  1. Generate a NEW keystore with fresh credentials.
  2. Upload the new keystore to GitHub Secrets via `gh secret set`.
  3. Delete the old keystore files from the working tree.
  4. Rewrite commit history to purge the leaked files (e.g.,
     `git filter-repo --invert-paths --path <file>` or BFG Repo-Cleaner).
  5. Force-push the rewritten history.
  6. Warn all users that they must reinstall once; subsequent updates will
     work normally with the new key.
  7. Record the rotation in section 16 with the rotation date and reason.
- **Rule 15.4b**: If this project is ever published to Google Play or becomes
  commercial, FIRST migrate the signing identity to GitHub Secrets or Play
  App Signing and rotate the key before any other change.

### 15.5 Binary Assets Rule (web-IDE binary corruption)
- Binary assets (e.g., .ttf fonts) committed through AI Studio are silently
  corrupted: the web commit pipeline re-encodes binary bytes as text (GitHub
  then renders them as multi-line text; sizes inflate; OS font validators
  reject them; Android crashes at first font render).
- NEVER commit binary asset files from AI Studio. Only all-text strategies
  are allowed for fonts and other binaries:
  (a) commit base64 TEXT files + a Gradle decode step that materializes the
      real binary at build time (decoded outputs are gitignored), or
  (b) downloadable fonts (font-provider XML), or
  (c) CI-side fetch from a pinned official URL with checksum verification.
- A release that crashes on launch right after adding a binary asset is
  presumed to be this corruption until proven otherwise.
- PROVEN DEFAULT (2026-08-07, Cartino v0.1.9): option (a) base64+decode is the
  only asset pipeline verified end-to-end in AI Studio (device-verified
  Vazirmatn, zero binaries in git). Direct `.ttf` commits caused a launch
  crash and are classified as a security-adjacent defect.

---

## 16. Verified State Record (living, per-repository)
This section is a living record, NOT hardcoded project memory. It works in
ANY repository with zero edits:

1. BEFORE first verification: this section is empty and imposes no
   repository-specific constraints. Treat sections 1-15 as the target state.
2. AFTER the first successful CI run AND a verified on-device install/update
   test, the agent MUST fill the template below with this repository's real
   values, and keep it updated whenever the maintainer approves a change.
3. FROZEN once recorded: no agent may change the recorded applicationId,
   signing identity, versionCode base, or release channel policy without
   explicit maintainer approval. An established signing identity must NEVER
   be rotated, regenerated, or deleted, **except under section 15.4 in
   response to a confirmed leak.**

**Template (to be filled after first verified release):**
```
- Repository: <owner>/<repo>
- Visibility: Public
- applicationId (frozen): <applicationId>
- Signing identity: GitHub Secrets KEYSTORE_* (created <YYYY-MM-DD>)
- versionCode formula: BASE_VERSION_CODE=<N> + commit count
- Release channel: stable per push (prerelease: false)
- Gradle execution: <wrapper|system gradle fallback>
- Wrapper policy: <present|forbidden due to web-IDE binary corruption>
- Font pipeline: <none|Vazirmatn via base64 TEXT + build-time decode|downloadable>
- Last verified OTA update test: <from version> -> <to version>, <YYYY-MM-DD>
- Key rotation history: (empty unless leak remediation occurred)
```

*(This section is intentionally blank until the first `v1.0.0` release is
verified on a real device. The agent must NOT pre-fill it.)*

---

## 17. Single-Writer Rule (AI Studio as source of truth)
- This project is developed exclusively in Google AI Studio. AI Studio is the
  ONLY place that creates commits and pushes to GitHub. Every push to `main`
  automatically publishes a release; no manual release steps are needed.
- Local clones are READ-ONLY: only `gh` commands (secrets, runs, releases) and
  inspection are allowed. `git commit`, `git push`, and history rewrites from
  any local machine are FORBIDDEN; any commit created outside AI Studio
  permanently diverges the workspace (GitHub → AI Studio sync is impossible).
- Version tags must be created server-side (GitHub Web UI tag creation, or
  `gh release create --target main`), never via local `git push --tags`.
- Do NOT re-import this GitHub repository into a new AI Studio project (AI
  Studio demands a fresh repository and creates a vicious cycle); always
  continue in the original connected AI Studio project.
- If the remote ever diverges from AI Studio's history, a one-time
  reconciliation (force-resetting `main` to AI Studio's last known commit)
  requires explicit maintainer approval and must not be repeated.

---

## 18. Asset Integrity & Hygiene Rules (Anti-Fake & Validation Standards)
Per section 0.5, the following rules are strictly enforced across all build,
test, and release stages:

### 18.1. Zero Fake/Synthetic Assets Rule
- The agent MUST NEVER create or substitute synthetic, placeholder, or fake
  binary assets (fonts, keystores, TTS models, images, or audio files) in order
  to make a build or test pass.
- If a required binary asset is missing from the repository, the agent MUST
  stop, report the exact missing asset to the maintainer, and wait for the
  official asset via the approved text/Base64 or user-selection protocol.

### 18.2. Decoded Binary Asset Validation Rule
- Every build-time `base64 -> binary` pipeline (including
  `decodeVazirmatnFonts`) MUST validate both the Base64 payload and the decoded
  output's magic bytes/signature, minimum size, and internal container/table
  structure before wiring it into build outputs.
- Any invalid, empty, truncated, or corrupted decoded asset MUST fail the build
  immediately with a clear, actionable message naming the broken file, error
  type, and required maintainer action; silent bypass or fallback (e.g., to
  `FontFamily.Default`) is strictly forbidden.

### 18.3. Temporary Test Artifact Cleanup Rule
- Any temporary file created exclusively for automated verification or testing
  MUST be deleted before finishing the same phase or turn.
- Before reporting completion, the agent MUST inspect the repository tree and
  explicitly prove that zero temporary, fake, or untracked binary artifacts
  remain.

---

## 19. Hardened CI Alignment & SDK License Rules
Per section 0.5, the following CI environment rules apply to all workflows:

### 19.1. CI Toolchain Alignment Rule
- The workflow's `JAVA_VERSION` and pinned Gradle version (`setup-gradle`) MUST
  be aligned with the verified build environment where compilation and unit
  tests passed, and MUST satisfy the active AGP's minimum Gradle requirement.
- Pinning the Gradle version in `setup-gradle` supplements—and NEVER replaces—the
  wrapper health-check launcher (`./gradlew --version` check with fallback to
  system `gradle` and `validate-wrappers: false` per section 15.1 / 15.3).

### 19.2. Android SDK License Acceptance Rule
- When `compileSdk` targets an API level with a minor revision or newer platform
  components, CI workflows MUST include a pre-build SDK license acceptance step
  (`sdkmanager --licenses`) without hardcoding exact SDK component revisions,
  allowing AGP to install the required platform and build-tools automatically.

---

## 20. Persian PDF Extraction Quality, ZWNJ Restoration & Cache Versioning Rules
Per section 0.5, real-device validation of Persian PDF extraction established the
following permanent standards:

### 20.1. Real-Book Validation & Multi-Candidate Scoring Rule
- Persian PDF extraction MUST NOT rely solely on synthetic text or a single
  `PDFTextStripper` pass.
- Primary extraction (`sortByPosition = true` with kerning tolerances tuned to
  preserve ZWNJ) MUST be evaluated by a deterministic readability scorer; when
  weak or corrupted signals appear (e.g., broken prefixes, guillemet mismatch,
  digit-letter adhesion, or replacement chars), a stream-order fallback pass
  (`sortByPosition = false`) MUST be evaluated and only the higher-scoring
  candidate kept.

### 20.2. High-Confidence Reading-Order Repair Rule
- RTL visual/word-order repair and guillemet mirroring (`»...«` -> `«...»`) MUST
  enforce a strict high-confidence gate: never invert or scramble valid or
  ambiguous prose lines when structural reversal signals are absent or the score
  margin is insufficient.

### 20.3. Morphological ZWNJ & Spacing Restoration Rule
- Morphological restoration of ZWNJ (`می‌`/`نمی‌`, shifted `آ` compounds,
  `ها`/`های`/`هایی`/`ترین`/`تر`, `هٔ`, enclitics) and digit-to-letter spacing
  MUST operate as pure, deterministic Kotlin with explicit exception-word
  protection (`میز`, `میدان`, `میل`, `میهن`, `نمک`, etc.) and zero binary models.

### 20.4. Raw/Cleaned Diagnostic Storage & Cache Schema Versioning Rule
- Per-page caches MUST store both the selected `rawText` and `cleanedText` alongside
  an explicit extraction schema version (`cacheSchemaVersion`).
- Whenever extraction logic upgrades cleanly in a breaking or diagnostic way,
  increment the schema version, flag legacy caches (`version < current`) as
  needing re-extraction (`needsReextraction`), and provide an in-place
  re-extraction action against the locally stored PDF without forcing the user to
  re-pick the document via SAF.

---

## 21. Glyph/Ligature Integrity, Layout Metadata & Structure-Aware Reading Plan Rules
Per section 0.5, Phase 5 structural and typographic validation established the
following permanent standards:

### 21.1. Atomic Ligature & Trailing-Glyph Integrity Rule (`لا` Ligature)
- Primary PDF text extraction MUST process each PDF `TextPosition` as an atomic
  glyph unit and sort line units strictly by physical horizontal coordinates for
  RTL lines (while keeping embedded LTR/digit spans ordered ascendingly), rather
  than delegating multi-character ligature glyphs (`لا`, `\uFEFB`, `\uFEFC`) to
  Bidi-reversed string buffers that invert `لا` into `ال` (`میلانی` → `میالنی`).
- Post-extraction restoration MUST NEVER perform blind global `"ال" -> "لا"`
  replacements; legitimate Arabic/Persian `ال` tokens (`الله`, `الف`, `الان`,
  `الم`, `سالم`, `تالیف`) MUST remain protected unless explicit glyph-inversion
  metadata or an unambiguous dictionary match applies.
- Detached single trailing Persian letters (`پسر`, `دین`, `تحرک`) caused by
  kerning splits MUST be resolved via horizontal gap measurement against normal
  word-space thresholds while preserving valid standalone single-letter words
  (such as `و`).

### 21.2. Line Layout & Typography Metadata Extraction Rule
- Page extraction MUST capture physical line metadata alongside text, including
  baseline `Y` coordinate, `minX`/`maxX` bounds, median/max font size (`sp`),
  bold font descriptor/name detection, line character count, and page zone
  (`TOP`, `MIDDLE`, `BOTTOM`), enabling downstream structure analysis without
  re-parsing rendered pages.

### 21.3. Labeled Structural Block Preservation Rule (No Silent Stripping)
- Extracted pages MUST preserve structural roles as typed `DocumentBlock`s
  (`PAGE_TITLE`, `SUBTITLE`, `TOC_ENTRY`, `CENTERED_SHORT_LINE`,
  `BODY_PARAGRAPH`, `FOOTNOTE`, `HEADER`, `FOOTER`, `PAGE_NUMBER`,
  `REFERENCE_MARK`, `EMPTY`).
- Repeating headers, footers, page numbers, footnotes, and centered standalone
  lines (e.g., dedications/poetry) MUST be explicitly tagged and stored in the
  versioned cache—NEVER silently deleted or flattened into an undifferentiated
  body stream.

### 21.4. Structure-Aware Reading Plan Contract Rule
- Before TTS synthesis, a user-selected page range and persistent `ReadingSettings`
  MUST be compiled deterministically into an ordered `ReadingPlan` of
  role-tagged `ReadingItem`s (with per-role pause durations such as
  `SHORT`/`MEDIUM`/`LONG` after titles, TOC announcements such as `فهرست کتاب`
  with `عنوان، صفحهٔ …`, and explicit `SkippedReadingBlock` records explaining
  skipped metadata) so the downstream TTS runtime receives structured cues.

---

## 22. Word Boundary Calibration & Asymmetric Spacing Rule (Phase 6)
To prevent real spaces between Persian words from collapsing into ZWNJ or disappearing entirely (e.g. «درآنروزگار», «برایاجرایقانونی», «رااجباریمیکرد», «وبسیاری», «بهرغم», «هنوزشناسنامه», «بیستونهرروزه»), the extraction and text cleaning pipeline MUST adhere to the following standards:

### 22.1. Multi-Tier Reference Space Width Calibration
- Inter-glyph horizontal gap analysis MUST calibrate the reference space width (`spaceWidthPt`) in the following priority order:
  1. Font space character advance inspection from `TextPosition.widthOfSpace` when sufficient valid glyphs exist.
  2. Dominant positive gap clustering fallback across the line segments.
  3. Standard typographic fraction of the median font size (`avgFont * 0.28f`).

### 22.2. Asymmetric Gap Classification (3-Bucket Rule)
- Inter-segment visual gaps MUST be classified into 3 distinct buckets:
  1. **Bucket 1 (Tight Kerning):** `gap <= tightKerningLimit` → Strict JOIN (`""`).
  2. **Bucket 2 (Intermediate Zone):** `tightKerningLimit < gap < fullSpaceThreshold` → ZWNJ (`\u200C`) permitted **ONLY** when an unmistakable Persian morphological affix pattern matches (e.g., prefix `می`/`نمی`, suffixes `ها`/`های`/`تر`/`ترین`, high-frequency compound prefixes like `نوش`/`خوش`/`هم`).
  3. **Bucket 3 (Full Space):** `gap >= fullSpaceThreshold` → Full Word Space (`" "`).
- **Conservative Asymmetry Mandate:** In ANY ambiguous or unconfirmed condition within intermediate gaps, the decision MUST strictly default to FULL SPACE (`" "`) — never ZWNJ and never glued.

### 22.3. Lexical Safety Net & Protected Exception List
- A robust lexical post-processor (`PersianWordBoundaryRestorer`) MUST restore known glued multi-word expressions («درآنروزگار» → «در آن روزگار», «برایاجرایقانونی» → «برای اجرای قانونی», «رااجباریمیکرد» → «را اجباری می‌کرد», «وبسیاری» → «و بسیاری», «بهرغم» → «به رغم», «هنوزشناسنامه» → «هنوز شناسنامه», «بیستونهرروزه» → «بیستون هر روزه»).
- A comprehensive protected exception list («میدان», «میان», «میوه», «میز», «میزبان», «میل», «میلاد», «میهن», «میخ», «مینا», «نمک», «نماز», «نمونه», «نمودار», «نمایش», «تنها», «انتها», «اشتها», «بها», «رها», «گواه», «شاه», «آشیانه», «شناسنامه», «بهار» etc.) MUST NEVER be split by prefix rules.

### 22.4. Scorer Penalty & Cache Version 4 Upgrade
- Extraction candidate evaluation (`PersianExtractionScorer`) MUST heavily penalize abnormally long tokens (>16 characters without space), glued function words, and excessive ZWNJ:space ratios, while favoring natural word spacing distributions.
- Cache schema version MUST be upgraded to `4`, serializing `PageSpacingMetrics` (`referenceSpaceWidthPt`, `referenceSource`, `medianGapPt`, `fullSpaceCount`, `zwnjCount`, `gluedJoinCount`) per page. Outdated v1..v3 caches trigger transparent re-extraction without deleting stored user books.

---

## 23. Intra-Word ZWNJ Integrity, Compound/Number-Word Restoration & Block Script Direction Rules (Phase 7)
Per section 0.5, Phase 7 intra-word ZWNJ and Bidi display hardening established the following permanent standards:

### 23.1. Uniform Plural & Affix ZWNJ Symmetry Rule
- Morphological restoration in `PersianZwnjRestorer` MUST apply `ها`/`های`/`هایی` plural ZWNJ uniformly across all valid Persian nouns (never restricting `های`/`ها` to a small hardcoded stem whitelist while only exempting verified protected non-split words such as `بها`, `رها`, `تنها`, `انتها`, `اشتها`, `گواه`, `شاه`, `بهار`, `صدا`, `جدا`, `خدا`, `شما`, `آنها`, `اینها`).
- Supported intra-word ZWNJ forms MUST cover:
  - Noun + plural suffixes (`سیستمهای` → `سیستم\u200Cهای`, `نادانستههای` → `نادانسته\u200Cهای`).
  - Prefix `بین` + noun/adjective (`بینالمللی` → `بین\u200Cالمللی`).
  - Prefix `به` + known compound second parts (`بهاستثنای` → `به\u200Cاستثنای`, `بهویژه` → `به\u200Cویژه`) while keeping standalone preposition `به` separated by a full space (`به تهران`, `به رغم`).
  - Indefinite suffix `ای` on `ی`-ending or silent-`ه` stems (`ایرانیای` → `ایرانی\u200Cای`, `خانهای` → `خانه\u200Cای`) while protecting authentic stems (`دریای`, `دنیای`, `رویای`, `جغرافیای`).
  - Compound connector `و` between known compound parts (`گفتوگو` → `گفت\u200Cوگو`, `رفتوآمد` → `رفت\u200Cوآمد`).
- Pipeline ordering: `PersianWordBoundaryRestorer` MUST execute before `PersianZwnjRestorer` and MUST preserve ZWNJ in recognized `به\u200C...` compounds so boundary normalization never strips valid intra-word ZWNJs.

### 23.2. Pure Compound Dictionary & Number-Word Splitter Rule
- `PersianCompoundDictionary` MUST perform exact-token dictionary mapping (never blind substring replacement inside longer stems) for controlled glued Persian compounds (`نقلقول`, `روبهرو`, `گفتوگو`, `رفتوآمد`, `بینالمللی`, `بهاستثنای`, `بهویژه`, `همچنین`, `هیچگاه`, `هیچوقت`, `کتابخانه`, `دانشگاه`, `پژوهشگاه`, `روششناسی`).
- `PersianNumberWordSplitter` MUST parse glued Persian number-word sequences (`بیستونهروزه` → `بیست\u200Cونه روزه`), joining numeric components with `\u200Cو` and separating a trailing non-number adjective/noun by a full space, while leaving non-number lookalikes (`سیستمهای`, `بیستونهرروزه`, `دولت`) completely untouched.

### 23.3. Block Script Classification, UI-Only Bidi Isolation & Reading Exclusion Rule
- `DocumentStructureAnalyzer` MUST tag every `DocumentBlock` with `BlockLanguage` (`PERSIAN`, `LATIN`, `MIXED`) by Persian-Arabic vs. Latin letter ratio, persisted in cache schema version `5` (triggering transparent re-extraction for `v1`..`v4` caches without deleting stored books).
- UI components (`BookBlockComponents`, `BookScreenSections`) MUST render `LATIN` blocks with explicit `LayoutDirection.Ltr` and `TextDirection.Ltr` so trailing English punctuation remains at the right end (`"...from the author."` never `".permission..."`), and apply Unicode Bidi directional isolates (`\u2066` LRI / `\u2069` PDI) ONLY at display time for embedded Latin spans in `PERSIAN`/`MIXED` blocks—NEVER mutating stored logical `block.text`.
- `ReadingPlanBuilder` MUST skip `BlockLanguage.LATIN` blocks by default with a clear Persian skip reason (without skipping `BlockLanguage.MIXED`), controlled by a persistent `"خواندن متن لاتین"` (`readLatinBlocks`, default `false`) toggle in `ReadingSettings`, `ReadingSettingsRepository`, and `ReadingSettingsSheet`.
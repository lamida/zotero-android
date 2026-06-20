# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

Before building, submodule assets must be bundled. Run from the repo root:

```bash
git submodule update --init --recursive
python3 scripts/bundle_translators.py
python3 scripts/bundle_translation.py
python3 scripts/bundle_pdf-worker.py
python3 scripts/bundle_citation_proc.py
python3 scripts/bundle_csl_locales.py
python3 scripts/bundle_styles.py
scripts/bundle_utilities.sh
scripts/bundle_reader.sh
```

**After syncing upstream:** if an upstream merge bumped a submodule (e.g. `reader`), the working-tree submodule will be stale (`git submodule status` shows a leading `+`). Run `git submodule update --init --recursive` to check out the recorded commit, then re-run the matching bundle script (`scripts/bundle_reader.sh`) so the APK ships the updated assets, not the old ones.

**`bundle_pdf-worker.py` may report failure but still be fine:** the script's `unzip` of the cmaps/fonts archive can exit with status 1 on a non-fatal *warning* even though all files extracted, which makes the Python `check_call` abort before it rebuilds `pdf-worker.zip`. Since the `pdf-worker` submodule and the fonts (fixed download URL) rarely change, the existing `app/src/main/assets/pdf-worker.zip` is usually still valid. To force a clean rebuild, remove the leftover extracted dirs first and re-zip manually:
```bash
cd pdf-worker && rm -rf cmaps standard_fonts pdf_worker_cmaps_and_fonts.zip
zip -r -q ../app/src/main/assets/pdf-worker.zip .
rm -rf cmaps standard_fonts pdf_worker_cmaps_and_fonts.zip   # keep submodule pristine
```

**Debug build:**
```bash
./gradlew assembleDevDebug
```

**Note on local builds with Java 26+:** If the build fails with a jlink error (`cannot find the build signature in the java.base`), add `org.gradle.java.home` to the local `gradle.properties` (do NOT commit):
```
org.gradle.java.home=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
```
This forces Gradle to use Java 17's jlink, which is compatible with the Android SDK's `core-for-system-modules.jar`. CI uses Java 17 (Zulu) and does not have this issue.

**Release APK (local, app name "Zotero"):**

The `internal` flavor produces the "Zotero" app name (not "Zotero Debug"). The play publisher plugin requires Play Store credentials for version codes, so local builds need a few one-time setup steps and one task exclusion.

**One-time machine setup (do once, never repeat):**
```bash
mkdir -p ~/.keystores
keytool -genkey -v -keystore ~/.keystores/zotero-android.jks \
  -alias zotero -keyalg RSA -keysize 2048 -validity 36500 \
  -storepass zoteropass -keypass zoteropass \
  -dname "CN=Zotero Android, OU=Dev, O=Lamida, L=Unknown, ST=Unknown, C=US"
```
The keystore at `~/.keystores/zotero-android.jks` must **never be regenerated** — Android requires the exact same signing key to update an installed APK. If it is lost, all installed copies must be uninstalled before a new key will work.

**PSPDFKit key:** Without `pspdfkit-key.txt` the PDF reader runs in trial mode and stamps "For evaluation purposes only" on every page. The key is stored as `PSPDFKIT_KEY` in the upstream `zotero/zotero-android` CI secrets. Once obtained, create the file (do not commit):
```bash
echo "YOUR_KEY_HERE" > pspdfkit-key.txt
```

**Before each build** (run from the repo root or worktree root):

1. Copy the permanent keystore and create secrets file (do not commit):
```bash
cp ~/.keystores/zotero-android.jks zotero.release.keystore
printf "zotero\nzoteropass\nzoteropass\n" > keystore-secrets.txt
```

2. Ensure `local.properties` exists with the Android SDK path (do not commit). Worktrees do not inherit this from the main repo — copy it if missing:
```bash
cp /Users/lamida/github/lamida/zotero-android/local.properties local.properties
```

3. Seed the play publisher version-code file (only needed if `app/build/` was cleaned):
```bash
mkdir -p app/build/intermediates/gpp/internalRelease
echo "247" > app/build/intermediates/gpp/internalRelease/available-version-codes.txt
```

4. Build:
```bash
./gradlew app:assembleInternalRelease --no-configuration-cache -x processInternalReleaseVersionCodes
```
Output: `app/build/outputs/apk/internal/release/app-internal-release.apk`

5. Copy to Google Drive Transfer/Apps (always from master after PR is merged):
```bash
GIT_HASH=$(git rev-parse --short HEAD)
cp app/build/outputs/apk/internal/release/app-internal-release.apk \
  ~/Library/CloudStorage/GoogleDrive-jonkartagolamida@gmail.com/My\ Drive/Transfer/Apps/zotero-android-${GIT_HASH}-release.apk
```

**Important:** Always build and copy from the master branch after the PR is merged, not from a feature branch. The hash in the filename must match the deployed commit on master.

**Release bundle (requires Play Store credentials — CI only):**
```bash
./gradlew publishInternalReleaseBundle
```

**Run unit tests:**
```bash
./gradlew app:testInternalReleaseUnitTest --no-configuration-cache -x processInternalReleaseVersionCodes
```

**Run a single test class:**
```bash
./gradlew app:testInternalReleaseUnitTest --no-configuration-cache -x processInternalReleaseVersionCodes \
  --tests "org.zotero.android.sync.DateParserTest"
```

## APK Output & Device Compatibility

`app:assembleInternalRelease` produces a **single universal APK** (~167 MB) — no per-ABI splits. One file installs on every supported device. Inspect it with `aapt2` from the SDK build-tools:

```bash
AAPT=$(ls -1 "$ANDROID_HOME"/build-tools/*/aapt2 | sort -V | tail -1)
"$AAPT" dump badging app/build/outputs/apk/internal/release/app-internal-release.apk \
  | grep -E "package:|SdkVersion|native-code|application-label:"
```

As built from master at `098dcfb3` (synced with upstream, versionCode 250):

| Property | Value |
|---|---|
| `applicationId` | `org.zotero.android` |
| App label | `Zotero` (internal flavor) |
| `versionCode` / `versionName` | `250` / `1.0.0-250` |
| `minSdkVersion` | 23 (Android 6.0) — see `buildSrc/src/main/kotlin/BuildConfig.kt` |
| `targetSdkVersion` / `compileSdk` | 35 (Android 15) |
| Bundled ABIs | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` |

Because the APK is universal (covers `arm64-v8a`) with `minSdk` 23 and forward-compatible `targetSdk` 35, the same file runs on all modern Android versions. Verified target devices for this build:

| Device | Android | API | Status |
|---|---|---|---|
| Onyx Tab Ultra C | 11 | 30 | ✓ supported |
| Bigme B7 | 14 | 34 | ✓ supported |
| Xiaomi 15 Ultra | 16 | 36 | ✓ supported (targetSdk 35, forward-compatible) |

> **PDF watermark:** without `pspdfkit-key.txt` the build runs in trial mode and stamps "For evaluation purposes only" on PDF pages. The APK is otherwise fully functional. Supply the key (see PSPDFKit note above) for a clean build.

## Product Flavors

Two flavors are defined in `buildSrc/src/main/kotlin/ProductFlavors.kt`:
- `dev` — debug builds, app name "Zotero Debug", uses `applicationIdSuffix = ".debug"`
- `internal` — release/internal builds, app name "Zotero"

Both require a `pspdfkit-key.txt` file in the repo root for the PDF reader key. Builds work without it but will emit a warning.

## Architecture

The app follows an MVI-like pattern with these building blocks in `app/src/main/java/org/zotero/android/architecture/`:

- **`BaseViewModel2<STATE, EFFECT>`** — base ViewModel. State changes go through `updateState { }`, one-off events through `triggerEffect()`. State is a copy (data class); never mutated in place.
- **`Screen<STATE, EFFECT>`** — interface implemented by Composable host components. Provides `render(state)` and `trigger(effect)` callbacks.
- **`ViewState` / `ViewEffect`** — marker interfaces for state and effect types.
- **`ScreenArguments`** — a singleton object holding `lateinit var` args for every destination. Screens write their args here before navigating; the destination reads them on entry. This sidesteps Navigation Compose's argument size limits.
- **`NavigationParamsMarshaller`** — for cases where args must travel through a Nav route string, objects are Gson-serialized and base64-encoded.

## Key Packages

| Package | Purpose |
|---|---|
| `architecture/` | MVI base classes, navigation helpers, DI modules, coroutine dispatchers |
| `screens/` | One sub-package per screen (ViewModel + Composable + data classes) |
| `uicomponents/` | Shared Compose UI components and theme |
| `api/` | Retrofit interfaces (`ZoteroApi`, `WebDavApi`, `AuthApi`) + response POJOs + mappers |
| `sync/` | Sync engine, action creators, library/collection/item domain models |
| `database/` | Realm wrapper (`RealmDbStorage`, `DbWrapperMain`) and request pattern |
| `pdf/` | PDF reader screens backed by Nutrient (PSPDFKit) |
| `htmlepub/` | HTML/EPUB reader screens |
| `translator/` | Web-based translator bridge (runs JS translators in a WebView) |
| `pdfworker/` | PDF worker bridge (WebView + JS) |
| `attachmentdownloader/` | Background file download controller |
| `backgrounduploader/` | Background file upload controller |
| `files/` | `FileStore` — central file path resolver for all app-managed files |

## Database Layer

Persistence uses Realm. All reads and writes go through `DbWrapperMain` (or `DbWrapperBundle` for bundled data). Operations are expressed as request objects:

- `DbRequest` — write-only, no return value; implement `process(database: Realm)`.
- `DbResponseRequest<T>` — read or write with a return value; implement `process(database: Realm): T`.

Requests live in `database/requests/`. `BaseViewModel2` exposes `suspend fun perform(dbWrapper, request)` helpers that dispatch to `Dispatchers.IO`.

## Navigation

- Phone layout: `DashboardRootPhoneNavigation` — single NavHost covering all screens.
- Tablet layout: `DashboardRootTopLevelTabletNavigation` — split-pane with separate NavHosts.
- `DashboardActivity` is the single Activity; it picks phone vs. tablet nav based on `CustomLayoutSize`.
- Navigation between screens uses `ZoteroNavigation` (wraps `NavHostController` + `OnBackPressedDispatcher`).

## Dependency Injection

Hilt is used throughout. Dependency plugins in `buildSrc/src/main/kotlin/dependencyplugins/` group related dependencies into Gradle plugins (e.g., `composeDependencies`, `networkDependencies`).

## Worktree cleanup

After a PR is merged, remove its worktree from `.claude/worktrees/`:

```bash
git worktree remove .claude/worktrees/<branch-name>
git push origin --delete <branch-name>
git branch -d <branch-name>
```

List active worktrees anytime with `git worktree list`. The main repo working directory is always the repo root (master branch); worktrees live under `.claude/worktrees/`.

## Submodules

Several directories are git submodules that must be initialized before the bundle scripts will work:
- `translators/` — Zotero translators (JS)
- `locales/` — CSL locales
- `styles/` — CSL citation styles
- `pdf-worker/` — PDF annotation worker JS
- `reader/` — HTML/EPUB reader
- `translation/` — translation engine JS
- `utilities/` — shared JS utilities

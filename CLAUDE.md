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

The `internal` flavor produces the "Zotero" app name (not "Zotero Debug"). The play publisher plugin requires Play Store credentials to resolve the version code, so local builds need two setup steps and one task exclusion.

1. Generate a local keystore (one-time, do not commit):
```bash
keytool -genkey -v -keystore zotero.release.keystore -alias zotero -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass zoteropass -keypass zoteropass \
  -dname "CN=Zotero Android, OU=Dev, O=Lamida, L=Unknown, ST=Unknown, C=US"
```

2. Create `keystore-secrets.txt` in the repo root (one-time, do not commit):
```
zotero
zoteropass
zoteropass
```
(lines are: key alias, store password, key password)

3. Seed the play publisher version-code file so Gradle has something to read:
```bash
mkdir -p app/build/intermediates/gpp/internalRelease
echo "247" > app/build/intermediates/gpp/internalRelease/available-version-codes.txt
```

4. Build:
```bash
./gradlew app:assembleInternalRelease --no-configuration-cache -x processInternalReleaseVersionCodes
```

Output: `app/build/outputs/apk/internal/release/app-internal-release.apk`

5. Copy to Google Drive Transfer/Apps (replace `<hash>` with `git rev-parse --short HEAD`):
```bash
GIT_HASH=$(git rev-parse --short HEAD)
cp app/build/outputs/apk/internal/release/app-internal-release.apk \
  ~/Library/CloudStorage/GoogleDrive-jonkartagolamida@gmail.com/My\ Drive/Transfer/Apps/zotero-android-${GIT_HASH}-release.apk
```

**Note:** `zotero.release.keystore` and `keystore-secrets.txt` are gitignored. If they don't exist yet, re-run steps 1-2. Step 3 only needs to be re-run if the `app/build` directory is cleaned.

**Release bundle (requires Play Store credentials — CI only):**
```bash
./gradlew publishInternalReleaseBundle
```

**Run unit tests:**
```bash
./gradlew test
```

**Run a single test class:**
```bash
./gradlew test --tests "org.zotero.android.sync.DateParserTest"
```

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

## Submodules

Several directories are git submodules that must be initialized before the bundle scripts will work:
- `translators/` — Zotero translators (JS)
- `locales/` — CSL locales
- `styles/` — CSL citation styles
- `pdf-worker/` — PDF annotation worker JS
- `reader/` — HTML/EPUB reader
- `translation/` — translation engine JS
- `utilities/` — shared JS utilities

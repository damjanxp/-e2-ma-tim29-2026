# AGENTS.md — Slagalica Mobile App

> **Audience:** AI coding agents (GitHub Copilot, Claude, Cursor, etc.) working in this repository.
> **Purpose:** Project conventions, architecture, and rules. Read this **before** generating code.

---

## Project Overview

**Slagalica** is a native Android mobile application — a real-time multiplayer quiz game inspired by the Serbian TV show "Slagalica". Two players compete head-to-head through six mini-games per match. The app supports user accounts, friends, leaderboards, regional chat, tournaments, and daily missions.

This is a **university project** (Mobile Applications course, 2025/2026). Three students collaborate; this repository contains the work of **Student 1**, whose responsibilities are:
- KT1 + KT2: Authentication (registration, login, password reset), games **Korak po korak** and **Moj broj**
- KO (final): Match orchestration (Funkcionalnost 3), regional chat (Funkcionalnost 8), challenges (Funkcionalnost 9)

The other two students contribute the remaining games (Ko zna zna, Spojnice, Asocijacije, Skočko) and features (profile, regions, leagues, friends, leaderboards, tournaments, daily missions, notifications).

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| Language | **Java 17** (NOT Kotlin — do not generate Kotlin code unless explicitly asked) |
| Platform | Android, minSdk 26, targetSdk 35 |
| UI Toolkit | Android Views (XML layouts) + Material Components (Material 3) |
| Build System | Gradle with **Version Catalog** (`libs.versions.toml`) — Groovy DSL (`build.gradle`, NOT `build.gradle.kts`) |
| Backend | Firebase (Auth, Firestore, Realtime Database, Cloud Messaging) |
| Image Loading | Glide 4.16 |
| Math Expression Parsing | exp4j 0.4.8 (for "Moj broj" game) |
| Maps | OpenStreetMap or Google Maps (Student 2's responsibility) |

**Do NOT introduce:** Kotlin, Jetpack Compose, RxJava, Dagger/Hilt, Retrofit, Room, MVVM/LiveData/ViewModel architecture, View Binding, Data Binding, or any framework not listed above.

**Note:** Firebase Storage is NOT used in this project (requires paid Blaze plan). Avatar images use local placeholders only.

---

## Package Name

The application package name is **`com.example.slagalica`**. This must be used consistently everywhere:

- All Java class `package` declarations: `package com.example.slagalica.xxx;`
- All references in `AndroidManifest.xml`
- All `import` statements

**Never use `rs.ftn.slagalica`** — this is an old incorrect name. If you see it anywhere in the codebase, replace it with `com.example.slagalica`.

---

## Architecture

The app follows a **strict three-layer architecture** (mandated by the project specification):

```
com.example.slagalica/
├── ui/          ← Presentation layer (Activities, Fragments, Adapters, custom Views)
│   ├── auth/    ← LoginActivity, RegisterActivity, ForgotPasswordActivity
│   ├── games/   ← Game Activities (KorakPoKorakActivity, MojBrojActivity, etc.)
│   ├── profile/ ← ProfileActivity, ProfileEditActivity
│   ├── chat/    ← ChatActivity, ChatAdapter
│   ├── match/   ← MatchOrchestratorActivity, MatchmakingActivity
│   └── main/    ← MainActivity (home screen)
│
├── logic/       ← Business logic (stateless utility/manager classes)
│   ├── auth/    ← Validation, password rules
│   ├── games/   ← Scoring rules, expression validation, answer normalization
│   ├── match/   ← MatchFinalizer, reward calculations
│   └── chat/    ← ChatNotificationService
│
├── data/        ← Data layer (Firebase access, models)
│   ├── model/   ← POJOs (User, Match, Message, Challenge, etc.)
│   ├── repository/ ← UserRepository, MatchRepository, ChatRepository, etc.
│   └── remote/  ← Firebase service helpers (if needed)
│
└── util/        ← Helpers (Constants, Validators, ShakeDetector, DateUtils)
```

**Layer rules — DO NOT VIOLATE:**

1. **`ui/`** may import from `logic/`, `data/`, `util/`. Must NOT contain business rules directly.
2. **`logic/`** may import from `data/model/` and `util/`. Must NOT import from `ui/`.
3. **`data/`** may import from `util/`. Must NOT import from `ui/` or `logic/`.
4. **`util/`** is a leaf — no imports from other internal packages.

---

## Naming Conventions

- **Activities:** `XxxActivity.java` (e.g., `LoginActivity`, `MojBrojActivity`)
- **Fragments:** `XxxFragment.java`
- **Adapters:** `XxxAdapter.java`
- **POJOs / models:** singular noun (`User`, `Match`, `Message`, `Challenge`)
- **Repositories:** `XxxRepository.java`, **always singletons** with `getInstance()`
- **Logic / utility classes:** `XxxLogic.java` or descriptive (`MatchFinalizer`, `ShakeDetector`)
- **Layouts:** `activity_snake_case.xml`, `fragment_snake_case.xml`, `item_snake_case.xml`, `dialog_snake_case.xml`
- **String resources:** snake_case keys (`login_button_label`, `error_invalid_email`)
- **IDs in XML:** camelCase with type prefix:
  - `et` = EditText/TextInputEditText
  - `tv` = TextView
  - `btn` = Button/MaterialButton
  - `iv` = ImageView
  - `rv` = RecyclerView
  - `cv` = CardView
  - `pb` = ProgressBar
  - `tl` = TextInputLayout
- **Methods:** camelCase, verb-first (`registerUser`, `calculateScore`)
- **Constants:** `UPPER_SNAKE_CASE` in `util.Constants`
- **Booleans:** `isXxx`, `hasXxx`, `canXxx`

**Language:** Code identifiers and comments in **English**. All user-facing strings in **Serbian (Latin script)** via `strings.xml` — never hardcode Serbian text in Java files.

---

## Firebase Configuration

### Services in use

| Service | Purpose |
|---------|---------|
| Firebase Auth | User registration, login, email verification, password reset |
| Firestore | User profiles, game content, match history, statistics |
| Realtime Database | Active matches, matchmaking queue, regional chat, challenges |
| Cloud Messaging | Push notifications |

**Firebase Storage is NOT enabled** — requires paid plan. Do not generate any code that uses `FirebaseStorage`.

Note: the version catalog `gradle/libs.versions.toml` currently contains a `firebase-storage` entry under [libraries] (it is unused in `app/build.gradle`). Do NOT add or enable `firebase-storage` in dependencies — leave it unused.

### Firestore collections

| Collection | Document ID | Purpose |
|------------|-------------|---------|
| `users` | Firebase Auth UID | User profiles, tokens, stars, league |
| `matchHistory` | auto-generated | Completed matches for statistics |
| `korakPoKorak` | auto-generated | Game content for "Korak po korak" |
| `koZnaZna` | auto-generated | Question bank (Student 2) |
| `spojnice` | auto-generated | Connection puzzles (Student 2) |
| `asocijacije` | auto-generated | Association puzzles (Student 3) |

### Realtime Database paths

| Path | Purpose |
|------|---------|
| `matchmaking/{userId}` | Matchmaking queue entries |
| `matches/{matchId}` | Active match state, scores, per-game data |
| `chats/{regionKey}/{messageId}` | Regional chat messages |
| `challenges/{challengeId}` | Open and in-progress challenges |

### Firebase coding rules

- **Repositories are singletons.** Never instantiate Firebase services directly in Activities.
- **Always use callback interfaces** — do not return `Task<>` to the UI layer.
- **Always remove listeners in `onDestroy()`** to prevent memory leaks.
- **POJOs must have:** public no-arg constructor, public getters/setters, no `final` fields.
- **Error messages via callbacks must be in Serbian** (pre-translated from Firebase error codes).

---

## Gradle & Build Configuration

### Current working plugin declaration — DO NOT CHANGE

This exact structure was reached after fixing a classloader conflict. Any change to plugin placement will break the build.

| | File | Content |
| |------|---------|
| | `settings.gradle` | Only `foojay-resolver` — **no google-services here** |
| | `build.gradle` (root) | `alias(libs.plugins.android.application) apply false` and `alias(libs.plugins.google.services) apply false` |
| | `libs.versions.toml` | `googleServices = "4.4.4"` |
| | `app/build.gradle` | `alias(libs.plugins.android.application)` and `alias(libs.plugins.google.services)` (no plugin versions here) |

**Why:** Declaring `google-services` in `settings.gradle` uses the settings classloader which initializes before AGP, making `com.android.build.api.variant.Variant` unavailable and breaking the build.

### Version Catalog rules

- Check `libs.versions.toml` first — if library is already there, use `libs.xxx` syntax
- Only use hardcoded version strings for libraries NOT in the catalog
- Never declare a plugin version in `app/build.gradle` — versions go in `libs.versions.toml` only
- `compileSdk` must be a plain integer: `compileSdk 35` — never use version blocks like `version = release(36) { ... }`

- Note: there is a mismatch in the repository where Navigation dependencies are hardcoded in `app/build.gradle` as `2.8.4` while the version catalog defines `navigationComponent = "2.7.7"`. When changing navigation versions, update `gradle/libs.versions.toml` and prefer `libs.navigation-fragment` / `libs.navigation-ui` rather than editing `app/build.gradle` directly.

---

## Build & Run

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device/emulator
./gradlew installDebug

# Clean (use when builds behave unexpectedly)
./gradlew clean

# View logs from this app only
adb logcat -s "Slagalica:*"
```

On Windows PowerShell use the project wrapper directly (PowerShell):

```powershell
.\gradlew assembleDebug
.\gradlew installDebug
.\gradlew clean
# View logs (adb must be on PATH)
adb logcat -s "Slagalica:*"
```

**Required local file (NOT in version control):**
- `app/google-services.json` — place directly in `app/` folder (not in `app/src/`)

---

## Files the Agent Must NOT Modify

- `app/google-services.json`
- `gradle/wrapper/gradle-wrapper.jar`
- `local.properties`
- `.idea/`
- `app/build/`, `build/`, `.gradle/`
- `settings.gradle` — do not add any plugins here

---

## Code Style

- **Indentation:** 4 spaces
- **Line length:** soft limit 120 chars
- **Braces:** K&R style (opening brace same line)
- **Imports:** no wildcards
- **Javadoc:** required on public methods in `logic/` and `data/repository/`
- **Nullability:** use `@Nullable` / `@NonNull` from `androidx.annotation`
- **Validation errors:** always `TextInputLayout.setError("message")` — never `Toast` for form validation
- **User-facing strings:** always via `getString(R.string.xxx)` — never hardcoded in Java

---

## Lifecycle & Memory Discipline

1. **`CountDownTimer`** — store as field, call `.cancel()` in `onDestroy()`
2. **`ShakeDetector`** — `.start()` in `onResume()`, `.stop()` in `onPause()`
3. **Firebase listeners** — every attachment must have a matching removal in `onDestroy()`
4. **Background work** — use `Executors.newSingleThreadExecutor()` + `runOnUiThread()`. Do NOT use `AsyncTask`
5. **No anonymous listeners** holding Activity references for long-lived operations

---

## During-Match UI Rules

- User must always see: **token count, star count, current league** — even during a match
- User must always access: **profile, statistics, friends, leaderboards, match start** — except during an active match
- **Back button during match:** show confirmation dialog ("Da li si siguran? Izgubićeš partiju.")
- All game Activities: `android:screenOrientation="portrait"` in manifest

---

## Game-Specific Notes (Student 1)

### Korak po korak
- 2 rounds × 70s, 7 hints opening every 10s
- Scoring: hint index `i` (0-based) → `20 - 2*i` points
- Answer comparison: case-insensitive + diacritic-insensitive via `Normalizer.normalize(NFD)` in `KorakPoKorakLogic.tacanOdgovor()`
- Solo mode (challenge): 1 round only

### Moj broj
- 2 rounds × 60s
- Expression: digits + `+`, `-`, `*`, `/`, `(`, `)` (convert `×`→`*`, `÷`→`/` before evaluating)
- Multiset validation: each number usable at most as many times as it appears in the 6-number set
- Use **exp4j** for evaluation; catch `ArithmeticException` for division by zero
- Shake-to-stop: `util.ShakeDetector`, threshold ~12.0 m/s², 1s cooldown
- Solo mode (challenge): 1 round only

---

## Match Coordination (KO)

- Fixed game order: `Ko zna zna → Spojnice → Asocijacije → Skočko → Korak po korak → Moj broj`
- `MatchOrchestratorActivity` launches each game via `ActivityResultLauncher`
- Each game reads `matchId` and `userId` from Intent extras, returns `setResult(RESULT_OK, intentWithPoints)`
- Do not block UI waiting for opponent — use Realtime DB listeners with a "Waiting..." overlay

---

## Common Pitfalls — DO NOT REPEAT

1. **Generating Kotlin.** Java only.
2. **Wrong package name.** Always `com.example.slagalica`, never `rs.ftn.slagalica`.
3. **Adding google-services to `settings.gradle`.** Breaks the build — see Gradle section.
4. **Declaring plugin version in `app/build.gradle`.** Versions in `libs.versions.toml` only.
5. **Using View Binding / Data Binding / LiveData / ViewModel.** Not in this project.
6. **Hardcoding Serbian strings in Java.** Use `strings.xml`.
7. **Forgetting to register new Activities in `AndroidManifest.xml`.**
8. **Using `Toast` for form validation.** Use `TextInputLayout.setError()`.
9. **Using `firebase-storage`.** Removed — requires paid plan.
10. **`compileSdk` with version blocks** like `version = release(36) { minorApiLevel = 1 }`. Use plain integer: `compileSdk 35`.
11. **Leaving Firebase listeners attached after `onDestroy()`.**

---

## Git Workflow

- **Branches:** `main` (releases), `dev` (active development), `feature/student1-<scope>`
- **Pull requests** target `dev`, reviewed by at least one team member
- **Commit messages:** imperative English, scoped (`auth: add password reset`, `korak: implement scoring`)
- **Do not commit:** `google-services.json`, `/build/`, `/.gradle/`, `/.idea/`, `local.properties`

---

## When Uncertain

1. Re-read the project specification (`docs/Specifikacija_Student1.docx`)
2. Follow existing patterns in the codebase
3. Ask the developer rather than guessing on scoring rules or user-facing behavior
4. Prefer **simple, explicit, easily-explained Java** — the developer must defend every line at the oral exam
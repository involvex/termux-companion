# AGENTS.md — Termux Companion

## Project Overview

**Termux Companion** is an Android application that provides a rich GUI companion for the [Termux](https://f-droid.org/packages/com.termux/) terminal emulator. It enables executing commands, browsing/editing files, and AI-powered command autocomplete — all from a modern Jetpack Compose interface.

- **Package**: `com.termux.companion`
- **Min SDK**: 26 (Android 8.0)
- **Target/Compile SDK**: 34 (Android 14)
- **Language**: Kotlin 2.0.20
- **Build System**: Gradle with Kotlin DSL

---

## Useful Commands

### Build & Run

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Clean build
./gradlew clean

# Install debug APK on connected device
./gradlew installDebug

# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Check for dependency updates
./gradlew dependencies --configuration implementation
```

### Gradle Tips

```bash
# Show full dependency tree for a configuration
./gradlew :app:dependencies --configuration runtimeClasspath

# Build and lint in one pass
./gradlew assembleDebug lint

# Generate a build report (useful for APK size analysis)
./gradlew :app:assembleDebug -Preport
```

### Development

```bash
# List all Kotlin source files
dir /s /b app\src\main\java\*.kt

# Find files matching a pattern
# Use glob: **/*.kt or **/*.xml
```

---

## Technologies & Stack

### Core Framework

| Technology | Version | Purpose |
|---|---|---|
| Kotlin | 2.0.20 | Primary language |
| Jetpack Compose | BOM 2024.09.00 | Declarative UI framework |
| Material 3 | Via BOM | Design system & components |
| Android Gradle Plugin | 8.6.1 | Build toolchain |

### Architecture & DI

| Technology | Version | Purpose |
|---|---|---|
| Hilt (Dagger) | 2.52 | Dependency injection |
| MVVM + StateFlow | - | Architecture pattern |
| Navigation Compose | 2.8.0 | In-app navigation |

### Data & Networking

| Technology | Version | Purpose |
|---|---|---|
| Room | 2.6.1 | Local SQLite database |
| DataStore Preferences | 1.1.1 | Key-value settings storage |
| Retrofit | 2.11.0 | REST API client |
| OkHttp | 4.12.0 | HTTP layer + logging |
| Gson | Via Retrofit | JSON serialization |

### Async & Coroutines

| Technology | Version | Purpose |
|---|---|---|
| Kotlin Coroutines | 1.8.1 | Async operations |
| Lifecycle KTX | 2.8.5 | Lifecycle-aware coroutines |
| Lifecycle ViewModel Compose | 2.8.5 | ViewModel integration with Compose |

### Testing

| Technology | Version | Purpose |
|---|---|---|
| JUnit | 4.13.2 | Unit testing |
| AndroidX Test | 1.2.1 | Instrumented test runner |
| Compose UI Test | Via BOM | UI testing |

---

## Architecture

```
app/src/main/java/com/termux/companion/
├── TermuxCompanionApp.kt          # Hilt Application class
├── MainActivity.kt                 # Single Activity entry point
├── data/
│   ├── ai/
│   │   ├── AISuggestionService.kt  # LLM API integration for autocomplete
│   │   └── CommandAutocomplete.kt  # Static command database + autocomplete logic
│   ├── db/
│   │   ├── AppDatabase.kt          # Room database definition
│   │   ├── CommandHistoryDao.kt    # DAO for command history queries
│   │   └── CommandHistoryEntity.kt # Room entity for persisted commands
│   ├── settings/
│   │   └── SettingsRepository.kt   # DataStore-backed preferences
│   └── termux/
│       ├── TermuxCommandExecutor.kt    # Core: sends commands to Termux via Intent
│       ├── TermuxDiagnosticsChecker.kt # Validates Termux installation & permissions
│       └── TermuxResultReceiver.kt     # BroadcastReceiver for command results
├── di/
│   └── AppModule.kt               # Hilt module: database, repositories, services
├── domain/
│   └── model/
│       └── Models.kt              # Data classes & enums (FileItem, AppSettings, etc.)
├── ui/
│   ├── components/
│   │   ├── ConnectionStatusBar.kt  # Termux connection state banner
│   │   └── LoadingStates.kt        # Reusable Loading/Error/Empty state composables
│   ├── editor/
│   │   ├── EditorScreen.kt         # File editor with line numbers & search
│   │   └── EditorViewModel.kt      # Editor state management
│   ├── explorer/
│   │   ├── FileExplorerScreen.kt   # File browser with context menus
│   │   └── FileExplorerViewModel.kt# Explorer state management
│   ├── navigation/
│   │   ├── AppNavHost.kt           # Navigation graph + bottom bar
│   │   └── Screen.kt              # Route definitions
│   ├── settings/
│   │   ├── SettingsScreen.kt       # Settings UI
│   │   └── SettingsViewModel.kt    # Settings state management
│   ├── terminal/
│   │   ├── TerminalScreen.kt       # Terminal output + input bar + autocomplete
│   │   └── TerminalViewModel.kt    # Command execution + suggestions logic
│   └── theme/
│       ├── Theme.kt                # Material 3 color schemes (dark/light + terminal theme)
│       └── Type.kt                 # Typography definitions
└── utils/
    └── Constants.kt                # App-wide constants (paths, version)
```

### Key Patterns

- **Single Activity**: `MainActivity` hosts all Compose UI via `AppNavHost`
- **MVVM**: ViewModels expose `StateFlow` state consumed by Composables via `collectAsState()`
- **Dependency Injection**: All singletons provided via Hilt `@Module` in `AppModule`
- **Navigation**: Bottom navigation with 4 tabs: Terminal, Files, Editor, Settings
- **Theme**: Custom terminal-inspired dark theme (green-on-dark) with Material 3 + dynamic color support

---

## Best Practices

### Kotlin & Compose

1. **Use `data object` for sealed class members** (not `object :` syntax)
   ```kotlin
   // Good
   data object Terminal : Screen("terminal", "Terminal", Icons.Default.Terminal)
   // Bad
   object Terminal : Screen("terminal", "Terminal", Icons.Default.Terminal)
   ```

2. **Always use `collectAsState()` in Composables** to observe `StateFlow`
   ```kotlin
   val outputLines by viewModel.outputLines.collectAsState()
   ```

3. **Keep composables pure** — receive state and callbacks, not ViewModels directly (except at the screen level via `hiltViewModel()`)

4. **Use `Modifier.weight()` inside `Row`/`Column`** for proportional sizing, not fixed widths

5. **Use `imePadding()`** on terminal/editor screens to handle soft keyboard visibility

### Dependency Injection

1. **All singletons** are provided in `AppModule.kt` using `@Provides @Singleton`
2. **ViewModels** use `@HiltViewModel` + `@Inject constructor` — no manual DI
3. **Context-dependent classes** use `@ApplicationContext` qualifier
4. **Never create manual instances** of repository/service classes — always inject them

### Data Layer

1. **Room entities** use `@Entity(tableName = "...")` with explicit column names
2. **Room DAOs** return `suspend` functions — call them from coroutines
3. **DataStore** uses `Preferences` keys — define keys as `val` in the `companion object`
4. **Retrofit** services are created manually (not Hilt-provided) — follow the pattern in `AISuggestionService`

### Termux Integration

1. **Command execution** uses `TermuxCommandExecutor.executeWithResult()` with a callback pattern
2. **Results are delivered** via two mechanisms: file-based polling (primary) and PendingIntent (fallback)
3. **Timeouts**: Commands timeout after 15 seconds in the terminal, 10 seconds in the file explorer
4. **Error handling**: Always check `isTermuxInstalled()` and `hasRunCommandPermission()` before executing

### UI Guidelines

1. **Use Material 3 components** — `Scaffold`, `TopAppBar`, `NavigationBar`, `Card`, etc.
2. **Monospace font** for terminal and editor screens: `FontFamily.Monospace`
3. **Terminal colors**: Green primary (`#4AF626`), dark background (`#1A1A2E`), error red (`#E57373`)
4. **Edge-to-edge**: Call `enableEdgeToEdge()` in `MainActivity.onCreate()`
5. **Experimental APIs**: Opt-in explicitly with `@OptIn(ExperimentalMaterial3Api::class)` etc.

---

## Code Review Checklist

When reviewing or writing code for this project, verify:

- [ ] **No hardcoded strings** for paths — use `Constants.kt` or inject from config
- [ ] **No raw Thread creation** — use `viewModelScope.launch` or `Dispatchers.IO` via `withContext`
- [ ] **State is immutable** — use `MutableStateFlow` + `asStateFlow()` pattern
- [ ] **Callbacks are lifecycle-aware** — cancel jobs with `Job?.cancel()` before re-launching
- [ ] **Permissions are checked** before Termux operations
- [ ] **Error states are shown** to users, not silently swallowed
- [ ] **No memory leaks** in `BroadcastReceiver` — it's registered in `Application.onCreate()`
- [ ] **kapt is used** (not KSP) for Room and Hilt — both use annotation processing
- [ ] **Compose previews** use `@Preview` annotation and default parameter values
- [ ] **File paths** use forward slashes or platform-appropriate separators

---

## Common Pitfalls

### Termux Integration
- **`allow-external-apps=true`** must be set in Termux properties — without it, the companion app cannot send commands
- **`RUN_COMMAND` permission** is a custom permission declared in the manifest — it must be granted manually by the user
- **File-based polling** reads from `Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)` — storage permission required on Android 11+
- **PendingIntent path** uses `com.termux.service.extra.plugin_result_bundle` — this is Termux-specific and may change

### Build & Gradle
- **kapt** is required for both Room and Hilt — do not switch to KSP without migrating both
- **Compose BOM** manages all Compose library versions — do not pin individual Compose library versions separately
- **`nonTransitiveRClass=true`** is set — use `com.termux.companion.R` imports explicitly

### Coroutines
- **15-second timeout** in `TerminalViewModel.executeCommand()` — long-running commands will be reported as timed out
- **AI suggestions** have a 300ms debounce — typing quickly will not trigger excessive API calls
- **Job cancellation** — always cancel the previous job before starting a new one (e.g., `commandJob?.cancel()`)

---

## File Locations Reference

| File | Location |
|---|---|
| App entry point | `app/src/main/java/com/termux/companion/TermuxCompanionApp.kt` |
| Main activity | `app/src/main/java/com/termux/companion/MainActivity.kt` |
| Navigation graph | `app/src/main/java/com/termux/companion/ui/navigation/AppNavHost.kt` |
| Theme colors | `app/src/main/java/com/termux/companion/ui/theme/Theme.kt` |
| Termux command executor | `app/src/main/java/com/termux/companion/data/termux/TermuxCommandExecutor.kt` |
| Database schema | `app/src/main/java/com/termux/companion/data/db/AppDatabase.kt` |
| Settings keys | `app/src/main/java/com/termux/companion/data/settings/SettingsRepository.kt` |
| DI module | `app/src/main/java/com/termux/companion/di/AppModule.kt` |
| Android manifest | `app/src/main/AndroidManifest.xml` |
| Build config (app) | `app/build.gradle.kts` |
| Build config (root) | `build.gradle.kts` |
| Gradle properties | `gradle.properties` |

---

## Adding New Features

### New Screen / Tab
1. Create `ui/<feature>/<Feature>Screen.kt` with `@Composable` function
2. Create `ui/<feature>/<Feature>ViewModel.kt` with `@HiltViewModel` class
3. Add route in `ui/navigation/Screen.kt` (add to sealed class + `bottomNavItems`)
4. Add composable destination in `AppNavHost.kt`
5. Provide any new dependencies in `AppModule.kt`

### New Data Source
1. Create entity in `data/db/` if using Room, or model in `domain/model/`
2. Create DAO in `data/db/` if Room-backed
3. Create repository in `data/` (or `data/<domain>/`)
4. Provide via `AppModule.kt` with `@Provides @Singleton`
5. Inject into ViewModel via constructor

### New Termux Command
1. Use `TermuxCommandExecutor.executeWithResult()` or `executeCommandNoResult()`
2. Handle timeout (default 10-15s) and error cases
3. Check `isTermuxInstalled()` and `hasRunCommandPermission()` first

# LexiVault — Word Game Solver

LexiVault is a native Android word-game companion app for Scrabble and Words With Friends players. Enter the letters on your rack or a partially-known crossword pattern, and LexiVault searches a 200k+ word dictionary to find every valid, playable word — scored and sorted so you can pick your best move.

Built entirely with Jetpack Compose and Material 3, LexiVault follows an MVVM architecture with a single-module Gradle project.

## Features

- **Word Finder** — Enter your rack letters (use `?` for a blank tile) to find every word you can play. Free-form filters let Premium users narrow results by starts-with/contains/ends-with, word length range, and sort order.
- **Crossword Solver** — Enter a pattern with `?` for unknown letters (e.g. `C?T` or `?????`) to find every matching word for crossword-style games.
- **Scrabble & Words With Friends scoring** — Every result shows its point value; toggle between scoring systems (Words With Friends scoring is a Premium feature).
- **Saved Words** — Star words from any search to keep them handy, then filter your saved list before game night.
- **Premium subscription** — Unlimited results, advanced filters, custom sort order, and unlimited saved words are unlocked via Google Play Billing (monthly, yearly, or lifetime tiers), with purchase restore support.
- **Onboarding flow** — A short first-run walkthrough introduces the core features to new users.
- **Material 3 dynamic theming** — Full light/dark mode support, with dynamic (wallpaper-based) color on Android 12+ and a custom fallback palette on older versions.
- **Accessibility & polish** — Content descriptions on interactive elements, haptic feedback on key actions, proper IME actions/focus handling on text inputs, and dedicated empty/error states throughout.

## Requirements

- **Android Studio** Koala (2024.1) or newer
- **JDK 17**
- **Android SDK**: compileSdk 34, targetSdk 34
- **Minimum supported Android version**: Android 7.0 (API 24)
- Kotlin 1.9.24, Jetpack Compose (BOM 2024.06.00)

## Build instructions

1. Clone or open the project directory in Android Studio.
2. Let Gradle sync — all dependencies are resolved from Google's and Maven Central's repositories, no additional setup is required.
3. Run on a device or emulator running Android 7.0 (API 24) or later:
   ```
   ./gradlew installDebug
   ```
4. Or build a debug/release APK from the command line:
   ```
   ./gradlew assembleDebug
   ./gradlew assembleRelease
   ```
5. To run unit and instrumented tests:
   ```
   ./gradlew test
   ./gradlew connectedAndroidTest
   ```

The bundled dictionary (`app/src/main/assets/words.txt`) is loaded lazily and cached in memory on first search, so no network access or additional setup is needed to use the app offline.

## Project structure

```
app/src/main/java/com/factory/lexivaultwordgamesolver/
├── LexiVaultApplication.kt   # Application class; owns repository/premium/billing/onboarding singletons
├── MainActivity.kt           # Single Activity hosting the Compose UI
├── billing/                  # Google Play Billing integration, premium tier/product models
├── data/                     # WordDictionary (asset loading), WordRepository, Room database
│   └── db/                   # Room entities/DAO for saved words
├── onboarding/                # First-run onboarding completion state
├── solver/                    # Anagram solver, pattern solver, Scrabble/WWF scorer
└── ui/
    ├── components/             # Shared composables (EmptyState, WordResultRow, ScoringSystemToggle, etc.)
    ├── finder/                 # Word Finder screen + ViewModel
    ├── navigation/              # NavHost + bottom navigation shell
    ├── onboarding/               # Onboarding screen
    ├── pattern/                  # Crossword Solver screen + ViewModel
    ├── paywall/                  # Premium paywall screen + ViewModel
    ├── saved/                    # Saved Words screen + ViewModel
    ├── settings/                  # Settings screen + ViewModel
    └── theme/                     # Material 3 color scheme, typography
```

Each screen follows the same pattern: a `ViewModel` exposes a single `StateFlow<UiState>`, constructed via `LexiVaultViewModelFactory`, and the corresponding `@Composable` screen collects that state and renders it.

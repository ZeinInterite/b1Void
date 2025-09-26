# Repository Guidelines

## Project Structure & Module Organization
- Root Gradle build uses Kotlin DSL; main app module lives in `app`.
- Application code inside `app/src/main/java/com/example/b1void`; UI resources under `app/src/main/res`.
- Unit tests reside in `app/src/test`, instrumentation specs in `app/src/androidTest`; Gradle outputs land in `app/build`.
- Firebase config sits in `app/google-services.json`; avoid committing environment-specific variants.

## Build, Test, and Development Commands
- Don't use "./gradlew.bat compileDebugKotlin"
- Run `./gradlew assembleDebug` (Windows: `./gradlew.bat assembleDebug`) to build the debug APK.
- Use `./gradlew lint` to execute Android lint; resolve warnings before merging.
- Execute `./gradlew test` for JVM unit tests and `./gradlew connectedAndroidTest` with an attached device for Espresso flows.
- `./gradlew clean` clears build outputs when Gradle cache issues appear.

## Coding Style & Naming Conventions
- Follow the Kotlin style guide: 4-space indentation, explicit visibility for public APIs, camelCase for members, PascalCase for classes.
- Android resources stay snake_case (e.g., `activity_main.xml`) with string keys prefixed by feature (`auth_error_invalid_token`).
- Rely on Android Studio''s formatter and `Optimize Imports`; avoid unused synthetic imports because view binding is enabled.
- Keep package organization feature-based under `com.example.b1void` (e.g., `ui`, `data`, `storage`); use `internal` for module-only APIs.

## Testing Guidelines
- Mirror source names with a `Test` suffix for unit classes (e.g., `SessionRepositoryTest`).
- Instrumentation suites live under `androidTest` with descriptive names or `*IT` suffix.
- Target >80% coverage on new code; document justified gaps in pull requests.
- Add camera/storage smoke tests covering permissions and happy-path flows whenever features change.

## Commit & Pull Request Guidelines
- Write concise commit subjects in Russian imperative or past tense (see git log), e.g., transliterated `Obnovil obrabotku oshibok`, capped at 72 characters.
- Keep each commit focused and passing `./gradlew lint test`; add co-authors when pairing.
- Pull requests need a summary, screenshots or recordings for UI updates, and linked issues/tasks.
- Highlight required Firebase, Dropbox, or signing configuration steps in the PR description when relevant.

## Security & Configuration Tips
- The release signing block expects `app/release.keystore`; store real credentials outside version control.
- Keep API keys and secrets in `local.properties` or secure vaults; never add them to tracked files.
- When sharing builds, scrub personal data from `google-services.json` variants before distribution.

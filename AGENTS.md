# Repository Guidelines

## Project Structure & Module Organization
- Root build uses Gradle Kotlin DSL; main app module is `app`.
- Source: `app/src/main/java/com/example/b1void`; resources: `app/src/main/res`.
- Unit tests: `app/src/test`; instrumentation: `app/src/androidTest`.
- Build outputs: `app/build`. Firebase config: `app/google-services.json` (avoid environment‑specific variants in VCS).

## Build, Test, and Development Commands
- Build debug APK: `./gradlew assembleDebug` (Windows: `./gradlew.bat assembleDebug`).
- Lint: `./gradlew lint` (resolve warnings before merge).
- JVM unit tests: `./gradlew test`.
- Instrumentation (device/emulator required): `./gradlew connectedAndroidTest`.
- Clean outputs: `./gradlew clean`.
- Do not use `./gradlew.bat compileDebugKotlin`.

## Coding Style & Naming Conventions
- Kotlin style: 4‑space indent; explicit visibility for public APIs; PascalCase classes; camelCase members.
- Feature‑based packages under `com.example.b1void` (e.g., `ui`, `data`, `storage`); prefer `internal` for module‑only APIs.
- Android resources in snake_case (e.g., `activity_main.xml`). Strings prefixed by feature, e.g., `auth_error_invalid_token`.
- Use Android Studio formatter and Optimize Imports; avoid synthetic imports (view binding enabled).

## Testing Guidelines
- Mirror source names with `Test` suffix (e.g., `SessionRepositoryTest`).
- Instrumentation suites in `androidTest` with descriptive names or `*IT` suffix.
- Target >80% coverage on new code; document justified gaps in PRs.
- Add camera/storage smoke tests for permissions and happy‑path flows when features change.

## Commit & Pull Request Guidelines
- Commit subjects in Russian imperative or past tense (transliterated), ≤72 chars, focused, and green on `lint` + `test`.
- PRs require a summary, linked issues/tasks, and screenshots/recordings for UI updates.
- Note required Firebase, Dropbox, or signing steps in the PR when relevant.

## Security & Configuration Tips
- Release signing expects `app/release.keystore`; keep real credentials outside VCS.
- Store API keys/secrets in `local.properties` or secure vaults; never in tracked files.
- Scrub personal data from `google-services.json` variants before sharing builds.


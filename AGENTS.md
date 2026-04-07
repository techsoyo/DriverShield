# Repository Guidelines

This repository contains **DriverShield**, an Android application designed for truck drivers to track shifts and rest times with high precision and battery efficiency.

## Project Structure & Module Organization
The project follows **Clean Architecture** principles and is organized into a single-module Android app (`/app`).
- **`domain/`**: Pure Kotlin logic, interfaces for repositories, and legal limit definitions.
- **`data/`**: Implementation of repositories, Room database (`ShiftDatabase`), and DataStore for preferences.
- **`presentation/`**: Jetpack Compose UI, ViewModels, and UI-specific models.
- **`service/`**: Foreground services for background shift tracking and timers.

## Build, Test, and Development Commands
The project uses Gradle with Kotlin DSL.
- **Build**: `./gradlew build`
- **Clean**: `./gradlew clean`
- **Run Debug**: `./gradlew installDebug`
- **Run All Tests**: `./gradlew test`
- **Run Single Test**: `./gradlew test --tests "com.drivershield.domain.usecase.CheckLegalLimitsUseCaseTest"`
- **Lint**: `./gradlew lint`

## Coding Style & Naming Conventions
- **Language**: Kotlin 1.7+ (Strict typing: No `Any`, no `!!`, no unjustified `lateinit`).
- **Naming**:
  - `PascalCase` for classes and objects (e.g., `TimerService`, `ShiftRepository`).
  - `camelCase` for functions and variables (e.g., `startShift()`, `isWorking`).
  - `UPPER_SNAKE_CASE` for constants (e.g., `MAX_WEEKLY_MS`).
  - `_privateFlow` / `publicFlow` for StateFlows.
- **Concurrency**: Use Coroutines and Flows. Functions with side effects (DB/Network) must be `suspend` or return `Flow`.
- **UI**: AMOLED Black (`#000000`) is mandatory for background to save battery.

## Testing Guidelines
- **Frameworks**: JUnit 5, MockK, Turbine (Flows), and Robolectric.
- **Patterns**: Use `GIVEN/WHEN/THEN` naming for tests.
- **Coverage**: Every UseCase must have tests for happy paths, edge cases (legal limits ±1ms), and error states.
- **Execution**: Database tests must use `Room.inMemoryDatabaseBuilder`. Never use `Thread.sleep()`; use `TestCoroutineScheduler`.

## Specialized Agent Skills
The project defines four specialized operational modes (Skills):
1. **System/Battery Expert**: Focuses on `service/`, `WakeLocks`, and Doze mode survival.
2. **UI/UX Maestro**: Focuses on `presentation/`, accessibility, and high-contrast AMOLED design.
3. **Data Guardian**: Focuses on `data/`, Room atomicity, and UTC timestamp integrity.
4. **QA Tester**: Focuses on legal limit validation and regression testing.

## Plan Mode Protocol
**PLAN MODE is ALWAYS ACTIVE**. No files should be modified without presenting a detailed plan and receiving explicit approval.

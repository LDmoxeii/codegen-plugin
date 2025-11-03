# Repository Guidelines

## Project Structure & Modules
- `plugin/` — Gradle plugin implementation (Kotlin). Sources in `plugin/src/main/kotlin`, Pebble templates in `plugin/src/main/resources/templates/*.kt.peb`. Tests go under `plugin/src/test`.
- `ksp-processor/` — KSP models and processor support. Kotlin in `ksp-processor/src/main/kotlin`, service registration under `ksp-processor/src/main/resources/META-INF/services`.
- `docs/` — Design and workflow notes (see `docs/CODEGEN_WORKFLOW.md`).
- Root Gradle: multi-project (`settings.gradle.kts`), Kotlin 2.2.20, JVM 17.

## Build, Test, and Development
- Prereqs: JDK 17, Gradle wrapper.
- Build all: `./gradlew build` (Windows: `./gradlew.bat build`).
- Module build: `./gradlew :plugin:build` / `./gradlew :ksp-processor:build`.
- Tests: `./gradlew test` or per-module `:plugin:test`.
- Local publish (for consumers): `./gradlew publishToMavenLocal`.
- Remote publish (Aliyun): `./gradlew publish -Paliyun.maven.username=xxxx -Paliyun.maven.password=xxxx`.
- Plugin tasks exposed to consumers: `genArch`, `genAggregate`, `genDesign` (list with `gradle tasks`).

## Coding Style & Naming
- Language: Kotlin (JVM 17). Use idiomatic Kotlin, 4-space indent.
- Packages: lowercase; Classes/Interfaces: PascalCase; methods/fields: camelCase; constants: UPPER_SNAKE_CASE.
- Templates: name with `.kt.peb`; keep logic minimal and favor clear placeholders (e.g., `{{ Entity }}`, `{{ Package }}`).

## Testing Guidelines
- Frameworks: JUnit 5 + `kotlin.test`.
- Layout: tests in `src/test/kotlin` mirroring package; fixtures in `src/test/resources`.
- Naming: `*Test` for unit tests; prefer focused, deterministic cases.
- Run: `./gradlew test` (module-specific with `:plugin:test`).

## Commit & Pull Request Guidelines
- Follow Conventional Commits: `feat(scope): …`, `fix(scope): …`, `refactor(scope): …`, `chore(scope): …` (e.g., `feat(codegen): add enum translator`).
- PRs must include: clear description, rationale, linked issues, and before/after snippets or generated file diffs when templates change. Ensure `build` and `test` pass.

## Security & Configuration Tips
- Do not commit credentials. Put Aliyun creds in `~/.gradle/gradle.properties` as `aliyun.maven.username` and `aliyun.maven.password`.
- For DB-backed generation, point to non-production databases with least privilege.

## Agent-Specific Notes
- Avoid changing plugin ID (`com.only4.codegen`) or task names without updating docs.
- When adjusting templates, keep `templates/*.kt.peb` names stable and update generators if tags change.
- Prefer minimal, module-scoped changes; run module builds before pushing.

## Alias Configuration
- Default tag aliases live in `plugin/src/main/resources/aliases/{aggregate,design}.json`.
- Override via extension in a consuming build:
  - `codegen { aggregateTagAliases.put("entities", "aggregate"); designTagAliases.put("repos", "repository") }`.

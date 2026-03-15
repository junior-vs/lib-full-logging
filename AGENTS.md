# AGENTS.md

## Repo at a glance
- Multi-module Maven root (`pom.xml`) with one active module: `logging-quarkus`.
- `concepts/full-logging/` holds the architecture and field-contract docs; treat these as design intent for the library surface.
- Current runnable code lives under `logging-quarkus/src/main/java/br/com/vsjr/labs/log/**`.

## Big-picture architecture (what exists now)
- Logging model centers on immutable records: `LogEvento` and `LogContexto`.
- Correlation context is managed by `GerenciadorContextoLog` (MDC + OTel span extraction) and must be cleaned via `limpar()` to avoid thread reuse leaks.
- Sensitive fields are masked through `SanitizadorDados.sanitizar(chave, valor)` before logging.
- A compile-time guided DSL is intended via sealed stages in `LogEtapas` (`registrando -> em -> [porque|como|comDetalhe] -> info/debug/warn/erro`).
- Note: `LogSistematico` is currently skeletal (`em(...)` returns `null`), so treat DSL implementation as in-progress.

## Cross-file contracts you must preserve
- Field naming canon is documented in `concepts/full-logging/FIELD_NAMES.md` (prefer snake_case canonical names).
- Logging behavior and anti-patterns are defined in `concepts/full-logging/CODING_STANDARDS.md`.
- Runtime pipeline expectations (logs/traces/metrics) are in `concepts/full-logging/ARCHITECTURE.md` and `concepts/full-logging/INTEGRATION_GUIDE.md`.
- Quarkus runtime config currently wires JSON logs + OTel + Prometheus in `logging-quarkus/src/main/resources/application.properties`.

## Build/test/debug workflows (discoverable and used in CI)
- Local dev mode (module): `cd logging-quarkus; ./mvnw quarkus:dev` (see `logging-quarkus/README.md`).
- Standard CI build: `mvn -B clean install -Dno-format` (from `.github/workflows/build.yml`).
- Native CI build: `mvn -B install -Dnative -Dquarkus.native.container-build -Dnative.surefire.skip`.
- There are currently no Java tests under `logging-quarkus/src/test`; add tests when changing behavior.

## Project-specific conventions (not generic)
- Prefer Quarkus CDI scopes (`@ApplicationScoped`) and records/sealed interfaces already used in `log/context`, `log/core`, and `log/dsl`.
- Keep context propagation explicit: OTel IDs come from active span; do not invent trace IDs.
- Use MDC keys consistently with existing code (`traceId`, `spanId`, `userId`, `servico`, `classe`, `metodo`) unless performing a coordinated migration.
- Preserve Portuguese domain naming used in current API (`motivo`, `canal`, `detalhes`, `erroERelanca`) for compatibility.

## Integration points and external dependencies
- Core dependencies: `quarkus-opentelemetry`, `quarkus-logging-json`, `quarkus-smallrye-jwt`, `quarkus-smallrye-context-propagation`, `quarkus-arc` (`logging-quarkus/pom.xml`).
- CI and release automation are inherited from Quarkiverse reusable workflows in `.github/workflows/*.yml`.
- `quarkus.application.name` is injected into logging context; keep it configured for service attribution.

## Agent operating checklist for changes
- If touching logging schema or event fields, update docs in `concepts/full-logging/` alongside code.
- If touching request/log context lifecycle, verify MDC cleanup and OTel extraction paths.
- If implementing DSL behavior, align with staged contract in `LogEtapas` and add tests for call-order and null/sensitive inputs.
- Keep `requirements.md`, `design.md`, and `tasks.md` updated per `.github/instructions/spec-driven-workflow-v1.instructions.md`.


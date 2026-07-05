## Module dependency rules

- About domain and application:
  - `domain` depends on nothing (no Spring, no frameworks, no other modules).
  - No Spring annotations (`@Component`, `@Service`, `@Autowired`, `@Transactional`, etc.) in `domain` or `application` — both must be testable without a Spring context.
  - `application` depends only on `domain`. It defines inbound (`application.in`) and outbound (`application.out`) port interfaces — it does not implement outbound ports itself.
  - Domain invariants (validation, valid-state enforcement) live in `domain` entities/value objects, not in adapters or application services.
- About adapters:
  - Adapter modules may depend on external libraries/frameworks freely.
  - Adapter modules do not depend on each other. Each depends only on `domain` + `application` + `common` (if needed) + its own external libs.
  - Inbound adapters (`controller`, `scheduler`, `consumer`) call `application` only through inbound port interfaces. They never call `domain` directly.
  - Outbound adapters (`repository`, `client`, `producer`) implement outbound port interfaces owned by `application`.
  - All object mapping (DTO ↔ domain, entity ↔ domain, message schema ↔ domain) happens inside the adapter module that owns the boundary. Conversion logic is never shared across adapters and never lives in `domain`/`application`.
- About bootstrap and cross-cutting concerns:
  - `bootstrap` depends on every module — it is the sole composition root wiring Spring beans together. It holds configuration/wiring, not reusable logic.
  - Cross-cutting reusable logic (shared exception mappers, logging/tracing helpers) lives in `common`, not `bootstrap` — `bootstrap` is downstream of adapters, so adapters can't see anything placed there.

## Module and naming convention

- Root/parent service folder: `ai-svc`, packaging=pom, no source code.
- All submodules drop the `-svc` suffix: `ai-domain`, `ai-controller`, etc.
- `adapters/in/` and `adapters/out/` are filesystem-only groupings, not Maven modules — actual module names stay flat (`ai-controller`, not `adapters-in-ai-controller`).
- Full module-per-adapter granularity: each adapter type (`controller`, `scheduler`, `consumer`, `repository`, `client`, `producer`) is its own Maven module.

## Package naming convention

Base package: `com.jameshskoh.ai`

| Module | Package |
|---|---|
| `ai-domain` | `com.jameshskoh.ai.domain` |
| `ai-application` | `com.jameshskoh.ai.application` (`.in`, `.out` for ports) |
| `ai-common` | `com.jameshskoh.ai.common` |
| `ai-controller` | `com.jameshskoh.ai.adapter.in.controller` |
| `ai-scheduler` | `com.jameshskoh.ai.adapter.in.scheduler` |
| `ai-consumer` | `com.jameshskoh.ai.adapter.in.consumer` |
| `ai-repository` | `com.jameshskoh.ai.adapter.out.repository` |
| `ai-client` | `com.jameshskoh.ai.adapter.out.client` |
| `ai-producer` | `com.jameshskoh.ai.adapter.out.producer` |
| `ai-bootstrap` | `com.jameshskoh.ai` (no `.bootstrap` suffix; `@SpringBootApplication` class here) |
| `ai-test` | mirrors the package of the module under test (no `.test` segment) |

## Test module structure

- All test code lives in `ai-test`. No other module has a `src/test` directory.
- `ai-test` has a compile-scope dependency on every other module (including bootstrap). This is the one intentional exception to the inward-dependency rule — `ai-test` is a leaf consumer, never part of the runtime artifact.
- Test classes use the exact same package as the class under test (no `.test` segment), enabling package-private access across all modules.
- Always run the whole suite: `./mvnw test -pl ai-test`. Use `-Dtest=ClassName` or IDE filtering to scope locally.
- Test helper/config classes must use `@TestConfiguration`, not `@Component`/`@Configuration`. Each test explicitly imports only the `@TestConfiguration` classes it needs.

## Component scanning

- `@SpringBootApplication` sits at `com.jameshskoh.ai` (bare root) — default scan covers every module's sub-package automatically. No explicit `@ComponentScan` needed.
- Domain and application have no compile-time barrier against Spring annotations — code review must catch any stray `@Component`/`@Service`/`@Autowired` there.

## Versioning

- Single version across all modules (no independent per-module versioning).

## Build and run

```bash
# Full build + all tests
./mvnw clean install

# Run tests only (whole suite)
./mvnw test -pl ai-test

# Start the application
./mvnw spring-boot:run -pl ai-bootstrap
```

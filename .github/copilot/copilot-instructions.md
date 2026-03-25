# GitHub Copilot Instructions — studio-observability

## Priority Guidelines

When generating code for this repository:

1. **Version Compatibility**: Always use Java 21 language features (records, sealed interfaces,
   pattern matching `switch`, `var`, text blocks) and Quarkus 3.32.3 APIs exactly as found in this
   codebase. Never suggest deprecated or pre-21 patterns.
2. **Context Files**: Instructions in `.github/copilot/` and `.github/instructions/` are the
   primary source of truth.
3. **Codebase Patterns**: When context files don't address a specific scenario, scan the existing
   code under `lib-logging-quarkus/src/` for established patterns.
4. **Architectural Consistency**: Maintain the strict layered architecture — DSL → Service →
   Repository — and CDI boundaries established in `br.com.vsjr.labs.log`.
5. **Code Quality**: Prioritize maintainability, security (data minimization, sanitization),
   observability, and testability in all generated code.

---

## Technology Stack (exact versions — scan `lib-logging-quarkus/pom.xml` to confirm)

| Technology | Version |
|---|---|
| Java | 21 (`maven.compiler.release=25` in pom — use Java 21+ language features) |
| Quarkus | 3.32.3 |
| Maven | 3.x (via `mvnw`/`mvnw.cmd`) |
| JUnit | 5 (via `quarkus-junit`) |
| Micrometer | from Quarkus BOM |
| OpenTelemetry | from Quarkus BOM (`quarkus-opentelemetry`) |
| SmallRye JWT | from Quarkus BOM |
| SmallRye Context Propagation | from Quarkus BOM |

Key active dependencies: `quarkus-opentelemetry`, `quarkus-logging-json`, `quarkus-arc`,
`quarkus-micrometer-registry-prometheus`, `quarkus-rest`, `quarkus-rest-jackson`,
`quarkus-smallrye-jwt`, `quarkus-smallrye-context-propagation`, `quarkus-hibernate-validator`.

---

## Package Structure

```
br.com.vsjr.labs
├── log/                      ← library surface (core, public)
│   ├── annotations/          ← CDI interceptor bindings: @Logged, @Rastreado
│   ├── context/              ← MDC lifecycle: GerenciadorContextoLog, LogContexto, SanitizadorDados
│   ├── core/                 ← Immutable model: LogEvento (record)
│   ├── dsl/                  ← Fluent API: LogSistematico, LogEtapas (sealed interfaces)
│   ├── filtro/               ← JAX-RS filter: LogContextoFiltro (@Provider)
│   ├── interceptor/          ← CDI interceptors: LogInterceptor, RastreamentoInterceptor
│   └── tracing/              ← Span management: GerenciadorRastreamento, EnriquecedorSpan, ContextoSpan
└── exemple/                  ← Demo/example code (not library API)
    ├── rest/                 ← HellowrdResource, HelloService
    └── tracing/              ← EnriquecedorOperacao (business enricher example)
```

---

## Naming Conventions

Follow **Portuguese domain naming** exactly as established in this codebase. This is a deliberate,
non-negotiable project convention.

| Element | Convention | Examples |
|---|---|---|
| Classes | PascalCase, Portuguese | `GerenciadorContextoLog`, `LogContexto`, `SanitizadorDados` |
| Methods | camelCase, Portuguese (domain verbs) | `inicializar`, `registrarLocalizacao`, `limpar`, `sanitizar` |
| Constants | `ALL_CAPS` with Portuguese names | `CAMPO_TRACE_ID`, `CAMPO_SPAN_ID`, `CHAVES_CREDENCIAIS` |
| Packages | lowercase, Portuguese context-nouns | `context`, `filtro`, `interceptor`, `tracing`, `dsl` |
| Local vars | `var` + camelCase | `var spanContext`, `var cronometro`, `var chaveLower` |
| Annotations | PascalCase Portuguese | `@Logged`, `@Rastreado` |

**Span names** must follow the `Classe.metodo` format (e.g., `"HelloService.sayHello"`).

---

## Java 21 Features in Use — Use These Patterns

### Records for Immutable Data
```java
// Always use records for immutable, thread-safe value objects
public record LogContexto(
        String traceId,
        String spanId,
        String userId,
        String servico
) {
    // Compact constructor for validation/transformation
    public LogContexto {
        detalhes = detalhes != null ? Collections.unmodifiableMap(detalhes) : Map.of();
    }

    // Boolean query methods on record state
    public boolean temTrace() {
        return traceId != null && !traceId.isBlank();
    }
}
```

### Sealed Interfaces for Compile-Time DSL Contracts
```java
// Sealed interface hierarchy for staged fluent APIs
public final class LogEtapas {
    private LogEtapas() {}

    public sealed interface EtapaOnde permits LogSistematico {
        EtapaOpcional em(Class<?> classe, String metodo);
    }

    public sealed interface EtapaOpcional permits LogSistematico {
        EtapaOpcional porque(String motivo);
        void info();
        void erro(Throwable causa);
    }
}
```

### Pattern Matching `switch`
```java
// Use switch with pattern matching instead of if-else chains
return switch (classificar(chave)) {
    case CREDENCIAL    -> "****";
    case DADO_PESSOAL  -> "[PROTEGIDO]";
    case PUBLICO       -> valor;
};

// Pattern matching for type checks in switch
return switch (requestContext.getSecurityContext()) {
    case null -> "anonimo";
    case SecurityContext sc when sc.getUserPrincipal() instanceof Principal p
            && p.getName() != null -> p.getName();
    default -> "anonimo";
};
```

### `var` for Local Variables
```java
// Always use var for local variable type inference
var spanContext = capturarSpanContext();
var cronometro = Timer.start(meterRegistry);
var chaveLower = chave.toLowerCase();
```

---

## CDI Patterns

### Bean Scopes
- Always use `@ApplicationScoped` for singleton beans — never `@Singleton`.
- Use constructor injection for all dependencies in CDI beans.
- Reserve `@Inject` for field injection only when constructor injection is not possible (e.g.,
  `@Inject Instance<T>` for CDI instance beans).

```java
// Correct: constructor injection
@ApplicationScoped
public class GerenciadorRastreamento {

    Tracer tracer;

    @Inject
    Instance<EnriquecedorSpan> enriquecedores;

    public GerenciadorRastreamento(Tracer tracer) {
        this.tracer = tracer;
    }
}
```

### CDI Interceptors
- Annotate interceptors with `@InterceptorBinding`, `@Interceptor`, and `@Priority`.
- Use `@Priority(Interceptor.Priority.APPLICATION)` for standard interceptors.
- Use `@Priority(Interceptor.Priority.APPLICATION - 10)` when the interceptor must run
  **before** `LogInterceptor` (e.g., `RastreamentoInterceptor`).
- Use `@AroundInvoke` for method interception; never use post-construct or pre-destroy for
  business logic.
- Interceptors must clean up only the MDC fields they themselves inserted. Never clear MDC
  fields owned by another layer.

```java
@Rastreado
@Interceptor
@Priority(Interceptor.Priority.APPLICATION - 10)
public class RastreamentoInterceptor {
    // ...
    @AroundInvoke
    public Object rastrear(InvocationContext contexto) throws Exception {
        // save state → try { proceed } catch { mark error } finally { restore }
    }
}
```

### Configuration Injection
- Use `@ConfigProperty` with a sane `defaultValue` for all injected config values.

```java
@Inject
@ConfigProperty(name = "quarkus.application.name", defaultValue = "servico-desconhecido")
String nomeServico;
```

---

## Logging Patterns

### Mandatory: Use `LogSistematico` for All Business Events
Never use bare `Logger.getLogger(...)` calls for business events. The DSL enforces 5W1H
structure and automatic sanitization.

**Minimum valid sequence (What + Where):**
```java
LogSistematico
    .registrando("Pedido criado")
    .em(PedidoService.class, "criar")
    .info();
```

**Full sequence with optional dimensions:**
```java
LogSistematico
    .registrando("Pagamento recusado")
    .em(PagamentoService.class, "processar")
    .porque("Saldo insuficiente no gateway")          // Why
    .como("API REST — POST /pagamentos")              // How
    .comDetalhe("pedidoId", pedido.getId())
    .comDetalhe("valor", pedido.getValor())
    .comDetalhe("token", request.token())             // auto-masked → "****"
    .erro(excecao);
```

### Log Level Semantics
| Level | When to use |
|---|---|
| `info()` | State-changing operations: persist, external calls, authentication |
| `debug()` | Internal flows without state changes: validation, cache lookups |
| `warn()` | Recoverable anomalies: unauthorized access attempts, fallbacks, rate limits |
| `erro(Throwable)` | Unrecoverable failure — operation could not fulfill its contract |
| `erroERelanca(Throwable)` | Same as `erro` but rethrows — use in lambdas/streams |

### Logger Implementation
Use `org.jboss.logging.Logger` — the Quarkus-native logging API. Do not use SLF4J or
`java.util.logging` directly for new code.

```java
// Logger per class — idiomatic for Quarkus/JBoss Logging
private static final Logger LOG = Logger.getLogger(MyClass.class);
```

### MDC Field Ownership (Do Not Violate)
| MDC field | Owner | Lifecycle |
|---|---|---|
| `traceId`, `spanId` | `GerenciadorContextoLog` / `LogContextoFiltro` | request start → response |
| `userId`, `servico` | `GerenciadorContextoLog` / `LogContextoFiltro` | request start → response |
| `classe`, `metodo` | `LogInterceptor` | method entry → method exit (finally) |
| `log_classe`, `log_metodo`, `log_motivo`, `log_canal`, `detalhe_*` | `LogSistematico` | log call only |

---

## Tracing Patterns

### Annotations
- `@Rastreado` — creates an OTel child span per method invocation.
- `@Logged` — adds class/method to MDC and records Micrometer timer/counter metrics.
- Combine both on a class for full observability:
  ```java
  @ApplicationScoped
  @Logged
  @Rastreado
  public class PagamentoService { ... }
  ```

### Span Enrichment — `EnriquecedorSpan` Contract
When adding new OTel span attributes, implement `EnriquecedorSpan` as a new CDI bean.
**Never modify `GerenciadorRastreamento` directly** to add attributes.

```java
@ApplicationScoped
public class EnriquecedorPagamento implements EnriquecedorSpan {

    @Override
    public void enriquecer(Span span, InvocationContext contexto) {
        var parametros = contexto.getParameters();
        if (parametros != null && parametros.length > 0
                && parametros[0] instanceof PagamentoRequest req) {
            span.setAttribute("pagamento.valor", req.valor().toString());
        }
    }

    @Override
    public int prioridade() {
        return 100;  // business enrichers: 100+; infra enrichers: 10-50
    }
}
```

Priority contract:
- `10–50`: infrastructure enrichers (e.g., `EnriquecedorMetadados` at 10, `EnriquecedorIdentidade` at 20)
- `100+`: business domain enrichers (e.g., `EnriquecedorOperacao` at 100)

Attribute naming: follow [OTel Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/)
for technical attributes (`service.name`, `code.namespace`, `code.function`, `enduser.id`); use
domain-prefixed names for business attributes (`pagamento.valor`, `pedido.id`).

---

## REST Resource Patterns

```java
@Path("/hello")
@Logged
@Rastreado
public class HellowrdResource {

    @Inject
    HelloService helloService;

    @Path("/world")
    @GET
    public String hello() {
        LogSistematico
                .registrando("Recurso de hello")
                .em(HellowrdResource.class, "hello")
                .porque("Solicitação do recurso de hello")
                .como("API REST - GET /hello/world")
                .info();
        return helloService.sayHello();
    }
}
```

- Always annotate REST resources and services with both `@Logged` and `@Rastreado`.
- Always call `LogSistematico` inside exception handlers before delegating or rethrowing.
- Return `0` / empty response as fallback — do not propagate raw exceptions to REST layer without logging.
- Use `@QueryParam` for simple parameters; never construct raw query strings.

---

## Security and Sanitization

### `SanitizadorDados` — Automatic via `comDetalhe`
Sensitive field masking is applied automatically when using `comDetalhe(chave, valor)`.
Never bypass this by writing keys directly to the MDC or logger.

Sensitive key categories (checked case-insensitively):
- **Credentials** → `"****"`: `password`, `senha`, `secret`, `token`, `accesstoken`,
  `refreshtoken`, `authorization`, `apikey`, `cvv`
- **Personal data** → `"[PROTEGIDO]"`: `cpf`, `rg`, `email`, `celular`, `cardnumber`,
  `numerocartao`

When adding new sensitive field names, update `SanitizadorDados.CHAVES_CREDENCIAIS` or
`CHAVES_DADOS_PESSOAIS` — do not handle masking ad hoc elsewhere.

### General Security Rules
- Validate all input at system boundaries using Jakarta Bean Validation (`@Valid`, constraint annotations).
- Use `Optional<T>` to avoid null-pointer exceptions in service returns.
- Follow OWASP Top 10: no string concatenation in query parameters, no raw exception messages
  exposed to callers.
- User identity is always resolved from `SecurityContext.getUserPrincipal()` — never trust
  user-supplied values for `userId`.

---

## Metrics Patterns (Micrometer)

Use `MeterRegistry` injected via constructor in beans that record metrics.

```java
// Counter for failures
meterRegistry.counter("metodo.falha",
        "classe", classe,
        "metodo", nomeMetodo,
        "excecao", e.getClass().getSimpleName()
).increment();

// Timer with percentile histogram
cronometro.stop(
        Timer.builder("metodo.execucao")
                .tag("classe", classe)
                .tag("metodo", nomeMetodo)
                .publishPercentileHistogram()   // p50, p95, p99 auto-published
                .register(meterRegistry)
);
```

- Metrics endpoint: `/q/metrics` (Prometheus format).
- Use `publishPercentileHistogram()` for latency timers.
- Tag dimensions: always include at minimum `classe` and `metodo`.

---

## Testing Patterns

### Unit Tests — JUnit 5 Without `@QuarkusTest`
For pure unit tests of DSL/utility classes that don't need CDI, use plain JUnit 5 with direct
JBoss LogManager capture:

```java
class LogSistematicoTest {

    @Test
    void erroDevePropagarThrowableParaORuntimeDeLogging() {
        var logger = org.jboss.logmanager.Logger.getLogger(ServicoTeste.class.getName());
        var nivelOriginal = logger.getLevel();
        var handler = new CapturingHandler();
        handler.setLevel(Level.ALL);
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);

        try {
            // ... exercise code ...
            assertEquals(1, handler.records().size());
        } finally {
            // Always restore logger state in finally
            logger.removeHandler(handler);
            logger.setUseParentHandlers(usarHandlersPaiOriginal);
            logger.setLevel(nivelOriginal);
        }
    }
}
```

Key rules:
- **Always restore logger state in `finally`** after attaching a test handler.
- Use `CopyOnWriteArrayList` for thread-safe log record capture.
- Use `assertSame` for exact Throwable identity checks; `assertEquals` for message checks.
- Test class and helper inner classes are `private static final`.

### Integration Tests — `@QuarkusTest`
For beans requiring CDI, use `@QuarkusTest` with RestAssured for HTTP-level testing.
Mock external endpoints with `@QuarkusTestResource` when needed.

---

## Javadoc Standards

Follow the documentation style used throughout the library:

1. **Class-level Javadoc**: describes responsibilities, lifecycle context, design rationale, and
   what infrastructure the class depends on. Bullet-list format for responsibilities.
2. **Method-level Javadoc**: documents parameters with `@param`, return value with `@return`,
   exceptions with `@throws`. Include behavioral guarantees (e.g., thread-safety, order
   constraints).
3. **Inline comments**: use `// ── Section name ──────` separators between logical sections.
   Use inline comments to explain *why*, not *what* (`// Sanitização automática: ...`).
4. **Examples in Javadoc**: use `{@code ...}` for inline code, `<pre>{@code ... }</pre>` for
   multi-line examples.
5. **Cross-references**: use `{@link ClassName}` and `{@link ClassName#method}` for all
   references to related classes/methods.

```java
/**
 * Breve descrição da responsabilidade principal.
 *
 * <p>Contexto adicional e design rationale em um ou mais parágrafos.</p>
 *
 * <p>Exemplo de uso:</p>
 * <pre>{@code
 * MyClass.create("arg")
 *     .doSomething()
 *     .finish();
 * }</pre>
 *
 * @param argumento descrição do argumento
 * @return descrição do retorno
 */
```

---

## Configuration Standards

- All config in `lib-logging-quarkus/src/main/resources/application.properties`.
- Use `%dev.` and `%prod.` profiles for environment-specific overrides.
- Never hardcode endpoint URLs; always use `@ConfigProperty` with a default.
- OTLP endpoint: `http://localhost:4317` (gRPC); override per environment.

### Profile Rules
| Profile | Pattern |
|---|---|
| dev | `%dev.quarkus.log.console.json.pretty-print=true`, DEBUG for `br.com.vsjr.labs` |
| prod | `pretty-print=false`, trace sampler `traceidratio` at `0.1` |

---

## Build and Dev Workflow

```powershell
# Dev mode (Windows)
cd lib-logging-quarkus; .\mvnw.cmd quarkus:dev

# Run tests
.\mvnw.cmd test

# Package JAR
.\mvnw.cmd package

# CI build (same as .github/workflows/build.yml)
mvn -B clean install -Dno-format
```

---

## Architecture Boundaries — Never Violate

1. **DSL layer** (`dsl/`) must not depend on `filtro/` or `interceptor/`.
2. **`LogSistematico`** must not be called from within `GerenciadorContextoLog` (circular
   dependency risk).
3. **MDC cleanup** in `LogContextoFiltro.filter(request, response)` must always execute,
   even on exception — use `try/finally` in any code that adds to MDC.
4. **Span lifecycle**: `Scope.close()` must always be called before `Span.end()`. Both must
   run in `finally`.
5. **`EnriquecedorSpan` implementations** must be defensive: check `contexto.getParameters()`
   for null and length before accessing elements.
6. **Library vs example code**: `br.com.vsjr.labs.log.*` is library API; `br.com.vsjr.labs.exemple.*`
   is demo/example code only — never import from `exemple` in `log` packages.

---

## Project-Specific Guidance

- Scan the `lib-logging-quarkus/AGENTS.md` file for runtime flow and safe extension points
  before making behavioral changes.
- When adding new HTTP context fields, always add the corresponding cleanup call in
  `GerenciadorContextoLog.limpar()`.
- DSL call order is enforced by sealed interfaces — never change `EtapaOnde` or `EtapaOpcional`
  without updating `LogSistematico` and running the full test suite.
- Keep `requirements.md`, `design.md`, and `tasks.md` updated per the
  spec-driven workflow defined in `.github/instructions/spec-driven-workflow-v1.instructions.md`.
- When in doubt, prioritize consistency with the existing codebase over external best practices
  or newer library features.

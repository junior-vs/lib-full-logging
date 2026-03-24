# Visão Geral da Arquitetura

> Este documento descreve a arquitetura conceitual e modular das bibliotecas de Logging Sistemático, suas responsabilidades e a forma como interagem.

---

## Contexto do Sistema

A biblioteca de Logging não é um processo separado (sidecar ou agente), mas uma dependência embarcada ativamente nas aplicações. Seu propósito é unificar o fornecimento do JSON estruturado via `stdout`, entregando a rastreabilidade necessária para o coletor de ambiente (Fluentd / OTel Collector).

```text
┌─────────────────────────────────────────────────────┐
│                   Serviço da Aplicação              │
│                                                     │
│  ┌──────────────┐     ┌─────────────────────────┐   │
│  │ Lógica de    │────▶│    Logging Abstraction  │   │
│  │ Negócio      │     │                         │   │
│  └──────────────┘     │   LogSistematico (DSL)  │   │
│                       │   GerenciadorContextoLog│   │
│  ┌──────────────┐     │   SanitizadorDados      │   │
│  │ Rest / Web   │────▶│                         │   │
│  │ Endpoints    │     └────────────┬────────────┘   │
│  └──────────────┘                  │                │
└────────────────────────────────────┼────────────────┘
                                     │
              ┌──────────────────────┼──────────────────────┐
              ▼                      ▼                      ▼
     Log Aggregator           Tracing Backend        Metrics Backend
  (Elasticsearch/Loki)     (Jaeger/Zipkin/OTLP)   (Prometheus/Grafana)
```

---

## Arquitetura dos Módulos

A arquitetura do projeto possui duas frentes primárias de implementação para abarcar o escopo corporativo. O desenvolvimento de negócio não acessa frameworks diretos, mas apenas a DSL (`LogSistematico`, `@Logged`, `LogEtapas`).

### [Implementação SLF4J + Log4j2](Logs/implementacao_slf4j.md)
**Código-fonte da biblioteca portável (Jakarta EE)**

Destinada a contêineres imperativos tradicionais (Wildfly, TomEE, Payara). Utiliza o `LoggerFactory` padrão e gera o JSON via configuração de template do Log4j2 (`log4j2.xml` + `JsonTemplateLayout`).
- **Contexto (MDC)**: Baseia-se no `org.slf4j.MDC` nativo associado a um `GerenciadorContextoLog` instanciado passivamente.
- **Ativação**: O Interceptor (`LoggingInterceptor`) necessita de declaração compulsória do CDI dentro do `beans.xml` da aplicação cliente para que capture o tempo de execução e o fluxo do `Who` e `Where`.
- **Rastreabilidade**: Integra a extração de OTel `traceId` capturando e limpando do `ThreadLocal` tradicional de forma segura através do bloco `finally`.

### [Implementação Quarkus 3.27](Logs/biblioteca_quarkus.md)
**Código-fonte da biblioteca nativa Quarkus**

Construída para compilação estática em Native Image (GraalVM) e compatibilidade total com cenários reativos. Diferencia-se por não possuir overhead de *bridges* na geração do JSON.
- **Contexto (MDC)**: Usa estritamente a aba nativa em `org.jboss.logging.MDC`.
- **Reatividade Integrada**: Substitui a frágil gestão do ThreadLocal puro pela associação obrigatória ao `quarkus-smallrye-context-propagation`, atrelando contexto (OTel e MDC) à própria *Virtual Thread* ou pipeline reativo Mutiny do Vert.x.
- **Ecossistema Embutido**: O JSON é acionado com simples `quarkus.log.console.json=true`, as rotinas de métrica ganham vida sob `quarkus-micrometer-registry-prometheus` e a auto-instrumentação baseada em OpenTelemetry cuida silenciosamente da propagação dos Cabeçalhos (*Headers*) em HTTP de saída e entrada.

---

## Fluxo de Vida da Requisição (Data Flow)

A rotina abaixo ilustra como os pacotes de contexto atuam estruturalmente na manutenção dos metadados nas implementações:

```text
1. Requisição chega (JAX-RS / Rota Reativa)
      │
      ▼
2. Filtro (ContainerRequestFilter) e OTel
   - OTel inicia span/trace.
   - GerenciadorContextoLog inicializa MDC (userId, servico, traceId e spanId originais).
      │
      ▼
3. Processamento e Regras
   - Interceptors anotados (@Logged) repassam para o MDC temporariamente métricas e [classe, metodo].
   - LogSistematico é acionado. SanitizadorDados oblitera senhas/P1.
   - O MDC global se funde ao JSON final (stdout).
      │
      ▼
4. Destruição do Contexto Funcional 
   - Bloco Finally é acionado transversalmente.
   - GerenciadorContextoLog.limpar() apaga o MDC preservando a ThreadPool limpa.
```

---

## Futura Implementação

### Módulo de Extensão Quarkus
Visando simplificar radicalmente a instalação e eliminar declarações passivas (como CDI Interceptors fixos e reflexões baseadas no `@Logged` que demandam varredura no tempo de execução), há o projeto em potencial para portar a biblioteca Quarkus para um **Módulo de Extensão Quarkus (Quarkus Extension)** real.
A extensão automatizaria a injeção em *Build-Time* do compilador Quarkus, registrando o `LogSistematico`, os interceptores de métricas e os componentes de Context Propagation em fases antecipadas do *Bytecode*, maximizando o desempenho final da imagem nativa.

---

## Conceitos Não Aplicáveis ou Fora do Escopo do Projeto

Parte da documentação histórica propunha abordagens monolíticas de observabilidade que hoje escapam da responsabilidade de uma biblioteca estrita de Logging (responsabilidade segregada aos frameworks Quarkus base). Abaixo destacam-se os temas reclassificados como **fora do escopo**:

### 1. Separação em Módulos "OBSERVA4J-*" (Core, Tracing, Exceptions, Metrics, Audit)
A arquitetura anterior idealizava uma coleção imensa sob o guarda-chuva de `observa4j` — contemplando repórteres de Exception (para o backend Sentry), Heath-checks customizados, auditores complexos e tracing independente.
A nova realidade dita que **APENAS a injeção estruturada do JSON e do MDC via DSL (a biblioteca `logging-quarkus`) fazem parte do SDK**. Ferramentas como Prometheus, Jaeger/Tracing e Exception Backend já estão solucionados nativamente por extensões nativas do Quarkus sem que a nossa biblioteca seja o "Ator Principal" que os engole.

### 2. Prefixos de Configuração Proprietários
O desenho em que dezenas de propriedades eram marcadas como `observa4j.tracing.exporter`, `observa4j.exceptions.reporter` não é válido. A plataforma usa as configurações padronizadas de repositório da tecnologia Quarkus (ex: `quarkus.otel.exporter.otlp.endpoint`).

### 3. Integração Direta App-Logstash/GELF
Os módulos do passado descreviam fluxos da própria aplicação disparar seus arquivos e logs direto para endpoints UDP do Graylog/Logstash. A arquitetura de implantação atual (Nuvem / Kubernetes / Container) orienta saídas de logging sempre para `STDOUT`. O escoamento via rede, TCP, ou drivers, é uma responsabilidade da Infraestrutura através de DaemonSets e coletores (OTel Collector Agent, FluentBit), **não da Aplicação/SDK.**

# Logging Sistemático em Aplicações Java

Biblioteca e padrão arquitetural para logging estruturado em aplicações Java, fundamentados em estudos empíricos e práticas validadas pela indústria. O objetivo é afastar o logging do empirismo — onde cada desenvolvedor decide subjetivamente o que, como e quando registrar — e elevá-lo à categoria de **componente arquitetural com contratos claros**.

> [!NOTE]
> Este repositório cobre o pilar de **logging** da observabilidade. Conceitos de métricas e tracing distribuído são documentados em `concepts/` como contexto arquitetural, mas suas implementações completas estão fora do escopo atual.

---

## O Projeto

| Artefato | Descrição | Versão |
|---|---|---|
| `lib-logging-quarkus` | Biblioteca nativa — JBoss Logging + ArC + OTel | Quarkus 3.32.3, Java 25 |
| `concepts/` | Padrão conceitual: 5W1H, campos canônicos, métricas, tracing | — |

A biblioteca expõe uma DSL em português com validação em tempo de compilação. O compilador Java — via `sealed interfaces` — impede que um log seja emitido sem as dimensões obrigatórias *What* e *Where*. Logs incompletos são erros de compilação, não bugs silenciosos em produção.

O JSON estruturado produzido segue nomes de campos canônicos definidos em [`concepts/FIELD_NAMES.md`](concepts/FIELD_NAMES.md), garantindo que qualquer pipeline de observabilidade (ELK, Loki, Datadog) receba campos uniformes sem configuração adicional de parser.

---

## Início Rápido

### Pré-requisitos

- Java 25
- Maven 3.9+
- Quarkus 3.32.3

### Build e execução em modo dev

```bash
cd lib-logging-quarkus
./mvnw quarkus:dev
```

### Build do artefato

```bash
mvn -B clean install -Dno-format
```

### Build nativo (GraalVM)

```bash
mvn -B install -Dnative -Dquarkus.native.container-build
```

---

## Usando a DSL

### Uso mínimo obrigatório

```java
LOG
    .registrando("Pedido criado")
    .em(PedidoService.class, "criar")
    .info();
```

### Uso completo com todas as dimensões do 5W1H

```java
LOG
    .registrando("Pagamento recusado")              // What  — obrigatório
    .em(PagamentoService.class, "processar")        // Where — obrigatório
    .porque("Saldo insuficiente no gateway")        // Why   — opcional
    .como("API REST — POST /pagamentos")            // How   — opcional
    .comDetalhe("pedidoId",  pedido.getId())
    .comDetalhe("errorCode", "PAG-4022")            // chave KEDB
    .comDetalhe("token",     req.token())           // mascarado automaticamente: "****"
    .erro(excecao);
```

As dimensões *Who* (`userId`, `applicationName`) e *When* (`timestamp`) são injetadas automaticamente via MDC — o desenvolvedor não as declara.

### Interceptação via anotação

Aplique `@Logged` em um bean ou método CDI para injeção automática de contexto (`userId`, `traceId`, `spanId`, `classe`, `metodo`) em todos os logs emitidos dentro daquele escopo. O interceptor também registra métricas Micrometer de duração e falha quando disponíveis.

```java
@ApplicationScoped
@Logged
public class PedidoService {
    // userId, traceId e spanId presentes automaticamente em todos os logs
}
```

### Eventos de negócio

Use `.comDetalhe("eventType", ...)` para distinguir eventos de negócio de eventos técnicos nas ferramentas de observabilidade:

```java
LOG
    .registrando("Pedido concluído")
    .em(PedidoService.class, "concluir")
    .porque("Pagamento confirmado pelo gateway")
    .comDetalhe("eventType",  "ORDER_COMPLETED")
    .comDetalhe("pedidoId",   pedido.getId())
    .comDetalhe("valorTotal", pedido.getValorTotal())
    .info();
```

O campo `detalhe_eventType` no JSON habilita dashboards de analytics em tempo real sem necessidade de pipeline de analytics separado.

---

## Saída JSON Estruturada

Todo evento é emitido como JSON com campos canônicos. Exemplo para o caso de erro acima:

```json
{
  "timestamp":        "2026-03-11T21:55:00.123Z",
  "level":            "ERROR",
  "message":          "Pagamento recusado",
  "applicationName":  "pedidos-service",
  "userId":           "usr-4821",
  "traceId":          "4bf92f3577b34da6a3ce929d0e0e4736",
  "spanId":           "a3ce929d0e0e4736",
  "log_classe":       "PagamentoService",
  "log_metodo":       "processar",
  "log_motivo":       "Saldo insuficiente no gateway",
  "log_canal":        "API REST — POST /pagamentos",
  "detalhe_pedidoId": "4821",
  "detalhe_errorCode":"PAG-4022",
  "detalhe_token":    "****"
}
```

A convenção de prefixos isolam as origens dos campos:

| Prefixo | Origem | Exemplo |
|---|---|---|
| _(sem prefixo)_ | Infraestrutura / MDC automático | `userId`, `traceId`, `applicationName` |
| `log_` | DSL — declarado pelo desenvolvedor | `log_motivo`, `log_canal` |
| `detalhe_` | `.comDetalhe(chave, valor)` | `detalhe_pedidoId`, `detalhe_errorCode` |

Para a lista completa de campos, consulte [`concepts/FIELD_NAMES.md`](concepts/FIELD_NAMES.md).

---

## Conceitos

A pasta [`concepts/`](concepts/) documenta os fundamentos arquiteturais do padrão:

| Documento | Conteúdo |
|---|---|
| [`5W1H.md`](concepts/5W1H.md) | Anatomia do evento semântico: Who, What, When, Where, Why, How |
| [`CODING_STANDARDS.md`](concepts/CODING_STANDARDS.md) | Padrões obrigatórios, proibidos e checklist de conformidade |
| [`FIELD_NAMES.md`](concepts/FIELD_NAMES.md) | Campos canônicos de saída JSON — contratos que todos os módulos devem respeitar |
| [`METRICS.md`](concepts/METRICS.md) | O segundo pilar: tipos de medidores, cardinalidade, alertas por sintoma |
| [`DISTRIBUTED_TRACING.md`](concepts/DISTRIBUTED_TRACING.md) | O terceiro pilar: trace, span, propagação W3C TraceContext, correlação com logs |

### O framework 5W1H

Todo evento de log deve responder a seis dimensões investigativas. As dimensões *Who* e *When* são preenchidas automaticamente pela infraestrutura; as demais são guiadas pela DSL.

| Dimensão | Pergunta | Fonte |
|---|---|---|
| **Who** | Quem é o ator? | MDC automático — `SecurityContext` / JWT |
| **What** | Qual é o evento? | `.registrando("...")` — obrigatório |
| **When** | Quando ocorreu? | Automático — formatador JSON (UTC, ms) |
| **Where** | Onde no sistema? | `.em(Classe.class, "metodo")` — obrigatório |
| **Why** | Qual o motivo de negócio? | `.porque("...")` — opcional |
| **How** | Por qual canal chegou? | `.como("...")` — opcional |

### Os Três Pilares da Observabilidade

```
                          Observabilidade
                    ┌─────────┼─────────┐
                    │         │         │
                  Logs     Métricas  Traces
                    │         │         │
              O que aconteceu  Quanto  Onde demorou
              e em que contexto?  e com que taxa?  ou falhou?
```

O `traceId` gerado pelo OpenTelemetry e propagado via `W3C TraceContext` (`traceparent`) é o identificador que une os três sinais na mesma requisição.

> **Métricas dizem *se* há um problema; Traces dizem *onde* ele está; Logs dizem *qual* foi o erro exato.**

### Segurança e mascaramento automático

O `SanitizadorDados` intercepta valores sensíveis pelo nome da chave antes de qualquer escrita, sem ação do desenvolvedor:

| Categoria | Chaves detectadas | Valor no JSON |
|---|---|---|
| Credenciais | `password`, `token`, `secret`, `cvv`, `apikey` | `"****"` |
| Dados pessoais | `cpf`, `email`, `cardnumber`, `pan` | `"[PROTEGIDO]"` |

> [!WARNING]
> O mascaramento é a última linha de defesa, não a única. O desenvolvedor deve conhecer o que passa em `.comDetalhe()`. O transporte de logs deve usar SSL/TLS — mascaramento sem criptografia no canal expõe campos de contexto a qualquer observador de rede.

---

## Erros Comuns

> [!CAUTION]
> **Logging sem estrutura** — `log.info("Pedido " + id + " salvo")` não pode ser consultado, filtrado ou agregado. Comece estruturado desde o primeiro commit; migrar em produção leva meses.

**Mensagens sem contexto de entidade** — `log.error("Falha ao salvar")` não tem valor diagnóstico. Inclua sempre o identificador da entidade afetada e o motivo de negócio.

**`traceId` gerado manualmente** — `UUID.randomUUID()` cria um identificador que não correlaciona com nenhum span no Jaeger ou Grafana Tempo. O `traceId` deve sempre ser extraído do contexto OTel ativo.

**MDC sem limpeza no `finally`** — contexto da requisição anterior vaza para a próxima thread do pool. Use `@Logged` ou garanta a limpeza explícita no `finally`.

**Cardinality explosion em métricas** — `userId`, `requestId` ou URLs completas como labels Prometheus criam milhões de séries temporais. Dados de alta cardinalidade pertencem a logs e traces, não a métricas.

**Alertas em causas, não em sintomas** — alertar em `CPU > 80%` em vez de `latência > SLA` produz fadiga de alertas. Todo alerta deve exigir uma ação humana específica.

---

## Infraestrutura Local

O diretório [`containers-dev/`](containers-dev/) fornece um stack de observabilidade completo via Docker Compose (Prometheus, Grafana, Loki, OpenTelemetry Collector, Jaeger) para desenvolvimento local. Consulte o [`containers-dev/QUICKSTART.md`](containers-dev/QUICKSTART.md) para instruções de setup.

---

## Referências

**Fundamentos deste padrão:**

- Microsoft Research (2010) — *Characterizing Logging Practices in Open-Source Software*
- Anton Chuvakin — *Security Information and Event Management*
- Chris Richardson — [Application Logging](https://microservices.io/patterns/observability/application-logging.html), [Distributed Tracing](https://microservices.io/patterns/observability/distributed-tracing.html), [Exception Tracking](https://microservices.io/patterns/observability/exception-tracking.html), [Audit Logging](https://microservices.io/patterns/observability/audit-logging.html) — microservices.io
- Iluwatar — [microservices-log-aggregation](https://github.com/iluwatar/java-design-patterns/tree/master/microservices-log-aggregation), [microservices-distributed-tracing](https://github.com/iluwatar/java-design-patterns/tree/master/microservices-distributed-tracing)

**Observabilidade e SRE:**

- [OpenTelemetry Specification](https://opentelemetry.io/docs/specs/)
- [W3C TraceContext Recommendation](https://www.w3.org/TR/trace-context/)
- [Quarkus 3.x — OpenTelemetry Guide](https://quarkus.io/guides/opentelemetry)
- [Elasticsearch Common Schema (ECS)](https://www.elastic.co/guide/en/ecs/current/)
- [Google SRE Book — Monitoring Distributed Systems](https://sre.google/sre-book/monitoring-distributed-systems/)
- [Dapper — Large-Scale Distributed Systems Tracing](https://research.google/pubs/pub36356/)
- Cindy Sridharan — [Monitoring and Observability](https://copyconstruct.medium.com/monitoring-and-observability-8417d1952e1c)

**Livros:**

- Charity Majors, Liz Fong-Jones, George Miranda — *Observability Engineering* (O'Reilly, 2022)
- Betsy Beyer, Chris Jones et al. — *Site Reliability Engineering* (Google, 2016)
- Cindy Sridharan — *Distributed Systems Observability* (O'Reilly, 2018)
- James Turnbull — *The Art of Monitoring* (O'Reilly, 2018)

**Ferramentas:**

- [Prometheus](https://prometheus.io/) — métricas e alertas
- [Grafana](https://grafana.com/) — dashboards e visualização
- [Grafana Loki](https://grafana.com/oss/loki/) — armazenamento e consulta de logs
- [Elasticsearch + Kibana (ELK)](https://www.elastic.co/) — indexação e busca de logs estruturados
- [Jaeger](https://www.jaegertracing.io/) — backend de tracing distribuído open-source
- [Grafana Tempo](https://grafana.com/oss/tempo/) — armazenamento de traces escalável
- [OpenTelemetry Collector](https://opentelemetry.io/docs/collector/) — pipeline de telemetria agnóstico de vendor
- [Datadog](https://www.datadoghq.com/) — plataforma de observabilidade gerenciada
# Logging Sistemático em Aplicações Java

Biblioteca e padrão arquitetural para logging estruturado em aplicações Java, fundamentados em estudos empíricos e práticas validadas pela indústria. O objetivo é afastar o logging do empirismo — onde cada desenvolvedor decide subjetivamente o que, como e quando registrar — e elevá-lo à categoria de **componente arquitetural com contratos claros**.

---

## O Projeto

Este repositório contém três artefatos independentes e um documento de padrão que os fundamenta:

| Artefato | Descrição | Quando usar |
|---|---|---|
| `logging_revisado.md` | Padrão conceitual: 5W1H, taxonomia, níveis, segurança, governança | Leitura obrigatória antes de qualquer implementação |
| `lib-logging-slf4j` | Biblioteca portável — SLF4J 2.x + Log4j2 + CDI Jakarta EE | Wildfly, TomEE, Payara, OpenLiberty |
| `lib-logging-quarkus` | Biblioteca nativa — JBoss Logging + ArC + OTel | Quarkus 3.27, JVM ou native image GraalVM |
| `quarkus-logging-extension` | Extensão Quarkus — build-time processing, auto-config, Dev UI | Quarkus 3.27 com zero configuração manual |

Os três módulos expõem a **mesma API pública** (`LogSistematico`, `@Logged`) e produzem o mesmo JSON estruturado, com nomes de campos canônicos idênticos. Um pipeline de observabilidade que consuma logs de serviços baseados em Jakarta EE e Quarkus recebe campos uniformes, sem queries distintas por runtime.

**Escopo:** este projeto cobre exclusivamente o pilar de **logging**. Métricas (Micrometer/Prometheus) e tracing distribuído (OpenTelemetry) são mencionados como contexto e como receptores dos identificadores de correlação gerados pela biblioteca, mas suas implementações completas estão fora do escopo atual.

---

## Fundamentação

O padrão é construído sobre a triangulação de quatro fontes:

- **Microsoft Research (2010)** — estudo empírico em bases de código de 2,5 a 10,4 milhões de linhas que identificou os cinco cenários primários onde desenvolvedores inserem logs: asserções, verificação de retorno, exceções, pontos de execução e rastreamento.
- **Anton Chuvakin** — categorização de eventos que devem *obrigatoriamente* gerar registros independente de qualquer heurística: autenticação, mudanças de estado, chamadas externas, disponibilidade, exaustão de recursos e entradas inválidas.
- **Chris Richardson — microservices.io** — catálogo de padrões de observabilidade para arquiteturas distribuídas: *Application Logging*, *Distributed Tracing*, *Exception Tracking* e *Audit Logging*, cada um com responsabilidades e fronteiras distintas.
- **Iluwatar — java-design-patterns** — implementações de referência de `microservices-log-aggregation` e `microservices-distributed-tracing`, que formalizam o log como fluxo *append-only* e tratam a agregação centralizada como componente de primeira classe.

---

## Conceitos Centrais

### O log como fluxo append-only

O log é uma sequência ordenada de registros imutáveis: cada evento é acrescentado ao final, nunca modificado retroativamente. Isso tem três implicações diretas:

- **Fonte da verdade:** um banco de dados registra o estado *atual*; o log registra cada *mudança de estado* ao longo do tempo. Um log completo permite reconstruir o estado de qualquer entidade em qualquer ponto do passado.
- **Agregação centralizada obrigatória:** em microsserviços, os logs de uma única operação distribuída ficam espalhados em dezenas de processos. Sem agregação centralizada (ELK, Loki, Datadog), o diagnóstico é impraticável.
- **Imutabilidade como contrato:** alterar ou deletar um registro viola o padrão e pode comprometer investigações de segurança e conformidade regulatória.

### O framework 5W1H

Todo evento de log deve responder a seis dimensões investigativas. As dimensões *When* e parte do *Where* são preenchidas automaticamente pela infraestrutura; as demais são guiadas pela DSL.

| Dimensão | Pergunta | Exemplos |
|---|---|---|
| **Who** | Quem é o ator? | `userId`, IP de origem |
| **What** | Qual é o evento? | "Pedido criado", "Login falhou" |
| **When** | Quando ocorreu? | Timestamp ISO 8601 UTC com milissegundos |
| **Where** | Onde no sistema? | Serviço, classe, método, `requestId`, `traceId` |
| **Why** | Qual o motivo de negócio? | "Saldo insuficiente", "Sessão expirada" |
| **How** | Por qual canal chegou? | "API REST", "Fila assíncrona", "Job agendado" |

### DSL e Fluent Interface

A biblioteca expõe uma DSL em português com validação em tempo de compilação. A sequência de chamadas é imposta pelas `sealed interfaces` do Java 21 — não é possível emitir um log sem declarar obrigatoriamente o *What* e o *Where*:

```java
LogSistematico
    .registrando("Pagamento recusado")          // What  — obrigatório
    .em(PagamentoService.class, "processar")    // Where — obrigatório
    .porque("Saldo insuficiente no gateway")    // Why   — opcional
    .como("API REST — POST /pagamentos")        // How   — opcional
    .comDetalhe("pedidoId",  pedido.getId())
    .comDetalhe("errorCode", "PAG-4022")        // ← chave KEDB
    .comDetalhe("token",     req.token())       // ← mascarado: "****"
    .erro(excecao);
```

### Correlação por identificadores

Dois identificadores coexistem em todo evento de log:

| Identificador | Escopo | Gerado por |
|---|---|---|
| `requestId` | Uma requisição HTTP em um único serviço | Filtro JAX-RS |
| `traceId` | Toda a operação distribuída, em todos os serviços | OpenTelemetry SDK |

O `traceId` e o `spanId` — extraídos do span OTel ativo, nunca gerados como `UUID.randomUUID()` — são a chave que une logs, métricas e traces na mesma requisição.

### Segurança e mascaramento

O `SanitizadorDados` intercepta automaticamente valores sensíveis pelo nome da chave antes de qualquer registro, aplicando dois graus de proteção:

- **Mascaramento:** `"****"` para credenciais (`password`, `token`, `cvv`), `"[PROTEGIDO]"` para dados pessoais (`cpf`, `email`, `cardnumber`).
- **Redação:** omissão completa do campo — para dados sob sigilo legal ou de menores. Não implementado automaticamente; o desenvolvedor deve omitir o campo antes de chamar `.comDetalhe()`.

O transporte de logs entre nós da rede deve usar **SSL/TLS**. Mascaramento na aplicação sem criptografia no canal expõe campos de contexto (`userId`, `traceId`, nomes de entidades) a qualquer observador de rede.

### Os Três Pilares da Observabilidade

```
                          Observabilidade
                    ┌─────────┼─────────┐
                    │         │         │
                  Logs     Métricas  Traces
                    │         │         │
              O que aconteceu  Quanto  Onde foi
              e em que contexto?  e quantas vezes?  o tempo?
```

- **Logs** — registros discretos e timestampados de eventos. São o sinal mais detalhado e o mais fácil de produzir, mas o mais difícil de consultar em escala sem estrutura JSON.
- **Métricas** — medições numéricas agregadas ao longo do tempo. Baratas para armazenar, rápidas para consultar, ideais para dashboards e alertas. Exemplos: taxa de erros, latência p99, uso de CPU.
- **Traces** — registros da jornada de uma requisição através de múltiplos serviços. Cada trace é composto de spans; cada span representa uma unidade de trabalho.

O `traceId` gerado pelo OpenTelemetry e propagado via `W3C TraceContext` (`traceparent`) é o identificador que une os três sinais na mesma requisição.

> **Escopo deste projeto:** os pilares de métricas e tracing são mencionados para contextualizar o papel dos logs no ecossistema de observabilidade. A implementação desses pilares está fora do escopo atual.

---

## Erros Comuns

**Logging sem estrutura** — `log.info("Pedido " + id + " salvo")` não pode ser consultado, filtrado ou agregado. Migrar de logging não estruturado para estruturado em um sistema em produção é um esforço de meses. Comece estruturado desde o primeiro commit.

**Mensagens sem contexto de entidade** — `log.error("Falha ao salvar")` não tem valor diagnóstico em produção. O log deve incluir os identificadores da entidade afetada, o motivo de negócio e o contexto da operação.

**Log-and-throw sem contexto adicional** — registrar a mesma exceção em múltiplas camadas sem agregar informação nova gera ruído que prejudica o diagnóstico. Cada camada loga apenas o que sabe a mais sobre o erro.

**`traceId` gerado manualmente** — `UUID.randomUUID()` como `traceId` cria um identificador falso que não correlaciona com nenhum span real no Jaeger ou Zipkin. O `traceId` deve sempre ser extraído do contexto OTel ativo.

**MDC sem limpeza no `finally`** — contexto da requisição anterior vaza para a próxima requisição atendida pela mesma thread no pool, contaminando logs com dados de outras operações.

**Dados sensíveis em log** — senhas, tokens, CPF, PAN e dados de menores em logs constituem violação da LGPD. O `SanitizadorDados` é a última linha de defesa, não a única — o desenvolvedor deve conhecer o que está passando em `.comDetalhe()`.

**Cardinality explosion em métricas** — valores não limitados (`userId`, `requestId`, URL completa) como labels de métricas no Prometheus criam milhões de séries temporais. Coloque dados de alta cardinalidade em logs e traces, não em métricas.

**Alertas em causas, não em sintomas** — alertar em `CPU > 80%` em vez de `latência > SLA` produz fadiga de alertas. Todo alerta deve exigir uma ação humana específica; se a resposta correta for "aguardar", não é um alerta — é uma linha de log.

---

## Referências

**Fundamentos deste padrão:**
- Microsoft Research (2010) — *Characterizing Logging Practices in Open-Source Software* — estudo empírico sobre padrões de inserção de logs
- Anton Chuvakin — *Security Information and Event Management* — categorização de eventos críticos de segurança
- Chris Richardson — [Application Logging](https://microservices.io/patterns/observability/application-logging.html) — microservices.io
- Chris Richardson — [Distributed Tracing](https://microservices.io/patterns/observability/distributed-tracing.html) — microservices.io
- Chris Richardson — [Exception Tracking](https://microservices.io/patterns/observability/exception-tracking.html) — microservices.io
- Chris Richardson — [Audit Logging](https://microservices.io/patterns/observability/audit-logging.html) — microservices.io
- Iluwatar — [java-design-patterns: microservices-log-aggregation](https://github.com/iluwatar/java-design-patterns/tree/master/microservices-log-aggregation)
- Iluwatar — [java-design-patterns: microservices-distributed-tracing](https://github.com/iluwatar/java-design-patterns/tree/master/microservices-distributed-tracing)

**Observabilidade e SRE:**
- [OpenTelemetry Specification](https://opentelemetry.io/docs/specs/)
- [W3C TraceContext Recommendation](https://www.w3.org/TR/trace-context/)
- [Quarkus 3.x — OpenTelemetry Guide](https://quarkus.io/guides/opentelemetry)
- [Elasticsearch Common Schema (ECS)](https://www.elastic.co/guide/en/ecs/current/)
- [Google SRE Book — Monitoring Distributed Systems](https://sre.google/sre-book/monitoring-distributed-systems/) — capítulo fundacional sobre alertas baseados em sintomas
- [Dapper — Large-Scale Distributed Systems Tracing](https://research.google/pubs/pub36356/) — paper seminal do Google sobre tracing distribuído
- Cindy Sridharan — [Monitoring and Observability](https://copyconstruct.medium.com/monitoring-and-observability-8417d1952e1c) — distinção entre monitoramento e observabilidade

**Livros:**
- Charity Majors, Liz Fong-Jones, George Miranda — *Observability Engineering* (O'Reilly, 2022) — observabilidade moderna e debugging por alta cardinalidade
- Betsy Beyer, Chris Jones et al. — *Site Reliability Engineering* (Google, 2016) — práticas SRE, especialmente os capítulos de monitoramento e alertas
- Cindy Sridharan — *Distributed Systems Observability* (O'Reilly, 2018) — guia conciso dos três pilares
- James Turnbull — *The Art of Monitoring* (O'Reilly, 2018) — guia prático de infraestrutura de monitoramento

**Ferramentas:**
- [Prometheus](https://prometheus.io/) — coleta de métricas e alertas
- [Grafana](https://grafana.com/) — dashboards e visualização
- [Grafana Loki](https://grafana.com/oss/loki/) — armazenamento e consulta de logs
- [Elasticsearch + Kibana (ELK)](https://www.elastic.co/) — indexação e busca de logs estruturados
- [Jaeger](https://www.jaegertracing.io/) — backend de tracing distribuído open-source
- [Grafana Tempo](https://grafana.com/oss/tempo/) — armazenamento de traces escalável e de baixo custo
- [OpenTelemetry Collector](https://opentelemetry.io/docs/collector/) — pipeline de telemetria agnóstico de vendor
- [Datadog](https://www.datadoghq.com/) — plataforma de observabilidade gerenciada
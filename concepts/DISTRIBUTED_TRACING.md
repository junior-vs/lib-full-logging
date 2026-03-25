# Rastreamento Distribuído (Distributed Tracing)

> Padrão estrutural para correlação de execuções através do ecossistema de microsserviços. O rastreamento unifica logs dispersos em uma narrativa única de transação de usuário.
>
> *Conceitualmente aderente ao padrão homônimo catalogado no repositório [iluwatar/java-design-patterns](https://github.com/iluwatar/java-design-patterns).*

---

## 1. Visão Geral

Em uma arquitetura de microsserviços, uma única operação do usuário (como fechar um pedido) pode percorrer dezenas de serviços distintos. Cada serviço registra os próprios eventos, mas esses rastros ocorrem simultaneamente a milhares de outras requisições. Sem um identificador global, reconstruir a jornada de um problema torna-se virtualmente impossível.

O Rastreamento Distribuído resolve essa questão atribuindo um **Trace ID** no ponto de entrada e propagando-o por cada salto de rede, agrupando *Spans* (blocos de execução) e *Logs* (eventos granulares) sob o mesmo guarda-chuva.

---

## 2. Posicionamento nos Três Pilares da Observabilidade

O Rastreamento Distribuído é um dos três pilares da **Observabilidade** — a capacidade de entender o estado interno de um sistema a partir das saídas que ele gera. Os três pilares operam de forma complementar e cada um responde a uma pergunta distinta:

| Pilar | Pergunta Central | Natureza do Dado |
| --- | --- | --- |
| **Logging** | *"O que aconteceu em um momento específico?"* | Registros discretos de eventos textuais com severidade |
| **Monitoring / Metrics** | *"Como o sistema está se comportando agora e quais são as tendências?"* | Valores numéricos agregados (CPU, memória, req/min) |
| **Tracing** | *"Por onde a requisição passou e por que demorou ou falhou?"* | Grafo temporal de operações encadeadas entre serviços |

O mnemônico prático que resume a interação entre os três: **Métricas dizem *se* há um problema; Traces dizem *onde* ele está; Logs dizem *qual* foi o erro exato.** O OpenTelemetry é a infraestrutura que une os três sinais em um único pipeline de telemetria.

Operar sistemas distribuídos sem observabilidade é equivalente a conduzir um veículo com o painel apagado e o capô selado — o problema só se revela quando o sistema para completamente.

---

## 3. Conceitos Fundamentais

### 3.1 Trace ID

Cada requisição que entra no sistema recebe um **identificador único global** chamado Trace ID. Esse identificador é propagado entre os serviços — geralmente via cabeçalhos HTTP — de modo que todas as ações realizadas em nome daquela requisição possam ser correlacionadas em uma única árvore de execução, independentemente de quantas fronteiras de rede sejam atravessadas.

### 3.2 Span

O Span é a **unidade básica de trabalho** dentro de um rastreamento. Cada operação significativa — a execução de um método, uma consulta ao banco de dados, uma chamada remota a outro serviço — é representada por um Span. Um Span registra obrigatoriamente seu momento de início e de fim, possibilitando o cálculo preciso de latência individual de cada componente.

Os Spans carregam metadados enriquecidos:

- **Trace ID e Span ID**: identificadores que correlacionam Spans de diferentes serviços pertencentes à mesma transação.
- **Tags**: pares chave-valor para busca e filtragem (URL acessada, método HTTP, código de status, ID de cliente etc.).
- **Logs / Eventos**: mensagens associadas a momentos específicos dentro do Span, úteis para capturar exceções ou erros no contexto exato onde ocorreram.

### 3.3 Estrutura Hierárquica (Root Span e Child Spans)

Os Spans são aninhados, formando uma **estrutura em árvore**. O Span criado no ponto de entrada da requisição é chamado de **Root Span**; qualquer operação subsequente gera um **Child Span** vinculado ao pai que o originou. Essa hierarquia permite visualizar:

- A **ordem das operações**: qual serviço chamou qual e em que sequência.
- **Onde o tempo foi gasto**: a comparação entre a duração do Root Span e a soma dos Child Spans expõe imediatamente onde estão os gargalos de desempenho.

### 3.4 Propagação de Contexto

Para que o rastreamento funcione entre serviços distintos, o Trace ID precisa "viajar" junto com a requisição. Esse mecanismo é chamado de **Propagação de Contexto** e é executado via cabeçalhos HTTP. O padrão de mercado é o **W3C TraceContext** (`traceparent: 00-[traceId]-[parentSpanId]-01`), que garante interoperabilidade entre implementações de diferentes fornecedores.

---

## 4. OpenTelemetry (OTel) — O Padrão de Mercado

O **OpenTelemetry** é um projeto de código aberto mantido pela CNCF (*Cloud Native Computing Foundation*) que fornece um padrão unificado — APIs, SDKs e protocolos — para instrumentar, gerar, coletar e exportar dados de telemetria (traces, métricas e logs).

Antes do OpenTelemetry, cada ferramenta de monitoramento exigia o uso de sua biblioteca proprietária, criando *vendor lock-in*: migrar do Datadog para o New Relic, por exemplo, implicava reescrever todo o código de instrumentação. O OTel eliminou esse problema — instrumenta-se o código **uma única vez** usando o padrão aberto e os dados podem ser enviados para qualquer plataforma de mercado compatível.

### 4.1 Instrumentação

A instrumentação pode ocorrer de duas formas:

- **Automática**: em frameworks como o Quarkus, ao adicionar a extensão `quarkus-opentelemetry`, todos os endpoints REST são rastreados automaticamente sem qualquer alteração no código de negócio.
- **Manual (Customizada)**: desenvolvedores podem usar a anotação `@WithSpan` em métodos específicos ou a API de `Tracer` para criar Spans customizados, garantindo que partes críticas da lógica interna também sejam monitoradas detalhadamente.

### 4.2 Protocolo OTLP e Pipeline de Coleta

O OpenTelemetry utiliza o protocolo **OTLP** (*OpenTelemetry Line Protocol*) para transmitir dados de telemetria para fora da aplicação. O pipeline típico segue o seguinte fluxo:

```
Aplicação (OTel SDK)
        │
        │  OTLP (gRPC ou HTTP)
        ▼
OTel Collector  ──(filtragem, aggregation, sampling)──▶  Backend de Análise
                                                          (Jaeger / Grafana Tempo / Datadog)
```

Em ambientes menores ou de desenvolvimento, a aplicação pode exportar diretamente para um backend compatível, dispensando o Collector intermediário. Em produção e larga escala, o Collector é recomendado pois centraliza políticas de filtragem, amostragem e roteamento sem alterar o código da aplicação.

### 4.3 Ferramentas de Visualização

Os dados coletados são armazenados e visualizados em ferramentas especializadas:

- **Jaeger**: amplamente utilizado em ambientes cloud-native; oferece cronogramas detalhados de requisições e permite análise de causa raiz diretamente no painel.
- **Zipkin**: alternativa madura, especialmente em ecossistemas Spring.
- **Grafana Tempo**: ganhou forte adoção corporativa por integrar-se nativamente ao ecossistema Grafana (Loki para logs, Prometheus para métricas), unificando os três pilares de observabilidade em um único painel operacional.

---

## 5. Quando Utilizar (When to Use)

Baseado nos preceitos arquiteturais de design de microsserviços, este padrão é imperativo quando:

- Múltiplos serviços formam um único caminho de requisição de usuário e o diagnóstico de falhas exige visibilidade além das fronteiras do serviço.
- Monitorar e descobrir gargalos de performance (*bottlenecks*) em um ambiente altamente distribuído é crítico para o negócio.
- A correlação de Logs e Métricas de serviços independentes é o único meio prático de atestar a saúde geral do sistema perante os usuários.

---

## 6. Integração Nativa no Quarkus

O projeto utiliza a extensão `quarkus-opentelemetry` como provedora primária do rastreamento. O esforço de configuração é mínimo, pois a plataforma injeta os agrupadores automaticamente.

Com a dependência instalada, o Quarkus:

- Cria o *Root Span* nos endpoints HTTP de entrada (Controllers / JAX-RS).
- Propaga o cabeçalho **W3C TraceContext** (`traceparent`) nas chamadas do REST Client para outros serviços.
- Injeta automaticamente `traceId` e `spanId` no MDC, de modo que o provedor JSON do JBoss Logging os empacote na saída do Console (`logging-quarkus`).

### A Arquitetura do `logging-quarkus` (LogSistematico)

A biblioteca captura esses metadados passivamente a cada `.info()` ou `.erro()`. Assim, um erro no Serviço A e um processamento no Serviço B carregam o mesmo conjunto chave-valor em seus respectivos `stdout`, unificando as visualizações em agregadores (Kibana, Loki).

---

## 7. Extração Segura de Trace (vs. Falsificação)

É anti-padrão gerar `UUID.randomUUID()` aleatoriamente e tratar o resultado como `traceId`. Metadados inventados pela aplicação não criam grafos inter-serviços nas ferramentas modernas — o dado deve vir da árvore legítima do rastreador subjacente.

Ao acionar blocos passivos assíncronos que perdem o contexto nativo da requisição HTTP, deve-se usar os componentes de contexto da biblioteca:

```java
// O GerenciadorContextoLog descobre se existe uma transação OTel ativa
// e a transporta para o ecossistema do Log sem que o dev precise manusear IDs.
try {
    gerenciadorContextoLog.inicializar(usuarioAtivo); // Puxa traceId/spanId reais e injeta no MDC
    // ...
} finally {
    gerenciadorContextoLog.limpar();
}
```

---

## 8. Trade-offs (Custo e Desvantagens)

Embora agregue grande maturidade ao diagnóstico, a arquitetura de rastreamento exige atenção às seguintes ressalvas:

- **Overhead Transacional**: a coleta de traces em alta amostragem adiciona esforço computacional, latência extra a cada requisição HTTP (injeção de cabeçalhos) e aumento do tamanho dos payloads internos.
- **Complexidade de Infraestrutura**: exige configuração e sustentação de tecnologias externas dedicadas (Jaeger, Zipkin, OTel Collector, bancos de dados de grande retenção para Spans).
- **Gerenciamento de Volume**: em ecossistemas de grande escala, gravar 100% dos metadados satura o disco. Técnicas como *Tail-based Sampling* — reter apenas os traces que contêm falhas — são necessárias para controlar o volume sem perder diagnóstico.

---

## 9. `traceId` vs `spanId`

Os dois identificadores são complementares e operam em granularidades diferentes dentro da mesma árvore de execução:

| Identificador | Granularidade | Propósito Real |
| --- | --- | --- |
| `traceId` | **Toda a transação** — atravessa bordas de múltiplos serviços | Correlacionar todos os spans de uma requisição de ponta a ponta; é o identificador de busca no Jaeger/Grafana Tempo |
| `spanId` | **Uma operação individual** — um método, uma query, uma chamada downstream | Identificar o nó exato da árvore onde ocorreu a falha ou o gargalo de latência |

O `traceId` é constante ao longo de toda a requisição; o `spanId` muda a cada nova unidade de trabalho, sempre referenciando o `spanId` do pai que o originou. Juntos, eles formam o par mínimo necessário para diagnóstico completo em um ambiente distribuído.

*Ambos adotam o formato `camelCase` em conformidade com a taxonomia nativa da saída OTel/Quarkus Logging (`traceId`, `spanId`).*

---

## 10. Padrões Relacionados (Related Patterns)

O Distributed Tracing atua de forma muito mais poderosa quando desenhado em conjunto com os padrões clássicos de microsserviços:

- **API Gateway**: atua como o ponto absoluto de entrada (*front-door*) e é geralmente responsável por criar o primeiro *Root Span* na requisição que viaja aos *downstreams*.
- **Log Aggregation**: o tracing isolado é pouco útil; ele brilha quando associado ao Log Aggregation (ex.: Elasticsearch / Loki), garantindo que ao pesquisar por um Trace ID se visualize o relatório inteiro agrupado.
- **Circuit Breaker**: utilizados em conjunto para mapear falhas em cascata; o rastreamento expõe graficamente qual nó ativou o disjuntor de circuito.
- **Saga**: orquestra transações distribuídas (onde cada nó encerra partes de uma transação maior); o Trace ID persistido no rastro mantém a integridade dos cancelamentos e compensações.

---

## 11. Implementação na Biblioteca Quarkus (`lib-logging-quarkus`)

> Padrão de referência: Chris Richardson — [Distributed Tracing (microservices.io)](https://microservices.io/patterns/observability/distributed-tracing.html)

Esta seção documenta a camada de rastreamento distribuído da biblioteca. A implementação cobre os cinco requisitos do padrão microservices.io e se integra ao `GerenciadorContextoLog` e ao `LogContextoFiltro` já existentes — sem duplicar responsabilidades.

| Requisito microservices.io | Mecanismo | Componente |
|---|---|---|
| Geração automática de `traceId` e `spanId` | `quarkus-opentelemetry` auto-instrumenta endpoints JAX-RS | Nativo — sem código adicional |
| Propagação W3C TraceContext entre serviços | Cabeçalho `traceparent` via `quarkus-smallrye-context-propagation` | Nativo — sem código adicional |
| Registro de início, fim e metadados por span | CDI `@AroundInvoke` com `Tracer` OTel | `@Rastreado` + `RastreamentoInterceptor` |
| Exportação para backend configurável | `application.properties` + OTLP | Configuração — sem código adicional |
| `traceId` em cada linha de log | MDC populado pelo `GerenciadorContextoLog` | `LogContextoFiltro` (já existente) |

---

### 11.1. Atualização da Estrutura do Projeto

Os novos componentes de tracing se encaixam no pacote `tracing/` — separado do pacote `context/` de logging para manter a separação de responsabilidades:

```
lib-logging-quarkus/
└── src/main/java/br/com/seudominio/log/
    ├── annotations/
    │   ├── Logged.java              ← @InterceptorBinding CDI (logging)
    │   └── Rastreado.java           ← @InterceptorBinding CDI (tracing)   ✦ novo
    ├── context/
    │   ├── LogContexto.java
    │   ├── GerenciadorContextoLog.java
    │   └── SanitizadorDados.java
    ├── core/
    │   └── LogEvento.java
    ├── dsl/
    │   ├── LogEtapas.java
    │   └── LogSistematico.java
    ├── filtro/
    │   └── LogContextoFiltro.java
    ├── interceptor/
    │   ├── LogInterceptor.java
    │   └── RastreamentoInterceptor.java   ← CDI @AroundInvoke + OTel Tracer  ✦ novo
    └── tracing/
        └── GerenciadorRastreamento.java   ← Ciclo de vida do Span + MDC sync  ✦ novo
```

---

### 11.2. Dependência Maven Adicional

A API do OpenTelemetry já está disponível transitivamente via `quarkus-opentelemetry`. Nenhuma dependência adicional é necessária.

```xml
<!--
    Já presente no pom.xml — provê auto-instrumentação HTTP
    e expõe io.opentelemetry.api.trace.Tracer injetável via CDI.
-->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-opentelemetry</artifactId>
</dependency>

<!--
    Já presente no pom.xml — garante propagação do span OTel
    e do MDC em pipelines reativos Mutiny / Vert.x.
-->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-context-propagation</artifactId>
</dependency>
```

---

### 11.3. Configuração de Exportação (`application.properties`)

A exportação é configurável por ambiente via propriedades — sem recompilação:

```properties
# ─── OpenTelemetry — Exportação de Traces ─────────────────────────────────────

# Endpoint do backend de análise (Jaeger via OTLP gRPC).
# Substitua pela URL do OTel Collector em produção para desacoplar o backend.
quarkus.otel.exporter.otlp.endpoint=http://jaeger:4317

# Protocolo de exportação: grpc (padrão) ou http/protobuf
# quarkus.otel.exporter.otlp.protocol=grpc

# Amostragem: always_on envia 100% dos spans para o Collector.
# Políticas de Tail-Based Sampling residem no OTel Collector — não aqui.
quarkus.otel.traces.sampler=always_on

# Nome do serviço: aparece como rótulo em todos os spans no Jaeger/Grafana Tempo.
# Deve ser único por microsserviço no ecossistema.
quarkus.application.name=pedidos-service

# ─── Backends alternativos ─────────────────────────────────────────────────────
# Zipkin  → quarkus.otel.exporter.otlp.endpoint=http://zipkin:9411/api/v2/spans
# OTel Collector → quarkus.otel.exporter.otlp.endpoint=http://otel-collector:4317
```

---

### 11.4. `@Rastreado` — Anotação CDI de Tracing

```java
package br.com.seudominio.log.annotations;

import jakarta.interceptor.InterceptorBinding;
import java.lang.annotation.*;

/**
 * Ativa rastreamento distribuído para um bean ou método CDI.
 *
 * <p>Quando aplicada, o {@link br.com.seudominio.log.interceptor.RastreamentoInterceptor}
 * cria um {@code Child Span} no span OTel ativo, registra metadados da operação
 * (classe, método, hora de início/fim) e propaga o {@code traceId} atualizado
 * para o MDC — mantendo a correlação com as linhas de log emitidas dentro
 * do método.</p>
 *
 * <p>Pode ser combinada com {@link Logged} no mesmo bean sem conflito:
 * {@code @Logged} gerencia o MDC de logging; {@code @Rastreado} gerencia
 * o span OTel. Quando usadas juntas, a ordem de execução é controlada
 * por {@code @Priority}: {@code RastreamentoInterceptor} executa primeiro
 * (cria o span), depois {@code LogInterceptor} (registra localização no MDC).</p>
 *
 * <pre>{@code
 * // Apenas tracing (sem métricas de duração Micrometer)
 * @ApplicationScoped
 * @Rastreado
 * public class IntegracaoFiscalClient { ... }
 *
 * // Tracing + Logging — interceptors acumulados
 * @ApplicationScoped
 * @Logged
 * @Rastreado
 * public class PagamentoService { ... }
 * }</pre>
 */
@InterceptorBinding
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface Rastreado {
}
```

---

### 11.5. `GerenciadorRastreamento` — Ciclo de Vida do Span

Centraliza a criação, enriquecimento e encerramento de spans. Separa a lógica
OTel do interceptor, tornando cada responsabilidade testável de forma isolada.

```java
package br.com.seudominio.log.tracing;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.MDC;

/**
 * Gerencia o ciclo de vida de spans customizados para métodos de negócio.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Criar {@code Child Span} vinculado ao span pai do contexto OTel ativo</li>
 *   <li>Registrar metadados da operação: classe, método, serviço</li>
 *   <li>Sincronizar o {@code spanId} atualizado no MDC após criação do span filho</li>
 *   <li>Marcar o span como erro em caso de exceção — com mensagem e tipo</li>
 *   <li>Encerrar o span e fechar o {@link Scope} no {@code finally}</li>
 * </ul>
 *
 * <p>O {@link Tracer} é injetado pelo CDI do Quarkus via
 * {@code quarkus-opentelemetry} — sem factory manual.</p>
 */
@ApplicationScoped
public class GerenciadorRastreamento {

    private static final String ATRIB_CLASSE  = "codigo.classe";
    private static final String ATRIB_METODO  = "codigo.metodo";
    private static final String ATRIB_SERVICO = "servico.nome";
    private static final String CAMPO_SPAN_ID = "spanId";

    @Inject
    Tracer tracer;

    @Inject
    @org.eclipse.microprofile.config.inject.ConfigProperty(
            name = "quarkus.application.name",
            defaultValue = "servico-desconhecido")
    String nomeServico;

    /**
     * Inicia um Child Span para a operação identificada por classe e método.
     *
     * <p>O span é vinculado automaticamente ao span pai do contexto OTel ativo
     * (propagado pelo Quarkus via cabeçalho W3C {@code traceparent}).
     * Se não houver span pai ativo, o OTel cria um Root Span.</p>
     *
     * <p>O {@code spanId} do novo span é sincronizado no MDC imediatamente,
     * garantindo que logs emitidos dentro do método carreguem o ID correto.</p>
     *
     * @param nomeClasse  nome simples da classe interceptada
     * @param nomeMetodo  nome do método interceptado
     * @return contexto de execução do span — deve ser fechado no {@code finally}
     */
    public ContextoSpan iniciar(String nomeClasse, String nomeMetodo) {
        var nomeSpan = nomeClasse + "." + nomeMetodo;

        var span = tracer.spanBuilder(nomeSpan)
                .setSpanKind(SpanKind.INTERNAL)
                .setParent(Context.current())
                .setAttribute(ATRIB_CLASSE,  nomeClasse)
                .setAttribute(ATRIB_METODO,  nomeMetodo)
                .setAttribute(ATRIB_SERVICO, nomeServico)
                .startSpan();

        // Torna o span filho o span corrente — necessário para que chamadas
        // aninhadas criem seus próprios Child Spans corretamente
        var scope = span.makeCurrent();

        // Sincroniza spanId no MDC: logs emitidos dentro do método carregam
        // o ID do span filho, não o ID do span pai da requisição HTTP
        MDC.put(CAMPO_SPAN_ID, span.getSpanContext().getSpanId());

        return new ContextoSpan(span, scope);
    }

    /**
     * Registra um atributo extra no span ativo — sem alterar o MDC.
     *
     * <p>Útil para enriquecer o span com identificadores de negócio
     * (ex: {@code pedido.id}, {@code pagamento.status}) visíveis
     * diretamente no painel do Jaeger/Grafana Tempo.</p>
     *
     * @param chave nome do atributo OTel (usar notação {@code dominio.campo})
     * @param valor valor do atributo
     */
    public void adicionarAtributo(Span span, String chave, String valor) {
        span.setAttribute(chave, valor);
    }

    /**
     * Marca o span como erro e registra os detalhes da exceção.
     *
     * <p>O OTel registra o stack trace completo como evento do span,
     * visível na UI do Jaeger sem necessidade de correlação manual com logs.</p>
     *
     * @param span    span a ser marcado
     * @param causa   exceção que causou o erro
     */
    public void registrarErro(Span span, Throwable causa) {
        span.setStatus(StatusCode.ERROR, causa.getMessage());
        span.recordException(causa, Attributes.empty());
    }

    /**
     * Encerra o span e fecha o Scope, restaurando o span pai como corrente.
     *
     * <p>Deve sempre ser chamado em bloco {@code finally} — o {@code Scope}
     * não é fechado automaticamente pelo OTel e seu vazamento corrompe
     * a hierarquia de spans nas requisições subsequentes na mesma thread.</p>
     *
     * @param contexto retornado por {@link #iniciar}
     */
    public void encerrar(ContextoSpan contexto) {
        try {
            contexto.scope().close();   // Restaura span pai como corrente
        } finally {
            contexto.span().end();      // Registra hora de término e envia ao Collector
        }
    }

    // ── Estrutura de retorno ──────────────────────────────────────────────────

    /**
     * Transporta o par (Span, Scope) produzido por {@link #iniciar}.
     *
     * <p>{@code record} do Java 21: imutável, sem boilerplate.
     * O {@code Scope} precisa ser fechado antes do {@code Span}
     * — a ordem de encerramento em {@link #encerrar} garante isso.</p>
     */
    public record ContextoSpan(Span span, Scope scope) {}
}
```

---

### 11.6. `RastreamentoInterceptor` — CDI Interceptor de Tracing

Intercepta métodos anotados com `@Rastreado` e delega o ciclo de vida do span
ao `GerenciadorRastreamento`. Executa antes do `LogInterceptor` (`@Priority`
menor = executa primeiro na cadeia CDI), garantindo que o `spanId` do filho
esteja no MDC quando o `LogInterceptor` registrar a localização.

```java
package br.com.seudominio.log.interceptor;

import br.com.seudominio.log.annotations.Rastreado;
import br.com.seudominio.log.tracing.GerenciadorRastreamento;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

/**
 * CDI Interceptor ativado por {@link Rastreado}.
 *
 * <p>Para cada método interceptado:</p>
 * <ol>
 *   <li>Cria um {@code Child Span} OTel vinculado ao span pai da requisição</li>
 *   <li>Registra classe, método e serviço como atributos do span</li>
 *   <li>Sincroniza o {@code spanId} do filho no MDC</li>
 *   <li>Executa o método de negócio</li>
 *   <li>Em caso de exceção: marca o span como {@code ERROR} com stack trace</li>
 *   <li>Encerra o span (registra hora de término) e restaura o span pai</li>
 * </ol>
 *
 * <p><b>Ordem de prioridade na cadeia de interceptors:</b></p>
 * <ul>
 *   <li>{@code RastreamentoInterceptor} — {@code Priority = APPLICATION - 10}
 *       → executa primeiro: cria o span filho e atualiza o MDC</li>
 *   <li>{@code LogInterceptor} — {@code Priority = APPLICATION}
 *       → executa depois: lê o {@code spanId} já atualizado no MDC</li>
 * </ul>
 *
 * <p><b>Falha de infraestrutura OTel:</b> exceções do {@code Tracer} são
 * capturadas e logadas localmente — nunca relançadas. Uma falha no backend
 * de rastreamento não deve interromper o fluxo de negócio.</p>
 */
@Rastreado
@Interceptor
@Priority(Interceptor.Priority.APPLICATION - 10)
public class RastreamentoInterceptor {

    private static final org.jboss.logging.Logger log =
            org.jboss.logging.Logger.getLogger(RastreamentoInterceptor.class);

    @Inject
    GerenciadorRastreamento gerenciador;

    @AroundInvoke
    public Object rastrear(InvocationContext contexto) throws Exception {
        var metodo     = contexto.getMethod();
        var classe     = metodo.getDeclaringClass().getSimpleName();
        var nomeMetodo = metodo.getName();

        GerenciadorRastreamento.ContextoSpan contextoSpan = null;

        try {
            contextoSpan = gerenciador.iniciar(classe, nomeMetodo);
            return contexto.proceed();

        } catch (Exception e) {
            // Marca o span como erro com stack trace — visível no Jaeger
            if (contextoSpan != null) {
                gerenciador.registrarErro(contextoSpan.span(), e);
            }
            throw e;

        } finally {
            if (contextoSpan != null) {
                try {
                    gerenciador.encerrar(contextoSpan);
                } catch (Exception otelEx) {
                    // Falha de infraestrutura OTel não interrompe o fluxo de negócio
                    log.warnf("Falha ao encerrar span OTel — classe=%s, metodo=%s: %s",
                            classe, nomeMetodo, otelEx.getMessage());
                }
            }
        }
    }
}
```

---

### 11.7. Atualização do `LogContextoFiltro`

O filtro existente não requer alterações funcionais. A única atualização
necessária é a remoção do bloco de documentação sobre `requestId`, que foi
**excluído do padrão**. O filtro já popula `traceId` e `spanId` corretamente
via `GerenciadorContextoLog`.

```java
// Comportamento inalterado — documentação interna simplificada:
//
// Fase REQUEST:  GerenciadorContextoLog.inicializar(userId)
//                → captura traceId + spanId do span OTel ativo
//                → popula MDC com traceId, spanId, userId, servico
//
// Fase RESPONSE: GerenciadorContextoLog.limpar()
//                → MDC limpo — sem vazamento entre requisições
```

---

### 11.8. Exemplos de Uso

**Caso 1 — Apenas `@Rastreado`: serviço de integração externa**

```java
@ApplicationScoped
@Rastreado  // Cria Child Span para cada método — sem métricas Micrometer
public class IntegracaoFiscalClient {

    public NotaFiscal emitir(Pedido pedido) {
        // O RastreamentoInterceptor criou um span "IntegracaoFiscalClient.emitir"
        // vinculado ao traceId da requisição HTTP original.
        // Qualquer exceção aqui é automaticamente marcada no span como ERROR.
        return chamarApiExterna(pedido);
    }
}
```

**Caso 2 — `@Logged` + `@Rastreado`: serviço de negócio crítico**

```java
@ApplicationScoped
@Logged     // LogInterceptor:          MDC com classe/método + métricas Micrometer
@Rastreado  // RastreamentoInterceptor: Child Span OTel + spanId atualizado no MDC
public class PagamentoService {

    public Pagamento processar(OrdemPagamento ordem) {
        // Span "PagamentoService.processar" criado e visível no Jaeger.
        // traceId e spanId (do filho) estão no MDC para todos os logs abaixo.

        LogSistematico
            .registrando("Pagamento iniciado")
            .em(PagamentoService.class, "processar")
            .comDetalhe("ordemId", ordem.getId())
            .comDetalhe("valor",   ordem.getValor())
            .info();
        // JSON de saída inclui: traceId, spanId (do Child Span), userId, servico

        return gateway.processar(ordem);
    }
}
```

**Caso 3 — Span com atributo de negócio customizado**

```java
@ApplicationScoped
public class EstoqueService {

    @Inject GerenciadorRastreamento rastreamento;

    @Rastreado
    public void reservar(String skuId, int quantidade) {
        // Enriquece o span com atributo de domínio — visível no painel do Jaeger
        var spanAtivo = io.opentelemetry.api.trace.Span.current();
        rastreamento.adicionarAtributo(spanAtivo, "estoque.sku",        skuId);
        rastreamento.adicionarAtributo(spanAtivo, "estoque.quantidade", String.valueOf(quantidade));

        // ... lógica de reserva
    }
}
```

**Saída JSON de um log emitido dentro de um método `@Rastreado`:**

```json
{
  "timestamp":        "2026-03-24T14:32:00.847Z",
  "level":            "INFO",
  "message":          "Pagamento iniciado",
  "traceId":          "4bf92f3577b34da6a3ce929d0e0e4736",
  "spanId":           "f9d3a1b2c4e56789",
  "userId":           "joao.silva@empresa.com",
  "servico":          "pagamentos-service",
  "log_classe":       "PagamentoService",
  "log_metodo":       "processar",
  "detalhe_ordemId":  "8821",
  "detalhe_valor":    "1249.90"
}
```

O `spanId` acima é o do **Child Span** criado pelo `RastreamentoInterceptor` —
não o do Root Span da requisição HTTP. Isso permite localizar exatamente
qual operação de negócio gerou cada linha de log no grafo do Jaeger.

---

### 11.9. Diagrama de Fluxo — Requisição com `@Logged` + `@Rastreado`

```
Requisição HTTP recebida
        │
        ▼
LogContextoFiltro.filter(request)
  └─ GerenciadorContextoLog.inicializar(userId)
       └─ MDC: { traceId, spanId(root), userId, servico }
        │
        ▼
RastreamentoInterceptor.rastrear()          [Priority = APPLICATION - 10]
  └─ GerenciadorRastreamento.iniciar()
       ├─ Cria Child Span vinculado ao Root Span
       └─ MDC: { spanId atualizado para o filho }
        │
        ▼
LogInterceptor.interceptar()                [Priority = APPLICATION]
  └─ GerenciadorContextoLog.registrarLocalizacao(classe, metodo)
       └─ MDC: { classe, metodo }
        │
        ▼
Método de negócio executa
  └─ LogSistematico.registrando(...).info()
       └─ JSON: { traceId, spanId(filho), userId, servico, classe, metodo, ... }
        │
        ▼
LogInterceptor.finally
  └─ MDC.remove(classe, metodo)
        │
        ▼
RastreamentoInterceptor.finally
  └─ GerenciadorRastreamento.encerrar()
       ├─ Scope.close()  → restaura Root Span como corrente
       └─ Span.end()     → registra hora de término, exporta via OTLP
        │
        ▼
LogContextoFiltro.filter(response)
  └─ GerenciadorContextoLog.limpar()
       └─ MDC: {} (limpo)
```


# Padrão de Logging em Aplicações Java

**Resumo:** O registro de logs em aplicações de software frequentemente carece de sistematização, sendo tratado de forma artesanal e subjetiva pelos desenvolvedores. Este documento sintetiza uma revisão bibliográfica e mercadológica sobre heurísticas de logging, propondo diretrizes fundamentadas em estudos empíricos e literatura especializada para padronizar o que, como e quando registrar. O resultado é um padrão arquitetural que combina o framework 5W1H, *Fluent Interface*, *Domain Specific Language* e logging estruturado em JSON — tratando o log não como mecanismo de debug, mas como componente estrutural da engenharia de software e base para observabilidade.

> **Documentos relacionados**
> - [Implementação SLF4J + Log4j2](implementacao_slf4j.md) — código-fonte da biblioteca portável (Jakarta EE)
> - [Implementação Quarkus 3.27](biblioteca_quarkus.md) — código-fonte da biblioteca nativa Quarkus

---

## 1. Introdução

Historicamente, a prática de logging assemelha-se aos estágios iniciais dos testes automatizados: é altamente baseada na opinião individual do desenvolvedor. Equipes frequentemente divergem sobre quais linhas merecem registro e qual o nível de severidade adequado — `INFO`, `DEBUG` ou `ERROR` —, resultando em logs inconsistentes e de difícil utilização para *troubleshooting*.

O objetivo desta revisão é afastar o logging do empirismo e fundamentá-lo em dados acadêmicos e práticas validadas pela indústria. Mais do que isso: elevar o log à categoria de **componente arquitetural**, com o mesmo rigor exigido de segurança, persistência e contratos de API. Aplicações sem logging sistemático apresentam baixa rastreabilidade, alto MTTR (*Mean Time to Recovery*) e risco de falhas não diagnosticáveis em produção.

---

## 2. Fundamentação Teórica

A formulação de uma heurística de logging robusta exige a triangulação de fontes de mercado e pesquisas acadêmicas. A fundamentação deste padrão baseia-se em três pilares:

- **O Princípio dos 5Ws:** Emprestado do jornalismo e da análise de causa raiz (*Root Cause Analysis*), estrutura cada evento de log em dimensões investigativas.
- **Estudos Empíricos (Microsoft, 2010):** Análise quantitativa e qualitativa de bases de código massivas — de 2,5 a 10,4 milhões de linhas — para identificar padrões reais de onde e como desenvolvedores inserem logs.
- **Literatura Especializada:** Trabalhos de Anton Chuvakin, autoridade em segurança e gerenciamento de logs, referentes à categorização de eventos operacionais críticos.

---

## 3. Taxonomia: O Que Registrar?

A ausência de um algoritmo determinístico para o logging exige o estabelecimento de heurísticas. A revisão identifica duas dimensões complementares de análise.

### 3.1. Categorias de Inserção de Log

O estudo da Microsoft identificou cinco cenários primários onde desenvolvedores inserem logs no código:

1. **Asserções (*Assertion Logging*):** Verificações de pré-condições, pós-condições e invariantes — ligadas ao conceito de *Design by Contract* e *Self-Testing Code*.
2. **Verificação de Retorno (*Return Value Checking*):** Validação de retornos de funções e integrações externas.
3. **Exceções (*Exception Logging*):** O registro de falhas sistêmicas com contexto suficiente para diagnóstico.
4. **Pontos de Execução (*Execution Point Logging*):** Logs observacionais para confirmar que o fluxo entrou em um bloco condicional específico.
5. **Rastreamento (*Trace Logging*):** Acompanhamento do caminho de execução ao longo de múltiplos componentes.

### 3.2. Eventos Críticos

Baseando-se em Anton Chuvakin, as seguintes classes de eventos devem **obrigatoriamente** gerar registros, independente da heurística do desenvolvedor:

- **Autenticação, Autorização e Acesso:** Sucessos, falhas e acessos remotos a componentes sensíveis.
- **Mudanças de Estado:** Persistência (create/update/delete), instalações e remoção de dados. Mudanças são os maiores vetores de falhas críticas.
- **Chamadas Externas:** Toda comunicação com APIs de terceiros, incluindo o *payload* enviado e a resposta recebida.
- **Disponibilidade:** Problemas de inicialização ou inatividade de componentes e dependências.
- **Exaustão de Recursos:** Falta de conexões no *pool* de banco de dados, estouros de memória ou indisponibilidade de rede.
- **Entradas Inválidas e Situações Inesperadas:** Recebimento de *payloads* maliciosos ou acesso a rotas não autorizadas.

---

## 4. O Framework 5W1H

Todo evento de log deve, obrigatoriamente, responder às seis dimensões do framework 5W1H para fornecer contexto investigativo completo:

| Dimensão | Pergunta | Exemplos práticos |
|---|---|---|
| **Who** (Quem) | Quem é o ator da ação? | `userId`, IP de origem, `visitorToken` |
| **What** (O quê) | Qual é o evento? | "Pedido criado", "Login falhou" |
| **When** (Quando) | Quando ocorreu? | Timestamp ISO 8601 em UTC com milissegundos |
| **Where** (Onde) | Onde no sistema? | Serviço, classe, método, endpoint, `requestId` |
| **Why** (Por quê) | Qual o motivo de negócio? | "Saldo insuficiente", "Sessão expirada" |
| **How** (Como) | Por qual canal chegou? | "API REST", "Fila assíncrona", "Job agendado" |

As dimensões *When* (timestamp) e parte do *Where* (classe e método) são automáticas e preenchidas pela infraestrutura de logging. As demais exigem declaração explícita pelo desenvolvedor — e é exatamente aí que a DSL atua, guiando esse preenchimento.

**Observação:** Classe e método são metadados técnicos úteis, mas não substituem rastreabilidade funcional. Um log com apenas `PedidoService.criar` sem o `pedidoId`, o usuário e o motivo não permite diagnóstico eficiente.

---

## 5. Princípios de Design da API

A biblioteca de logging é construída sobre quatro princípios de design que se reforçam mutuamente.

### 5.1. Domain Specific Language (DSL)

Uma DSL de domínio é uma linguagem de programação projetada para um problema específico. No contexto de logging, o objetivo é tornar a chamada ao logger tão expressiva quanto uma descrição em linguagem natural do evento ocorrido.

Em vez de:

```java
// PROIBIDO — concatenação de string sem estrutura nem guia
log.info("Order " + orderId + " saved by user " + userId);
```

A DSL produz:

```java
// CORRETO — legível como uma frase, estruturado, validado pelo compilador
LogSistematico
    .registrando("Pedido salvo")
    .em(PedidoService.class, "criar")
    .porque("Solicitação do cliente via checkout")
    .como("API REST — POST /pedidos")
    .comDetalhe("orderId", orderId)
    .comDetalhe("userId",  userId)
    .info(log);
```

Os nomes dos métodos são deliberadamente em português e no vocabulário do domínio operacional — `registrando`, `porque`, `como`, `comDetalhe` — em vez de termos técnicos genéricos como `set`, `add` ou `with`. Isso reduz a fricção cognitiva e torna o log legível como uma sentença.

### 5.2. Fluent Interface

A *Fluent Interface* é o mecanismo técnico que viabiliza a DSL. Cada método retorna a próxima interface na cadeia, criando um fluxo de chamadas encadeado e auto-documentado. A sequência de preenchimento é validada em tempo de compilação: não é possível chamar `.info()` sem ter passado antes por `.registrando()` e `.em()`.

```
LogSistematico
    .registrando(evento)      ← Ponto de entrada — retorna EtapaOnde
    .em(classe, metodo)       ← Obrigatório     — retorna EtapaOpcional
    [ .porque(motivo)    ]    ← Opcional
    [ .como(canal)       ]    ← Opcional
    [ .comDetalhe(k, v)  ]*   ← Zero ou mais
    .info(log) | .debug(log) | .warn(log) | .erro(log, ex)
```

Essa progressão guiada elimina a principal causa de logs incompletos: o desenvolvedor simplesmente não sabe o que deveria preencher. A Fluent Interface torna o preenchimento correto o caminho de menor resistência.

### 5.3. Contexto Automático via MDC

O MDC (*Mapped Diagnostic Context*) usa `ThreadLocal` para propagar dados ao longo de toda a cadeia de chamadas de uma requisição, sem que cada método precise receber o usuário como parâmetro. Um CDI Interceptor popula o MDC no início de cada invocação e garante sua limpeza no bloco `finally`, evitando vazamento entre *threads*.

Esse mecanismo resolve a dimensão *Who* do 5W1H automaticamente: o `userId`, o `traceId` e o `spanId` estão presentes em todos os logs de uma requisição sem que o desenvolvedor precise declará-los manualmente.

### 5.4. Imutabilidade dos Objetos de Valor

Todos os objetos de valor da biblioteca — `LogEvento`, `AuditRecord`, `ObservabilityContext` — são *Records* Java 21 imutáveis. Imutabilidade garante *thread-safety* estrutural sem sincronização e elimina erros de estado compartilhado em ambientes concorrentes. Nenhum estado mutável deve ser adicionado a esses objetos.

```java
// Definido pela biblioteca — não adicionar estado mutável
public record AuditRecord(
    String  actorId,
    String  action,
    String  entityType,
    String  entityId,
    Object  stateBefore,
    Object  stateAfter,
    Instant timestamp,
    String  traceId
) {}
```

---

## 6. Logs Estruturados em JSON

O formato JSON é mandatório. Logs em texto puro são legíveis para humanos no terminal, mas são um obstáculo para máquinas: qualquer extração de informação exige expressões regulares complexas e frágeis. Com JSON, cada dimensão do 5W1H torna-se um campo pesquisável, tipado e indexável.

**Comparação:**

```
# Texto puro — não estruturado, difícil de consultar
[ORDER-123] [USER-45] Payment failed at 2026-03-11

# JSON — cada campo é uma chave pesquisável de primeiro nível
{
  "timestamp":  "2026-03-11T21:55:00.123Z",
  "level":      "ERROR",
  "message":    "Falha ao processar pagamento",
  "userId":     "joao.silva",
  "traceId":    "4bf92f3577b34da6a3ce929d0e0e4736",
  "spanId":     "a3ce929d0e0e4736",
  "log_classe": "PagamentoService",
  "log_metodo": "processar",
  "log_motivo": "Gateway recusou a transação",
  "detalhe_pedidoId":          "4821",
  "detalhe_codigoErroGateway": "INSUFFICIENT_FUNDS"
}
```

Com logs em JSON, o sistema de observabilidade deixa de ser uma ferramenta de busca de texto e passa a ser um banco de dados consultável:

```
# Query real em Elasticsearch / Kibana
level: ERROR AND log_motivo: "Gateway*" AND @timestamp:[now-1h TO now]
```

**Regras obrigatórias:**

- É proibido montar pseudo-JSON via `String.format` ou concatenação.
- É proibido usar `System.out.println` ou `System.err.println`.
- Nomes de campos devem ser consistentes em toda a aplicação. Consulte o [Registro de Nomes de Campos](FIELD_NAMES.md).
- Dados sensíveis não devem ser registrados — mascaramento automático via `SensitiveDataSanitizer`.
- Serialização JSON deve usar `ObjectMapper` pré-compilado com módulos registrados — não reflexão por evento.

---

## 7. Padrões Proibidos

Os padrões abaixo são estritamente proibidos. *Code Review* deve rejeitar qualquer *pull request* que os introduza.

### 7.1. `System.out` e Concatenação de Strings

```java
// PROIBIDO
System.out.println("Order saved: " + orderId);
System.err.println("Error: " + e.getMessage());
log.info("Order " + orderId + " saved by user " + userId);
log.info(String.format("{\"order_id\":\"%s\"}", orderId));

// CORRETO — campos estruturados via key-value nativo (SLF4J 2.x)
logger.atInfo()
    .addKeyValue("order_id", orderId)
    .addKeyValue("user_id",  userId)
    .log("Order saved");
```

### 7.2. Registro Apenas da Mensagem da Exceção

```java
// PROIBIDO — descarta classe, stack trace e cadeia de causas
log.error(e.getMessage());
log.error("Error: " + e.getMessage());

// CORRETO — objeto de exceção completo como último argumento
log.error("Falha ao processar Order#{}", orderId, e);
```

### 7.3. Mensagens Genéricas sem Identificadores de Entidade

```java
// PROIBIDO — sem valor diagnóstico
log.error("Error saving");
log.warn("Validation failed");
log.info("Started");

// CORRETO — inclui contexto específico e identificadores
log.error("Falha ao salvar Order#{}: {}", orderId, e.getMessage(), e);
log.warn("Validação falhou para Order#{}: campo '{}' obrigatório", orderId, fieldName);
log.info("Processamento iniciado: Order#{} por User#{}", orderId, userId);
```

### 7.4. Log-and-Throw (Duplicação de Exceção)

A mesma exceção registrada em múltiplas camadas sem contexto adicional gera duplicação de ruído que prejudica o diagnóstico.

```java
// PROIBIDO — será logada novamente pela camada superior
catch (OrderException e) {
    log.error("Order processing failed", e);
    throw e;
}

// CORRETO — loga na fronteira onde a exceção é tratada, com contexto completo
catch (OrderException e) {
    log.error("Order processing failed: Order#{}", orderId, e);
    return Response.serverError().entity(ErrorResponse.from(e)).build();
}
```

### 7.5. Registro de Dados Sensíveis

```java
// PROIBIDO
log.info("Payment initiated for card: {}", creditCardNumber);
log.debug("User authenticated: password={}", password);
log.info("User data: {}", userObject); // se userObject contiver PAN, CPF, etc.

// CORRETO — apenas identificadores parciais ou mascarados
log.info("Payment initiated for card ending in: {}", last4Digits);
log.info("User #{} authenticated successfully", userId);
```

### 7.6. TraceId Gerado Manualmente

```java
// PROIBIDO — identificador falso, não correlacionável com tracing distribuído
String traceId = UUID.randomUUID().toString();
MDC.put("traceId", traceId);

// CORRETO — traceId real extraído do span OpenTelemetry ativo
String traceId = TraceContextExtractor.currentTraceId();
if (traceId != null) {
    MDC.put("trace_id", traceId);
}
```

### 7.7. MDC sem Limpeza no `finally`

```java
// PROIBIDO — contexto vaza para requisições subsequentes na mesma thread
MDC.put("user_id", userId);
processRequest();

// CORRETO — limpeza garantida independente de exceção
MDC.put("user_id", userId);
try {
    processRequest();
} finally {
    MDC.clear();
}
```

### 7.8. Guarda de Nível Ausente para Computações Custosas

```java
// PROIBIDO — serializa order mesmo com DEBUG desabilitado em produção
log.debug("Order state: {}", objectMapper.writeValueAsString(order));

// CORRETO — custo de serialização pago apenas se o nível estiver habilitado
if (log.isDebugEnabled()) {
    log.debug("Order state: {}", objectMapper.writeValueAsString(order));
}
```

---

## 8. Padrões Obrigatórios

### 8.1. Logging Estruturado via Key-Value (SLF4J 2.x)

```java
logger.atInfo()
    .addKeyValue("action",   "processarPedido")
    .addKeyValue("order_id", orderId)
    .addKeyValue("user_id",  userId)
    .log("Order processing started");
```

Ou via MDC para contexto que se aplica a todas as linhas de log em um escopo:

```java
MDC.put("order_id", orderId.toString());
log.info("Processing started");
// ... múltiplos logs carregam order_id automaticamente
MDC.remove("order_id");
```

### 8.2. Tratamento de Exceções com Contexto Completo

```java
try {
    orderService.process(order);
} catch (Exception e) {
    log.error("Order processing failed: Order#{}", order.getId(), e);
    exceptionReporter.report(e, Map.of("order_id", order.getId()));
    throw new ServiceException("Order processing failed", e);
}
```

### 8.3. Eventos de Negócio

Eventos relevantes para o negócio devem ser registrados com o método dedicado — não com `log.info()` genérico — para serem identificáveis como categoria distinta nos sistemas de observabilidade:

```java
structuredLogger.businessEvent("ORDER_COMPLETED",
    Map.of(
        "order_id",    order.getId(),
        "order_value", order.getTotal(),
        "currency",    "BRL",
        "items_count", order.getItems().size()
    ));
```

---

## 9. Gestão de Níveis de Severidade

A escolha do nível de log deve ser determinística, não subjetiva. A regra é baseada no impacto sobre o estado do sistema:

| Nível | Quando usar | Habilitado em produção? |
|---|---|---|
| `INFO` | Operações que alteram estado: persistência, autenticação, chamadas externas, mudanças relevantes | Sempre |
| `WARN` | Situações anômalas recuperáveis: tentativas de acesso indevido, *fallbacks* ativados, validações rejeitadas | Sempre |
| `ERROR` | Falhas reais: exceção que impede o cumprimento do contrato da operação | Sempre |
| `DEBUG` | Fluxos internos, decisões condicionais, dados intermediários sem alteração de estado | Não por padrão — ativável dinamicamente por pacote |

**Regra anti-duplicação:** é proibido registrar a mesma exceção em múltiplas camadas sem agregar contexto adicional. Cada camada loga apenas o que sabe a mais sobre o erro.

**Regra de exceção completa:** é proibido registrar apenas `e.getMessage()`. O objeto de exceção completo deve sempre ser passado ao logger, preservando classe, *stack trace* e cadeia de causas.

---

## 10. Segurança e Governança

O logging deve seguir o princípio de *data minimization*: registrar apenas o que é necessário para diagnóstico e auditoria, nunca mais.

**São proibidos nos logs:**

- Senhas e hashes de senha
- Tokens de autenticação e autorização (JWT, API keys)
- Números de cartão de crédito (PAN) e CVV
- CPF, RG e dados pessoais sensíveis conforme LGPD
- Dados que identifiquem menores de idade

Quando um campo de negócio contém dado sensível, a sanitização ocorre automaticamente via `SensitiveDataSanitizer`, que intercepta os valores pelos nomes das chaves — sem depender da disciplina do desenvolvedor no momento da chamada.

---

## 11. Falhas Silenciosas na Infraestrutura de Observabilidade

Falhas na infraestrutura de observabilidade **nunca devem propagar como exceções de negócio**. Se o backend de rastreamento, o exportador OpenTelemetry ou o pipeline de métricas estiver indisponível, o sistema registra a falha localmente e continua operando.

```java
// CORRETO — falha de observabilidade não interrompe o fluxo de negócio
try {
    trackingBackend.report(exceptionRecord);
} catch (Exception backendException) {
    fallbackLogger.warn("Exception tracking backend unavailable: {}",
            backendException.getMessage());
    // NÃO relançar — lógica de negócio não falha por causa de observabilidade
}
```

Essa regra se aplica a: backends de rastreamento de exceções, exportadores OpenTelemetry, pipelines de métricas Micrometer e qualquer integração de observabilidade externa. O código de negócio não deve conhecer nem depender da disponibilidade dessas integrações.

---

## 12. Observabilidade e Operação

### 12.1. Os Três Pilares da Observabilidade

| Pilar | Tecnologia | O que responde |
|---|---|---|
| **Logs** | JSON estruturado + ELK / Datadog / Loki | "O que aconteceu e em qual contexto?" |
| **Métricas** | Micrometer + Prometheus + Grafana | "Com que frequência e volume?" |
| **Tracing** | OpenTelemetry + Jaeger / Zipkin | "Qual o caminho completo da requisição?" |

O `traceId` e o `spanId` gerados pelo OpenTelemetry são a chave que une os três pilares. O `traceId` identifica toda a operação distribuída de ponta a ponta — o mesmo valor em todos os serviços envolvidos. O `spanId` identifica uma etapa específica dentro desse trace: cada serviço, cada chamada de banco, cada operação relevante gera seu próprio `spanId`. Filtrar por `traceId` reconstrói a história completa; filtrar por `spanId` isola exatamente o componente que falhou.

Por isso, **ambos os identificadores nunca devem ser gerados manualmente** — devem sempre ser extraídos do contexto OTel ativo.

### 12.2. Ambientes Reativos e Virtual Threads

Em aplicações com RESTEasy Reactive, Mutiny ou Vert.x, o MDC isolado não é suficiente: a execução pode trocar de *thread* entre operações assíncronas e o `ThreadLocal` é silenciosamente perdido. Nesses ambientes é obrigatório o uso de **SmallRye Context Propagation** (Quarkus) ou equivalente.

Para cenários de alta concorrência, *virtual threads* (Project Loom, Java 21) oferecem propagação de contexto não-bloqueante sem a complexidade de pipelines reativos explícitos — e são a abordagem preferida em novas implementações.

### 12.3. Alteração Dinâmica de Nível

Aplicações em produção devem permitir a ativação do nível `DEBUG` por pacote específico em tempo de execução, sem reinicialização, para investigar incidentes ativos sem elevar o volume global de logs.

### 12.4. Logs como Base de Alertas e Analytics

- **Analytics em tempo real:** eventos como `ORDER_COMPLETED` alimentam dashboards sem onerar o banco de dados principal.
- **Alertas automáticos:** pico de `LOGIN_FAILED` do mesmo IP em curto intervalo dispara alerta de força bruta; queda de 80% em `ORDER_CREATED` sinaliza falha silenciosa no frontend.
- **Auditoria e prova técnica:** *payload* e resposta de APIs externas criam evidências imutáveis para encerrar disputas técnicas.
- **Otimização de performance:** duração de operações registrada com Micrometer permite identificar gargalos com dados reais de produção.

---

## 13. Volume: Excesso vs. Omissão

Em casos de dúvida sobre a relevância de uma informação, a literatura favorece o excesso. Deixar de registrar uma informação crítica é significativamente mais prejudicial do que registrar dados supérfluos. Um log desnecessário pode ser filtrado; um log ausente no momento de um incidente crítico é irrecuperável.

---

## 14. Performance

A infraestrutura de logging não deve introduzir latência observável no caminho crítico:

- Injeção de contexto deve ser O(1) — sem consultas a banco de dados ou chamadas de rede síncronas no caminho do MDC.
- Serialização JSON deve usar `ObjectMapper` pré-compilado com módulos registrados (Jackson) — não reflexão por evento.
- Operações MDC são inserções em mapa `ThreadLocal` — não criar objetos complexos nesse caminho.
- Computações custosas protegidas por guarda de nível (ver seção 7.8).

---

## 15. Implementação da Biblioteca

O código-fonte está documentado em arquivos separados, organizados por plataforma:

| Implementação | Arquivo | Quando usar |
|---|---|---|
| **SLF4J + Log4j2 + CDI** | [implementacao_slf4j.md](implementacao_slf4j.md) | Wildfly, TomEE, Payara, OpenLiberty — qualquer container Jakarta EE |
| **Quarkus 3.27 nativo** | [biblioteca_quarkus.md](biblioteca_quarkus.md) | Aplicações Quarkus — JVM ou native image GraalVM |

Ambas as implementações expõem a mesma API pública (`LogSistematico`, `@Logged`) e produzem o mesmo JSON estruturado. O que difere é a infraestrutura interna: logger, MDC, configuração JSON, ativação do interceptor e propagação de contexto reativo.

---

## 16. Exemplos de Uso

### Caso 1 — Persistência (evento obrigatório)

```java
@ApplicationScoped
@Logged  // Injeta userId, traceId, spanId e métricas automaticamente
public class PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);

    public Pedido criar(NovoPedidoRequest request) {
        Pedido pedido = new Pedido(request);
        repository.salvar(pedido);

        LogSistematico
            .registrando("Pedido criado")
            .em(PedidoService.class, "criar")
            .porque("Solicitação do cliente via checkout")
            .como("API REST — POST /pedidos")
            .comDetalhe("pedidoId",   pedido.getId())
            .comDetalhe("valorTotal", pedido.getValorTotal())
            .info(log);

        return pedido;
    }
}
```

**JSON gerado:**

```json
{
  "timestamp":          "2026-03-11T21:55:00.123Z",
  "level":              "INFO",
  "message":            "Pedido criado",
  "userId":             "joao.silva@empresa.com",
  "traceId":            "4bf92f3577b34da6a3ce929d0e0e4736",
  "spanId":             "a3ce929d0e0e4736",
  "log_classe":         "PedidoService",
  "log_metodo":         "criar",
  "log_motivo":         "Solicitação do cliente via checkout",
  "log_canal":          "API REST — POST /pedidos",
  "detalhe_pedidoId":   "4821",
  "detalhe_valorTotal": "349.90"
}
```

---

### Caso 2 — Validação sem alteração de estado (DEBUG)

```java
public List<Pedido> buscarPorCliente(Long clienteId) {
    if (clienteId == null) {
        LogSistematico
            .registrando("Busca por cliente ignorada")
            .em(PedidoService.class, "buscarPorCliente")
            .porque("clienteId ausente na requisição")
            .debug(log);

        return Collections.emptyList();
    }
    return repository.findByCliente(clienteId);
}
```

---

### Caso 3 — Erro com exceção e contexto de gateway

```java
public void processar(Long pedidoId) {
    try {
        gateway.cobrar(pedidoId);
    } catch (GatewayException e) {
        LogSistematico
            .registrando("Falha ao processar pagamento")
            .em(PedidoService.class, "processar")
            .porque("Gateway recusou a transação")
            .comDetalhe("pedidoId",          pedidoId)
            .comDetalhe("codigoErroGateway", e.getCodigo())
            .erro(log, e);

        throw new PagamentoException("Pagamento não processado", e);
    }
}
```

---

### Caso 4 — Dado sensível mascarado automaticamente

```java
// "password" → "****"  |  "email" → "[PROTEGIDO]"  |  "ipOrigem" → valor original
LogSistematico
    .registrando("Tentativa de autenticação")
    .em(AutenticacaoService.class, "autenticar")
    .comDetalhe("email",    request.email())    // ← "[PROTEGIDO]"
    .comDetalhe("password", request.senha())    // ← "****"
    .comDetalhe("ipOrigem", request.ip())
    .warn(log);
```

---

### Caso 5 — Evento de negócio

```java
structuredLogger.businessEvent("ORDER_COMPLETED",
    Map.of(
        "order_id",    pedido.getId(),
        "order_value", pedido.getValorTotal(),
        "currency",    "BRL",
        "items_count", pedido.getItens().size()
    ));
```

---

## 17. Não Conformidades

São consideradas não conformidades graves, a serem bloqueadas em *Code Review*:

| Não conformidade | Impacto |
|---|---|
| `System.out.println` ou `e.printStackTrace()` | Sem estrutura, sem nível, sem MDC |
| Concatenação de strings ou pseudo-JSON | Campo não indexável, frágil a caracteres especiais |
| `log.error(e.getMessage())` sem objeto completo | Descarta stack trace e cadeia de causas |
| Mensagens genéricas sem identificadores de entidade | Inúteis para diagnóstico em produção |
| Log-and-throw sem contexto adicional | Duplicação de erro sem valor analítico |
| Dados sensíveis sem mascaramento | Violação de LGPD e políticas de segurança |
| `traceId` gerado como `UUID.randomUUID()` | Impossibilita correlação com tracing distribuído |
| MDC sem limpeza no `finally` | Vazamento de contexto entre threads em produção |
| Computação custosa sem guarda de nível | Overhead de serialização mesmo com nível desabilitado |
| Eventos de negócio via `log.info()` genérico | Não identificáveis como categoria em observabilidade |
| Falha de observabilidade relançada como exceção de negócio | Interrompe o fluxo de negócio por falha de infraestrutura |

---

## 18. Checklist de Code Review

Antes de aprovar qualquer *pull request* que toque em código de observabilidade:

- [ ] Nenhum `System.out.println` ou `System.err.println`
- [ ] Nenhuma concatenação de string ou `String.format` em mensagens de log
- [ ] Nenhum `log.error(e.getMessage())` — objeto de exceção completo passado
- [ ] Nenhuma mensagem genérica — identificadores de entidade presentes
- [ ] Nenhum log-and-throw sem contexto adicional
- [ ] Nenhum dado sensível (senhas, tokens, PAN, CPF) nos campos de log
- [ ] Nenhum `UUID.randomUUID()` como `traceId` — contexto OpenTelemetry usado
- [ ] MDC limpo no bloco `finally`
- [ ] Computações custosas protegidas por guarda de nível
- [ ] Nomes de campos canônicos do [Registro de Nomes de Campos](FIELD_NAMES.md) usados
- [ ] Eventos de negócio usam `structuredLogger.businessEvent()` — não `log.info()` genérico
- [ ] Falhas de backend de observabilidade tratadas localmente — não relançadas

---

## 19. Ciclo de Melhoria Contínua

Logging é um componente vivo da arquitetura. Após cada incidente em produção:

1. Revisar os logs gerados durante o incidente.
2. Identificar quais informações estavam ausentes e atrasaram o diagnóstico.
3. Atualizar a biblioteca ou o padrão para que a lacuna seja preenchida automaticamente no futuro.
4. Incorporar a melhoria como novo padrão organizacional e atualizar o checklist de *Code Review*.

---

## 20. Conclusão

O logging sistemático deixa de ser uma prática subjetiva e passa a ser um **componente arquitetural com contratos claros**: o framework 5W1H define o que deve estar presente em cada evento; a DSL e a Fluent Interface tornam o preenchimento correto o caminho natural; o CDI Interceptor elimina o trabalho repetitivo de propagar contexto; e o JSON estruturado transforma cada log em um dado pesquisável.

O resultado é uma camada de observabilidade que serve simultaneamente ao desenvolvedor depurando um erro às 3 da manhã, ao time de operações monitorando alertas em produção, ao time de negócio acompanhando métricas de conversão e ao time jurídico auditando acessos para conformidade com a LGPD.

Logs sistemáticos não são overhead — são a memória do sistema.

---

## Ver Também

- [Implementação SLF4J + Log4j2](implementacao_slf4j.md) — código-fonte completo da biblioteca portável
- [Implementação Quarkus 3.27](biblioteca_quarkus.md) — código-fonte completo da biblioteca nativa Quarkus
- [Registro de Nomes de Campos](FIELD_NAMES.md) — nomes canônicos dos campos JSON

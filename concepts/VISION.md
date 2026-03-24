# Vision Document — Logging Sistemático em Aplicações Java

> Versão 0.2 · DRAFT · Março 2026

---

## Sumário

1. [Introdução](#1-introdução)
2. [Contexto do Projeto](#2-contexto-do-projeto)
   - [O Problema](#21-o-problema)
   - [Público-Alvo](#22-público-alvo)
3. [Objetivos e Benefícios Esperados](#3-objetivos-e-benefícios-esperados)
   - [Objetivos Principais](#31-objetivos-principais)
   - [Benefícios Esperados](#32-benefícios-esperados)
   - [Diferenciação](#33-diferenciação-das-soluções-existentes)
4. [Escopo](#4-escopo)
   - [O Que a Biblioteca Entrega](#41-o-que-a-biblioteca-entrega)
   - [Fora de Escopo](#42-fora-de-escopo)
5. [Regras e Princípios](#5-regras-e-princípios)
   - [Padrões de Código](#51-padrões-de-código)
   - [Convenções de Nomenclatura](#52-convenções-de-nomenclatura)
   - [Boas Práticas](#53-boas-práticas)
6. [Conceitos Fundamentais](#6-conceitos-fundamentais)
   - [Glossário](#61-glossário)
   - [Principais Abstrações](#62-principais-abstrações)
7. [Exemplos de Uso](#7-exemplos-de-uso)
8. [Visão de Futuro](#8-visão-de-futuro)
   - [Evolução Esperada](#81-evolução-esperada)
   - [Extensões Possíveis](#82-extensões-e-integrações-possíveis)
   - [Roadmap Inicial](#83-roadmap-inicial)
9. [Questões em Aberto](#9-questões-em-aberto)
10. [Referências](#10-referências)

---

## 1. Introdução

Este documento descreve a visão do projeto de **Logging Sistemático em Aplicações Java** — um conjunto de bibliotecas Java 21 que fornece logging estruturado, contextualizado e padronizado para aplicações baseadas em Jakarta EE e Quarkus 3.27.

O projeto parte de uma premissa central: logging é tratado historicamente de forma artesanal e subjetiva, resultando em logs inconsistentes e de difícil utilização para diagnóstico. O objetivo é afastar essa prática do empirismo e fundamentá-la em dados acadêmicos e práticas validadas pela indústria — elevando o log à categoria de **componente arquitetural com contratos claros**.

O projeto entrega três artefatos independentes sobre um único padrão conceitual:

| Artefato | Tecnologia | Quando usar |
|---|---|---|
| `lib-logging-slf4j` | SLF4J 2.x + Log4j2 + CDI Jakarta EE | Wildfly, TomEE, Payara, OpenLiberty |
| `lib-logging-quarkus` | JBoss Logging + ArC + OTel | Quarkus 3.27, JVM ou native image GraalVM |
| `quarkus-logging-extension` | Quarkus Extension (deployment + runtime) | Quarkus 3.27 com zero configuração manual |

Os três módulos expõem a mesma API pública (`LogSistematico`, `@Logged`) e produzem JSON estruturado com campos canônicos idênticos, permitindo queries unificadas em qualquer pipeline de observabilidade.
 
---

## 2. Contexto do Projeto

### 2.1 O Problema

Aplicações modernas baseadas em microsserviços enfrentam um desafio fundamental de rastreabilidade: cada serviço gera dados diagnósticos fragmentados, inconsistentes e frequentemente impossíveis de consultar. O estado atual produz os seguintes problemas concretos:

**Logs sem estrutura.** Serviços escrevem linhas de texto sem formato definido, tornando o parsing automatizado impossível ou frágil. Ferramentas como Elasticsearch e Loki exigem estrutura JSON para indexar e consultar campos individualmente.

**Contexto ausente.** Eventos de log carecem de identidade (quem disparou?), contexto espacial (qual serviço, método, container?) e contexto causal (por quê aconteceu?). A investigação pós-incidente depende de suposições.

**Fragmentação por distribuição.** Em uma arquitetura com dezenas de serviços, uma única requisição do usuário pode gerar registros em dez instâncias diferentes. Sem um identificador de correlação compartilhado, esses registros não podem ser ordenados em uma linha do tempo coerente.

**Subjetividade na instrumentação.** Cada desenvolvedor decide individualmente o que registrar, em qual nível de severidade e com qual contexto — resultando em logs inconsistentes entre equipes e sistemas que se tornam inutilizáveis em escala.

**Degradação silenciosa.** Exceções são registradas mas não rastreadas ou de-duplicadas centralmente, permitindo que um bug em 3% das requisições cresça silenciosamente por horas antes de ser percebido.

**O insight central** — quando o framework 5W1H é aplicado a cada evento de log, o arquivo de log deixa de ser uma parede de texto e passa a ser um banco de dados consultável. Em vez de `grep` no terminal, o engenheiro executa queries como: _"Mostre todos os erros (What) do usuário USR-445 (Who) nos últimos 10 minutos (When) no serviço de pagamentos (Where)."_

### 2.2 Público-Alvo

| Consumidor | Necessidades Primárias |
|---|---|
| **Desenvolvedores Java / Quarkus / Jakarta EE** | Emitir logs estruturados e contextualizados com mínimo boilerplate; injeção automática de contexto MDC |
| **Times de Plataforma / SRE** | Padronizar o formato de log em todos os serviços; integrar com stacks existentes de Prometheus, OpenTelemetry e Elasticsearch |
| **Times de Segurança e Conformidade** | Acesso a trilhas de auditoria de ações de usuários para LGPD e outros frameworks regulatórios |
| **Times de Negócio e Analytics** | Consultar eventos de negócio (abandono de carrinho, conversão, conclusão de pedido) na mesma infraestrutura de log, sem pipeline de analytics separado |

---

## 3. Objetivos e Benefícios Esperados

### 3.1 Objetivos Principais

- **Logging sistemático como componente arquitetural.** Estabelecer contratos claros sobre o que, como e quando registrar — com o mesmo rigor exigido de segurança, persistência e contratos de API.
- **Enforcement do framework 5W1H.** Garantir que todo evento de log produzido pela biblioteca responda a Who, What, When, Where, Why e How.
- **Saída JSON estruturada.** Emitir todos os eventos em JSON machine-parseable, com campos canônicos idênticos entre os três módulos, habilitando indexação direta por Elasticsearch, Loki e similares.
- **Propagação automática de contexto.** Injetar contexto de requisição (`userId`, `traceId`, `spanId`, `servico`) nas fronteiras de filtro/interceptor CDI, eliminando boilerplate repetitivo nas camadas de negócio.
- **Tracing distribuído via OpenTelemetry.** Integrar `traceId` e `spanId` reais do span OTel ativo em todos os eventos de log — nunca gerá-los manualmente.
- **Mascaramento automático de dados sensíveis.** Proteger credenciais e dados pessoais (LGPD) automaticamente pelo nome da chave, sem depender da disciplina manual do desenvolvedor.
- **Rastreamento de exceções.** Fornecer infra-estrutura para reportar exceções a backends centralizados (Sentry, Rollbar) com de-duplicação por fingerprint.
- **Eventos de negócio como cidadãos de primeira classe.** Suportar eventos como `ORDER_COMPLETED` e `CHECKOUT_STARTED` com tipo identificável (`event_type`), separado dos eventos técnicos, alimentando dashboards de negócio sem pipeline adicional.

### 3.2 Benefícios Esperados

- **MTTR reduzido.** Logs estruturados e correlacionados permitem ao engenheiro isolar a causa de um incidente por query, não por varredura manual de texto.
- **Detecção proativa de anomalias.** Estrutura consistente habilita alertas baseados em threshold ou padrão — por exemplo, detectar pico de logins falhos antes de um ataque ser confirmado por usuários.
- **Conformidade regulatória (LGPD).** Trilha de auditoria persistente e consultável de ações de usuários sobre entidades sensíveis, sem infraestrutura separada.
- **Produtividade do desenvolvedor.** Injeção de contexto zero-config significa que o desenvolvedor escreve lógica de negócio, não plumbing de observabilidade.
- **Business intelligence sem overhead.** Eventos de negócio estruturados alimentam dashboards no Kibana ou Grafana diretamente — sem banco de dados de analytics ou SDK de terceiros.
- **Resolução de disputas.** Payload e resposta de APIs externas registrados criam evidência técnica irrefutável para encerrar disputas com gateways e integradores.

### 3.3 Diferenciação das Soluções Existentes

| Capacidade | Ferramentas Existentes | Vantagem desta biblioteca |
|---|---|---|
| Logging estruturado | SLF4J + Logback/Log4j2 (configuração manual por serviço) | Enforcement do 5W1H embutido; JSON pré-configurado; zero boilerplate para campos de contexto |
| Propagação de contexto | MDC manual, disperso em cada camada | CDI Interceptor (`@Logged`) injeta automaticamente `userId`, `traceId`, `spanId` e `servico` |
| Mascaramento de dados | Nenhuma proteção automática padrão | `SanitizadorDados` aplica mascaramento/redação por nome de chave antes de qualquer registro |
| Eventos de negócio | Kafka producers customizados, SDKs de analytics externos | `businessEvent()` na mesma infraestrutura de log; sem dependência adicional |
| Auditoria | Soluções artesanais acopladas à lógica de negócio | `@Auditable` interceptor (planejado v0.3) captura before/after de forma transversal |

---

## 4. Escopo

### 4.1 O Que a Biblioteca Entrega

Uma descrição completa de cada capacidade está no documento de padrão. A tabela abaixo sumariza o escopo por fase:

| Capacidade | Documento | Fase |
|---|---|---|
| Logging estruturado — framework 5W1H | [logging_revisado.md](logging_revisado.md) | v0.1 |
| DSL + Fluent Interface (`LogSistematico`) | [implementacao_slf4j.md](implementacao_slf4j.md) / [biblioteca_quarkus.md](biblioteca_quarkus.md) | v0.1 |
| Interceptor CDI automático (`@Logged`) | [implementacao_slf4j.md](implementacao_slf4j.md) / [biblioteca_quarkus.md](biblioteca_quarkus.md) | v0.1 |
| Mascaramento automático de dados sensíveis (`SanitizadorDados`) | [logging_revisado.md § 10](logging_revisado.md) | v0.1 |
| Integração OpenTelemetry (`traceId` / `spanId`) | [biblioteca_quarkus.md § 5.3](biblioteca_quarkus.md) | v0.1 |
| Filtro HTTP (`requestId` / contexto de requisição) | [implementacao_slf4j.md § 12](implementacao_slf4j.md) | v0.2 |
| Extensão Quarkus (build-time, auto-config, Dev UI) | [biblioteca_quarkus.md](biblioteca_quarkus.md) | v0.2 |
| Rastreamento de exceções (`ExceptionReporter`) | [logging_revisado.md § 14](logging_revisado.md) | v0.3 |
| Log de auditoria (`@Auditable`, `AuditWriter`) | [logging_revisado.md § 13](logging_revisado.md) | v0.3 |
| Eventos de negócio (`businessEvent()`) | [logging_revisado.md § 8.3](logging_revisado.md) | v0.1 |
| Registro de nomes de campos canônicos | [FIELD_NAMES.md](FIELD_NAMES.md) | v0.1 |

### 4.2 Fora de Escopo

As seguintes capacidades são explicitamente excluídas do escopo atual:

- **Infraestrutura de agregação de logs.** A biblioteca emite logs estruturados para stdout; o pipeline de agregação (Fluentd, Logstash, Vector) é responsabilidade do consumidor.
- **Armazenamento e UI de traces.** A biblioteca exporta spans via OTel; operar um servidor Jaeger ou Zipkin está fora da fronteira da biblioteca.
- **Armazenamento e dashboards de métricas.** A biblioteca expõe métricas de duração via Micrometer; configurar scraping do Prometheus e dashboards no Grafana é responsabilidade do consumidor.
- **Política de retenção de logs.** Decidir por quanto tempo os logs são mantidos é uma decisão de plataforma, não da biblioteca.
- **Linguagens não-Java.** O projeto cobre Java 21 em v1. Ambientes poliglotas requerem implementações específicas por linguagem.

---

## 5. Regras e Princípios

### 5.1 Padrões de Código

- **Java 21.** Usar `record` para objetos de valor imutáveis (`LogEvento`, `LogContexto`, `AuditRecord`), `sealed interface` para hierarquias de etapas da DSL (`LogEtapas`), e pattern matching com `switch` para classificação por tipo de dado (`SanitizadorDados`).
- **CDI Jakarta EE e Quarkus 3.27.** Pontos de injeção usam `@ApplicationScoped`. Nenhuma anotação Spring.
- **Imutabilidade por padrão.** Todos os objetos de evento de log e registro de auditoria são tipos de valor imutáveis (Java records). Nenhum estado mutável deve ser adicionado a esses objetos.
- **Falhas de observabilidade não interrompem o negócio.** Falhas de infraestrutura de observabilidade (backend de tracing indisponível, `MeterRegistry` inacessível) devem ser registradas localmente no logger de fallback e nunca relançadas como exceções de negócio.
- **Overhead mínimo em runtime.** Injeção de contexto deve ser O(1) — sem consultas a banco de dados ou chamadas de rede síncronas no caminho do MDC. Serialização JSON deve usar `ObjectMapper` pré-compilado, não reflexão por evento.
- **Princípio editorial de mensagens.** Mensagens de log devem usar linguagem direta, neutra e consistente com o vocabulário do domínio. Jargão informal, abreviações ambíguas ou humor são proibidos — logs são lidos em incidentes críticos por pessoas que não escreveram o código.

### 5.2 Convenções de Nomenclatura

Ver [Registro de Nomes de Campos](FIELD_NAMES.md) para a lista canônica completa. A regra central: **nomes de campos são reservados e devem ser usados consistentemente em todos os serviços e nos três módulos da biblioteca.** Usar sinônimos (ex: `order_number` em vez de `order_id`, ou `service` em vez de `servico`) viola o princípio de consistência e produz resultados divididos em ferramentas de analytics.

### 5.3 Boas Práticas

**Passar o objeto de exceção completo, não apenas a mensagem.**

```java
// PROIBIDO — descarta classe, stack trace e cadeia de causas
logger.error(e.getMessage());

// CORRETO — preserva todas as informações necessárias para rastreamento
LogSistematico
    .registrando("Falha ao processar pedido")
    .em(PedidoService.class, "processar")
    .porque("Erro inesperado no gateway")
    .comDetalhe("pedidoId", pedidoId)
    .erro(e);
```

**Mensagens específicas com identificadores de entidade.**

```java
// PROIBIDO — sem valor diagnóstico em produção
log.error("Falha ao salvar");

// CORRETO
log.error("Falha ao salvar Order#{}: chave duplicada", orderId);
```

**Registrar o estado da entidade no momento do evento.** Incluir o estado relevante da entidade no momento do evento, não apenas seu identificador. Isso permite reconstrução pós-incidente sem consultar o banco de dados.

**Preferir campos estruturados em vez de interpolação de strings.**

```java
// PROIBIDO — string interpolada não é indexável
log.info("Pedido " + id + " salvo por usuário " + userId);

// CORRETO — campos estruturados via DSL
LogSistematico
    .registrando("Pedido salvo")
    .em(PedidoService.class, "criar")
    .comDetalhe("pedidoId", id)
    .comDetalhe("userId",   userId)
    .info();
```

**Timestamps sempre em UTC.** Timestamps em fuso horário local em sistemas distribuídos produzem linhas do tempo enganosas. Todos os servidores devem estar sincronizados via NTP.

**Nunca registrar dados sensíveis.** Senhas, tokens, números de cartão e campos protegidos pela LGPD devem ser mascarados ou redados antes de qualquer registro. O `SanitizadorDados` é a última linha de defesa, não a única — o desenvolvedor deve conhecer o que está passando em `.comDetalhe()`.

---

## 6. Conceitos Fundamentais

### 6.1 Glossário

| Termo | Definição |
|---|---|
| **Observabilidade** | A capacidade de inferir o estado interno de um sistema a partir de suas saídas externas (logs, traces, métricas). |
| **Log Estruturado** | Um evento de log representado como estrutura de dados machine-parseable (JSON) com campos nomeados, em oposição a uma linha de texto puro. |
| **Append-Only Stream** | Característica fundamental do log: eventos são acrescentados ao final do fluxo, nunca modificados ou removidos retroativamente. Alterar um registro de log viola esse contrato e pode comprometer investigações de segurança e conformidade regulatória. |
| **MDC** | Mapped Diagnostic Context — mapa thread-local que armazena pares chave-valor automaticamente acrescentados a todo evento de log emitido naquela thread. Gerenciado pelo `GerenciadorContextoLog`. |
| **5W1H** | Framework investigativo emprestado do jornalismo e da Análise de Causa Raiz: Who (Quem), What (O quê), When (Quando), Where (Onde), Why (Por quê), How (Como). Todo evento de log deve responder a essas seis dimensões. |
| **Request ID** | Identificador gerado uma vez por requisição HTTP, propagado em todos os logs e chamadas downstream geradas por aquela requisição. Escopo: um único serviço. |
| **Trace ID** | Identificador OpenTelemetry que atravessa múltiplos serviços e correlaciona todos os spans de uma única operação distribuída de ponta a ponta. Escopo: toda a operação. |
| **Span** | Uma unidade de trabalho dentro de um trace — ex: uma query de banco de dados, uma chamada HTTP a serviço downstream, uma publicação de mensagem. Spans formam uma estrutura de árvore sob um Trace. |
| **Log de Auditoria** | Registro permanente e consultável de ações de usuários sobre entidades de negócio, mantido para conformidade, investigação de segurança e suporte ao cliente. Padrão distinto e complementar ao log de aplicação. |
| **KEDB** | Knowledge Engineering Database — repositório interno que documenta causa raiz, impacto e procedimento de remediação para cada código de erro único. O `errorCode` no log é a chave de ligação com a KEDB. |
| **Rastreamento de Exceções** | Reporte de exceções a um serviço centralizado para de-duplicação por fingerprint, atribuição de responsabilidade e notificação da equipe — distinto e complementar à agregação de logs. |
| **Fingerprint** | Identificador estável para uma classe de exceções, calculado a partir do nome da classe e dos primeiros stack frames do código da aplicação. Garante que a milésima ocorrência do mesmo bug seja reconhecida como o mesmo bug. |
| **Mascaramento** | Substituição do valor sensível por representação reduzida (`"****"` para credenciais, `"[PROTEGIDO]"` para dados pessoais). Confirma presença sem expor o conteúdo. |
| **Redação** | Omissão completa do campo do registro JSON. Indicada quando nem a confirmação de presença pode ser registrada (dados sob sigilo legal, informações de menores). |
| **Agregação de Logs** | Coleta de fluxos de log de múltiplas instâncias de serviço em um store centralizado e consultável (ex: Elasticsearch + Kibana, Loki + Grafana). |
| **OpenTelemetry** | Padrão CNCF que fornece API, SDK e instrumentação vendor-neutral para traces, métricas e logs. |

### 6.2 Principais Abstrações

Estas são definições conceituais. Detalhes de implementação estão nos documentos de implementação referenciados.

| Abstração | Descrição | Documento |
|---|---|---|
| `LogSistematico` | Ponto de entrada público da DSL. Implementa as `sealed interfaces` da Fluent Interface, validando a sequência de chamadas em tempo de compilação. | [implementacao_slf4j.md § 8](implementacao_slf4j.md) / [biblioteca_quarkus.md § 5.6](biblioteca_quarkus.md) |
| `@Logged` | Anotação CDI `@InterceptorBinding` que ativa injeção automática de contexto (`userId`, `traceId`, `spanId`, `servico`, `classe`, `metodo`) e coleta de métricas de duração. | [implementacao_slf4j.md § 9](implementacao_slf4j.md) / [biblioteca_quarkus.md § 5.7](biblioteca_quarkus.md) |
| `GerenciadorContextoLog` | CDI bean `@ApplicationScoped` responsável pelo ciclo de vida do MDC: inicialização, registro de localização e limpeza garantida no `finally`. | [implementacao_slf4j.md § 5](implementacao_slf4j.md) / [biblioteca_quarkus.md § 5.3](biblioteca_quarkus.md) |
| `LogContexto` | `record` imutável que representa o snapshot do contexto de correlação de uma requisição (`traceId`, `spanId`, `userId`, `servico`). | [implementacao_slf4j.md § 4](implementacao_slf4j.md) / [biblioteca_quarkus.md § 5.2](biblioteca_quarkus.md) |
| `LogEvento` | `record` imutável que transporta o evento do builder da DSL até o emissor. Modela as dimensões 5W1H. | [implementacao_slf4j.md § 6](implementacao_slf4j.md) / [biblioteca_quarkus.md § 5.4](biblioteca_quarkus.md) |
| `SanitizadorDados` | Classe utilitária que aplica mascaramento ou redação a valores sensíveis pelo nome da chave, antes de qualquer registro. | [implementacao_slf4j.md § 3](implementacao_slf4j.md) / [biblioteca_quarkus.md § 5.1](biblioteca_quarkus.md) |
| `AuditRecord` | `record` imutável capturando ator, ação, entidade-alvo, estados before/after e timestamp. Implementação futura. | [logging_revisado.md § 13](logging_revisado.md) |
| `AuditWriter` | Interface injetável que emite `AuditRecord` como eventos estruturados de log. Sem implementações de persistência embutidas. Implementação futura. | [logging_revisado.md § 13](logging_revisado.md) |
| `ExceptionReporter` | Bean CDI que recebe exceções, enriquece com contexto OTel, de-duplica por fingerprint e encaminha ao backend configurado (Sentry, Rollbar, webhook). Implementação futura. | [logging_revisado.md § 14](logging_revisado.md) |

---

## 7. Exemplos de Uso

> Estes exemplos ilustram a experiência pretendida do desenvolvedor. Para os exemplos completos com JSON de saída, ver as seções de exemplos nos documentos de implementação.

### 7.1 Evento de Negócio

```java
LogSistematico
    .registrando("Pedido concluído")
    .em(PedidoService.class, "concluir")
    .porque("Pagamento confirmado pelo gateway")
    .como("API REST — POST /pedidos/{id}/concluir")
    .comDetalhe("eventType",   "ORDER_COMPLETED")
    .comDetalhe("pedidoId",    pedido.getId())
    .comDetalhe("valorTotal",  pedido.getValorTotal())
    .comDetalhe("currency",    "BRL")
    .info();
```

JSON emitido:

```json
{
  "timestamp":              "2026-03-09T14:32:01.123Z",
  "level":                  "INFO",
  "message":                "Pedido concluído",
  "traceId":                "7d2c8e4f1a3b9c2d4bf92f3577b34da6",
  "spanId":                 "a3ce929d0e0e4736",
  "userId":                 "USR-445",
  "servico":                "pedidos-service",
  "classe":                 "PedidoService",
  "metodo":                 "concluir",
  "log_motivo":             "Pagamento confirmado pelo gateway",
  "log_canal":              "API REST — POST /pedidos/{id}/concluir",
  "detalhe_eventType":      "ORDER_COMPLETED",
  "detalhe_pedidoId":       "ORD-9912",
  "detalhe_valorTotal":     "349.90",
  "detalhe_currency":       "BRL"
}
```

### 7.2 Auditoria de Ação Sensível (implementação futura — v0.3)

```java
@Auditable(action = "UPDATE", entity = "UserProfile")
public void atualizarEmail(Long userId, String novoEmail) {
    // lógica de negócio pura — nenhum código de auditoria aqui
}
```

A biblioteca intercepta a chamada, captura o estado before/after e grava um `AuditRecord` imutável via `AuditWriter` configurado. O desenvolvedor não precisa instrumentar a camada de auditoria manualmente.

### 7.3 Correlação de Trace Entre Serviços

Uma ação do usuário dispara três chamadas downstream:

```
Requisição do usuário → pedidos-service  (trace_id: "7d2c...", span_id: "a3ce...")
                              ↓
                       pagamentos-service  (mesmo trace_id: "7d2c...", novo span_id: "b4df...")
                              ↓
                    notificacoes-service  (mesmo trace_id: "7d2c...", novo span_id: "c5e0...")
```

O engenheiro filtra por `trace_id = "7d2c..."` no agregador de logs e visualiza os eventos dos três serviços em ordem cronológica — com um único filtro, independente de estarem em containers diferentes.

### 7.4 Mascaramento Automático

```java
// "password"  → "****"        (CREDENCIAL)
// "email"     → "[PROTEGIDO]" (DADO_PESSOAL)
// "ipOrigem"  → valor original (PUBLICO)
LogSistematico
    .registrando("Tentativa de autenticação")
    .em(AutenticacaoService.class, "autenticar")
    .comDetalhe("email",    request.email())
    .comDetalhe("password", request.senha())
    .comDetalhe("ipOrigem", request.ip())
    .warn();
```

---

## 8. Visão de Futuro

### 8.1 Evolução Esperada

- **GraalVM Native Image.** Todos os componentes devem ser compatíveis com o modo de compilação nativa do Quarkus para deployments serverless. A extensão Quarkus (`quarkus-logging-extension`) é o veículo natural para registrar hints de reflexão e proxy em build-time.
- **Propagação de contexto reativo.** Quarkus Mutiny e REST clients reativos usam execução não thread-local. A propagação de contexto via SmallRye Context Propagation já está implementada na biblioteca Quarkus; a extensão deve garantir que seja ativada automaticamente sem configuração manual.
- **OTel Logs signal.** À medida que o modelo de dados de Logs do OpenTelemetry se estabiliza, uma versão futura deve emitir logs via protocolo OTLP, unificando os três sinais em um único pipeline.
- **Filtro HTTP com `requestId`.** Implementação do filtro JAX-RS que gera e propaga o `X-Request-ID` header, completando o contrato definido na seção 4.2 do padrão conceitual.

### 8.2 Extensões e Integrações Possíveis

- **Propagação de contexto via mensageria.** Estender a propagação de contexto para headers de mensagens Kafka e JMS, habilitando rastreamento end-to-end em arquiteturas orientadas a eventos.
- **Especialização de eventos de segurança.** Um `SecurityEventLogger` dedicado para falhas de autenticação, negações de autorização e padrões de acesso anômalos — alimentando um sistema SIEM.
- **Geração de relatórios de conformidade.** Jobs agendados que produzem resumos de log de auditoria em formatos regulatórios (relatórios de acesso a dados LGPD, exports de change log SOC 2).
- **Sampling adaptativo.** Tail-based sampling que retém preferencialmente traces contendo erros ou outliers de latência, reduzindo custo de armazenamento sem perder valor diagnóstico.

### 8.3 Roadmap Inicial

| Fase | Versão | Entregáveis |
|---|---|---|
| **Foundation** | `v0.1` | `GerenciadorContextoLog` CDI bean; `LogSistematico` DSL com enforcement 5W1H; `@Logged` interceptor; `SanitizadorDados`; integração OTel (`traceId`/`spanId`); `businessEvent()`; `FIELD_NAMES.md` |
| **HTTP & Extension** | `v0.2` | Filtro HTTP com `requestId`; extensão Quarkus (`deployment` + `runtime`); auto-config sem `application.properties` manual; Dev UI integrado |
| **Audit & Exceptions** | `v0.3` | `@Auditable` CDI interceptor; `AuditWriter` implementations; `ExceptionReporter` com de-duplicação por fingerprint e webhook de notificação |
| **Production Ready** | `v1.0` | Compatibilidade GraalVM Native Image confirmada; propagação de contexto reativo validada; documentação completa; aplicação de exemplo |

---

## 9. Questões em Aberto

**Resolvidas:**

1. ✅ Semântica de identidade — `userId` apenas; `visitor_token` fora do escopo v1.
2. ✅ Estratégia de sampling — `always_on` na aplicação; tail-based no OTel Collector.
3. ✅ Imutabilidade do registro de auditoria — append-only stream; sem persistência na biblioteca em v1.
4. ✅ Padrão de nomes de campos — snake_case canônico definido em `FIELD_NAMES.md`; consistente entre os três módulos.
5. ✅ PII handling — opt-out automático por nome de chave via `SanitizadorDados`; campos que exigem redação completa devem ser omitidos antes de `.comDetalhe()`.
6. ✅ Separação audit vs. log — streams separados; log de auditoria é padrão distinto; implementação futura em v0.3.

**Em aberto:**

7. 🟡 Escopo de frameworks para jobs em background — quais schedulers (Quartz, Quarkus Scheduler) são suportados em v1? O `LoggingInterceptor` se comporta corretamente sem contexto HTTP em todos eles?
8. 🟡 Algoritmo de fingerprint para de-duplicação de exceções — considerar apenas o nome da classe e os N primeiros frames de código próprio, ou incluir a mensagem da exceção quando ela contiver identificadores estáveis?

---

## 10. Referências

**Fundamentos do padrão:**
- Microsoft Research (2010) — *Characterizing Logging Practices in Open-Source Software*
- Anton Chuvakin — *Security Information and Event Management*
- Chris Richardson — [Application Logging](https://microservices.io/patterns/observability/application-logging.html) — microservices.io
- Chris Richardson — [Distributed Tracing](https://microservices.io/patterns/observability/distributed-tracing.html) — microservices.io
- Chris Richardson — [Exception Tracking](https://microservices.io/patterns/observability/exception-tracking.html) — microservices.io
- Chris Richardson — [Audit Logging](https://microservices.io/patterns/observability/audit-logging.html) — microservices.io
- Iluwatar — [java-design-patterns: microservices-log-aggregation](https://github.com/iluwatar/java-design-patterns/tree/master/microservices-log-aggregation)
- Iluwatar — [java-design-patterns: microservices-distributed-tracing](https://github.com/iluwatar/java-design-patterns/tree/master/microservices-distributed-tracing)

**Especificações e padrões:**
- [OpenTelemetry Specification](https://opentelemetry.io/docs/specs/)
- [W3C TraceContext Recommendation](https://www.w3.org/TR/trace-context/)
- [Quarkus 3.x — OpenTelemetry Guide](https://quarkus.io/guides/opentelemetry)
- [Elasticsearch Common Schema (ECS)](https://www.elastic.co/guide/en/ecs/current/)

---

## Apêndice — Conceitos do VISION Original Fora do Escopo Atual

Os conceitos abaixo estavam presentes na versão 0.1 do documento e foram removidos por conflitarem com decisões já tomadas no projeto ou por estarem além do escopo definido. Ficam registrados aqui para referência.

---

**Health Check API**

O VISION 0.1 incluía uma `HealthContributor` interface e um endpoint `/q/health` pré-construído como entregável da fase Foundation (v0.1). O Quarkus já fornece SmallRye Health com essa capacidade; uma reimplementação pela biblioteca criaria sobreposição sem valor diferencial. Não há nenhum documento de implementação no projeto que cubra essa capacidade. Health Check permanece fora do escopo.

---

**`ObservabilityContext` como `@RequestScoped` com `hostname` e `pid`**

O VISION 0.1 propunha um bean `@RequestScoped` chamado `ObservabilityContext` como portador central de `trace_id`, `span_id`, `request_id`, `user_id`, `hostname` e `pid`. O projeto usa `GerenciadorContextoLog` (`@ApplicationScoped`) + MDC. A decisão de usar `@ApplicationScoped` em vez de `@RequestScoped` é intencional — o MDC é o mecanismo de propagação thread-local, e criar um bean de escopo de requisição para carregar os mesmos dados seria redundante. Os campos `hostname` e `pid` não estão nos documentos de implementação e não fazem parte do contrato de campos canônicos atual.

---

**`FieldNameAdapter` com dialetos ECS, Datadog e Graylog**

O VISION 0.1 propunha uma interface `FieldNameAdapter` com implementações para remapear nomes de campos canônicos para convenções de plataforma específicas (`ecs`, `datadog`, `graylog`). A Open Question #4 foi resolvida com snake_case canônico fixo, sem adaptador de dialetos. Introduzir dialetos múltiplos fragmentaria as queries e contradiria o princípio de consistência de nomes de campos. Não há nenhum documento de implementação que mencione essa abstração.

---

**`StructuredLogger` como abstração central**

O VISION 0.1 definia `StructuredLogger` como a API primária para emissão de eventos. O projeto usa `LogSistematico` (DSL com Fluent Interface e `sealed interfaces`). São dois modelos de API diferentes; `LogSistematico` já está documentado e implementado com validação em tempo de compilação. `StructuredLogger` não será introduzido.

---

**Taylor Scott / SolidusConf 2020 como fonte primária do 5W**

O VISION 0.1 atribuía o framework 5W a Taylor Scott (SolidusConf 2020). O padrão conceitual do projeto atribui o 5W ao jornalismo e à Análise de Causa Raiz (RCA), sem citação a essa palestra. A referência foi removida do projeto nos documentos anteriores e não é fonte bibliográfica do padrão.

---

**`Application Metrics` e `Health Check API` de Chris Richardson como referências**

O VISION 0.1 incluía os padrões Application Metrics e Health Check API de microservices.io na lista de referências. Nenhum dos dois é citado nos documentos de implementação. Foram removidos para refletir o escopo real do projeto.

---

**Versão do Quarkus (`3.20` vs `3.27`)**

O VISION 0.1 referenciava `Quarkus 3.20` em múltiplos lugares. A versão correta em uso nos documentos de implementação é `Quarkus 3.27`.

---

**`Out of Scope` excluindo Jakarta EE**

O VISION 0.1 declarava "Non-Quarkus frameworks are not in scope for v1". O projeto inclui explicitamente `lib-logging-slf4j` para Jakarta EE (Wildfly, TomEE, Payara, OpenLiberty) como um dos três artefatos principais.
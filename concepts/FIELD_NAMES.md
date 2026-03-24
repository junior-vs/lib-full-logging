# Registro de Nomes de Campos (Field Name Registry)

> **Este documento formaliza as chaves canônicas de saída JSON para a arquitetura de observabilidade.** A adoção de taxonomias padronizadas é crucial para a integridade dos painéis analíticos (Dashboards), agregadores e sistemas de busca estruturada.

---

## 1. Identidade e Correlação

As chaves abaixo mapeiam os participantes da transação e unificam a jornada de rastreamento de causa e efeito (Dimensões _Who_ e parte de _Where_). São inseridas de forma passiva através do `LogContexto` ou `GerenciadorContextoLog`.

| Campo | Tipo | Descrição | Fonte Automática |
| --- | --- | --- | --- |
| `userId` | `string` | Identificador persistente do usuário autenticado no contexto. | ✅ Segurança / CDI |
| `traceId` | `string` | Identificador do rastreamento distribuído W3C. | ✅ OpenTelemetry |
| `spanId` | `string` | Ponto específico da métrica correlacionada ao trace. | ✅ OpenTelemetry |
| `requestId` | `string` | Identificador unitário para agrupar instâncias do roteamento HTTP local. | ✅ Filtro JAX-RS |

---

## 2. Evento Estrutural e Topologia da DSL

Dimensões elementares capturadas pelos terminadores da abstração e *wrappers* padrão do sistema Quarkus/SLF4J. Mapeia estritamente a localização técnica do evento.

| Campo | Tipo | Descrição | Exemplo |
| --- | --- | --- | --- |
| `timestamp` | `string` | Linha do tempo exata em milissegundos UTC. | `"2026-03-11T21:55:00.123Z"` |
| `level` | `string` | Severidade daquele ponto de extração. | `"INFO"`, `"ERROR"` |
| `message` | `string` | Motivo base atrelado no Builder de Log. | `"Pedido criado através da web"` |
| `log_classe` | `string` | Nome da classe fonte da invocação (`.em()`). | `"PedidoService"` |
| `log_metodo` | `string` | Assinatura sumária do método em ação. | `"criar"` |
| `log_motivo` | `string` | Concluída via invocação da DSL `.porque()`. | `"Solicitação do cliente"` |
| `log_canal` | `string` | Via injetora sinalizada na DSL `.como()`. | `"API REST - POST"` |
| `servico` | `string` | Nome global do microsserviço/aplicação em execução. | `"pedidos-service"` |

---

## 3. Campos de Negócio e Detalhes Diversos

A **Domain Specific Language (DSL)** do projeto dita que complementos customizados repassados via chave-valor não afetem os metadados primários do JSON na raiz (Evitando colisão de mapeamento no Elastic/Logstash).

O ato de utilizar `.comDetalhe("pedidoId", valor)` prefixa ativamente os campos dentro da estrutura `LogEvento` no envelope:

| Original via código | Saída Canônica Resultante no JSON |
| --- | --- |
| `.comDetalhe("pedidoId", 4821)` | `"detalhe_pedidoId": "4821"` |
| `.comDetalhe("valor", 349.90)` | `"detalhe_valor": 349.90` |
| `.comDetalhe("token", "****")` | `"detalhe_token": "****"` (Mascarado por `SanitizadorDados`) |

---

## 4. Auditoria Imutável (Audit Logging)

Os campos abaixo são atrelados às entidades de monitoria contínua (compliance), persistidos via sistema acoplado a `@Auditable`. 

| Campo | Descrição e Comportamento |
| --- | --- |
| `action` | Tipo de operação lógica engatilhada: `CREATE`, `UPDATE`, `DELETE`, `READ`, `LOGIN`, `LOGOUT`. |
| `entity_type` | Categoria de representação (Ex: `User`, `Account`). |
| `entity_id` | Chave primária de afetamento no formato textual para consulta. |
| `state_before` | Representação condensada do snapshot ANTES do ato de intervenção. |
| `state_after` | Fila do estado integral APÓS a conclusão e commit (ou snapshot transitório). |
| `outcome` | Diagnóstico binário limitante: `SUCCESS` ou `FAILURE`. |

---

## Conceitos Fora do Escopo do Projeto

Abaixo compilamos definições e diretrizes herdadas que entram em **conflito com a geração de Log Automática da arquitetura contemporânea**, tendo sido descontinuadas ou repassadas aos coletores de infraestrutura:

### 1. Proibição Universal Restrita e Formato "Flat Snake_Case"
A orientação anterior obrigava o uso de notações como `user_id`, `trace_id` em detrimento de camelCase (`userId`, `traceId`). O ecossistema presente do `logging-quarkus` absorve a taxonomia nativa da integração OTel e JBoss (utilizando `userId`, `traceId`), além de prefixos orgânicos na DSL como `log_classe` e `detalhe_pedidoId`. Sendo assim, ditames focados unicamente na padronização forçada por snake_case não são aplicáveis à fundação da biblioteca atual.

### 2. Adaptação Dinâmica de Campos (Platform Adapters / `observa4j.fields.standard`)
Instruir a aplicação a mapear `trace_id` para `dd.trace_id` (Datadog) ou para sintaxes da *Elasticsearch Common Schema (ECS)* acopla indevidamente ferramentas de coleta nos SDKs do código. 
Tais reconfigurações estruturais cabem integralmente aos roteadores (OTel Collector, Logstash, FluentBit) implantados fora do limite de compilação da aplicação; garantindo o princípio de que o container produza JSON canônico puro e sem transformações reativas a destinos múltiplos. 

### 3. Convenções de Output Defasadas (`@timestamp` e `severity`)
Documentos anteriores ditavam que as bases relativas a data carregavam intrinsecamente o sinal gráfico `@timestamp` por dependência forte ao indexador Elasticsearch, e o grau de risco como `severity`. A implementação do Quartus Json Template unifica as diretrizes de forma nativa e isolada da ferramenta gerando estritamente: `"timestamp"` e `"level"`, dispensando hacks de nomeação forçada de pacotes antigos.

### 4. Construção de Objetos Aninhados Extensos (Nested JSON Blocks for Exceptions)
A premissa anterior exigia que a serialização do bloco de exceção obedecesse a um formato estrito (ex: construindo subnós manuais de `exception.class`, `exception.stack_trace`, etc). No cenário presente, subdelega-se a serialização do rastro estruturado (*stacktrace* e causas da exceção) para o layout de JSON nativo dos formatadores Quarkus (`quarkus-logging-json`) ou do `Log4j2 (JsonTemplateLayout)`, não sendo incumbência de montagem declarativa obrigatória em objetos locais de intercepção.
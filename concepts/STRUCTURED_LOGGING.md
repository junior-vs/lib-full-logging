# Logging Estruturado

> Logging não é um mecanismo de debug — é um **componente estrutural da engenharia de software** e a base primária para observabilidade. Um log estruturado transforma eventos de sistema em dados consultáveis, alertáveis e auditáveis.

---

## Por que JSON?

Texto puro exige expressões regulares complexas e frágeis para extrair informação. Uma alteração no formato da mensagem — um espaço a mais, um campo reordenado — quebra silenciosamente o parser e corrompé dashboards e alertas.

JSON resolve esse problema de forma direta: cada dimensão do evento é um campo nomeado, tipado e indexável. O Elasticsearch, o Loki e o Datadog ingeram JSON nativamente — sem configuração de parser, sem regex, sem manutenção de padrões de texto.

| Abordagem | Consultável? | Parseável nativamente? | Indexação consistente? |
|---|---|---|---|
| `System.out.println("Erro id " + id)` | Não | Não | Não |
| `log.info("Pedido {} processado", id)` | Não | Não | Não |
| `LogSistematico` (JSON estruturado) | Sim | Sim | Sim |

Exemplo da diferença em uma query real no Elasticsearch / Kibana:

```
# Texto puro — impossível sem regex frágil
message: /Erro ao processar pedido [0-9]+/

# JSON estruturado — query direta e robusta
level: ERROR AND detalhe_pedidoId: "4821" AND @timestamp:[now-1h TO now]
```

---

## Configuração de Saída JSON

### Quarkus 3.27

```properties
# application.properties
quarkus.log.console.json=true
```

Todos os campos do MDC aparecem automaticamente como chaves de primeiro nível no objeto JSON de saída. Nenhuma configuração adicional é necessária para os campos da biblioteca.

### SLF4J + Log4j2 (Jakarta EE)

```xml
<!-- src/main/resources/log4j2.xml -->
<Configuration status="WARN">
    <Appenders>
        <Console name="JsonConsole" target="SYSTEM_OUT">
            <JsonTemplateLayout
                eventTemplateUri="classpath:LogstashJsonEventLayoutV1.json"/>
        </Console>
    </Appenders>
    <Loggers>
        <Root level="INFO">
            <AppenderRef ref="JsonConsole"/>
        </Root>
    </Loggers>
</Configuration>
```

O `JsonTemplateLayout` do Log4j2 serializa todos os campos do MDC como chaves de primeiro nível no JSON — o mesmo resultado da configuração Quarkus.

---

## O Log como Fluxo Append-Only

O log é uma sequência ordenada de registros imutáveis. Cada evento é acrescentado ao final do fluxo — nunca modificado ou removido retroativamente. Essa característica tem implicações diretas sobre como o sistema deve ser projetado:

**Fonte da verdade.** Um banco de dados registra o estado *atual* de uma entidade. O log registra cada *mudança de estado* ao longo do tempo. Um log completo permite reconstruir o estado de qualquer entidade em qualquer ponto do passado — sem consultar o banco de dados.

**Imutabilidade como contrato.** Alterar ou deletar um registro de log — mesmo para "corrigir" uma mensagem — viola o contrato do padrão append-only e pode comprometer investigações de segurança, disputas técnicas e conformidade regulatória (LGPD, SOC 2).

**Agregação centralizada obrigatória.** Em microsserviços, cada instância gera seu próprio fluxo de logs. Uma única operação distribuída gera eventos em N serviços diferentes. Sem um ponto de agregação centralizado (ELK, Loki, Datadog), os logs de uma operação ficam espalhados em dezenas de arquivos em containers distintos — o diagnóstico se torna impraticável independente da qualidade dos logs individuais.

---

## A DSL e o Contrato 5W1H

A DSL (`LogSistematico`) não é apenas uma API conveniente — é um **mecanismo de enforcement**. O compilador Java impede que um log seja emitido sem as dimensões obrigatórias *What* (`.registrando()`) e *Where* (`.em()`). Logs incompletos são erros de compilação, não bugs silenciosos em produção.

```
LogSistematico
    .registrando(evento)           // What  — obrigatório: o que aconteceu
    .em(classe, metodo)            // Where — obrigatório: onde no código
    [ .porque(motivo)         ]    // Why   — opcional: causa de negócio
    [ .como(canal)            ]    // How   — opcional: canal de entrada
    [ .comDetalhe(chave, val) ]*   // extra — zero ou mais campos de negócio
    .info() | .debug() | .warn() | .erro(ex) | .erroERelanca(ex)
```

As dimensões *Who* (`userId`, `servico`) e *When* (`timestamp`) são injetadas automaticamente pela infraestrutura via MDC — o desenvolvedor não as declara.

### Exemplo completo

```java
LogSistematico
    .registrando("Falha ao processar pagamento")
    .em(PagamentoService.class, "processar")
    .porque("Gateway recusou a transação")
    .como("API REST — POST /pagamentos")
    .comDetalhe("errorCode",         "PAG-4022")
    .comDetalhe("pedidoId",          pedido.getId())
    .comDetalhe("codigoErroGateway", e.getCodigo())
    .comDetalhe("token",             request.token()) // ← mascarado: "****"
    .erro(e);
```

JSON emitido:

```json
{
  "timestamp":                  "2026-03-11T21:55:00.123Z",
  "level":                      "ERROR",
  "message":                    "Falha ao processar pagamento",
  "traceId":                    "4bf92f3577b34da6a3ce929d0e0e4736",
  "spanId":                     "a3ce929d0e0e4736",
  "userId":                     "joao.silva@empresa.com",
  "servico":                    "pagamentos-service",
  "classe":                     "PagamentoService",
  "metodo":                     "processar",
  "log_classe":                 "PagamentoService",
  "log_metodo":                 "processar",
  "log_motivo":                 "Gateway recusou a transação",
  "log_canal":                  "API REST — POST /pagamentos",
  "detalhe_errorCode":          "PAG-4022",
  "detalhe_pedidoId":           "4821",
  "detalhe_codigoErroGateway":  "INSUFFICIENT_FUNDS",
  "detalhe_token":              "****"
}
```

---

## Gerenciamento de Contexto Automático

### MDC como mecanismo de propagação

O MDC (Mapped Diagnostic Context) é um mapa thread-local que armazena pares chave-valor automaticamente acrescentados a todo evento de log emitido naquela thread. É o mecanismo que permite que `userId`, `traceId` e `servico` apareçam em todos os logs de uma requisição sem que o desenvolvedor os passe explicitamente em cada chamada.

O `GerenciadorContextoLog` é o único ponto de escrita do MDC na biblioteca. Chamadas diretas a `MDC.put()` fora dele são não conformidades — criam campos fora do contrato canônico e dificultam o rastreamento de vazamentos de contexto.

### Integração OpenTelemetry

O `traceId` e o `spanId` são extraídos do span OTel ativo via `Span.current().getSpanContext()`. Na versão Quarkus, o `quarkus-opentelemetry` auto-instrumenta chamadas HTTP de entrada e saída, propagando o cabeçalho `traceparent` (W3C TraceContext) automaticamente. Na versão SLF4J, a instrumentação OTel é responsabilidade da configuração do agente Java OTel ou da instrumentação manual.

O `traceId` **nunca** deve ser gerado como `UUID.randomUUID()`. Um ID falso não correlaciona com nenhum span em nenhum sistema de tracing e torna o campo inútil para diagnóstico distribuído.

### Contexto reativo (Quarkus)

Em pipelines Mutiny e RESTEasy Reactive, a execução pode trocar de thread entre operações assíncronas. O `ThreadLocal` do MDC é silenciosamente perdido nessa troca. O `quarkus-smallrye-context-propagation` (habilitado via `quarkus.arc.context-propagation.mdc=true`) propaga o MDC e o span OTel automaticamente entre as trocas de thread do Vert.x — sem código adicional no desenvolvedor.

### Limpeza obrigatória

O MDC deve ser limpo ao final de cada requisição ou invocação de método. Sem limpeza, o contexto da requisição anterior vaza para a próxima requisição atendida pela mesma thread no pool — `userId` errado, `traceId` errado, `servico` errado em logs subsequentes.

A limpeza é garantida em dois pontos:

- **`LoggingInterceptor` / `LogInterceptor`** (via `@Logged`): limpa os campos de localização (`classe`, `metodo`) e chama `GerenciadorContextoLog.limpar()` no bloco `finally` de cada invocação interceptada.
- **Filtro HTTP** (via `ContainerRequestFilter`): chama `GerenciadorContextoLog.limpar()` na fase de resposta, após o processamento completo da requisição.

---

## Segurança e Mascaramento

### Princípio de data minimization

O logging deve seguir o princípio de data minimization: registrar apenas o que é necessário para diagnóstico e auditoria. Os seguintes campos são **proibidos** nos logs:

- Senhas e hashes de senha
- Tokens de autenticação e autorização (JWT, API keys)
- Números de cartão de crédito (PAN) e CVV
- CPF, RG e dados pessoais sensíveis conforme LGPD
- Dados que identifiquem menores de idade

### Mascaramento automático via `SanitizadorDados`

O `SanitizadorDados` intercepta valores sensíveis pelo nome da chave antes de qualquer registro, aplicando dois graus de proteção:

| Categoria | Chaves interceptadas | Valor no JSON |
|---|---|---|
| Credenciais | `password`, `senha`, `token`, `accesstoken`, `refreshtoken`, `authorization`, `apikey`, `cvv`, `secret` | `"****"` |
| Dados pessoais | `cpf`, `rg`, `email`, `celular`, `cardnumber`, `numerocartao` | `"[PROTEGIDO]"` |
| Demais | qualquer outra chave | valor original |

A diferença entre as duas categorias é semântica: credenciais têm seu valor substituído por `"****"`, confirmando presença sem expor o conteúdo; dados pessoais têm seu valor substituído por `"[PROTEGIDO]"`, o que satisfaz o princípio de minimização de dados da LGPD para dados de identificação pessoal.

**Redação completa** (omissão do campo do JSON) é necessária para dados sob sigilo legal ou de menores de idade. Não está implementada automaticamente — campos que exigem redação devem ser omitidos antes de chamar `.comDetalhe()`.

### Proteção em trânsito (SSL/TLS)

Mascaramento na aplicação não é suficiente se o canal de transporte não estiver protegido. Logs transmitidos em texto claro entre o container e o coletor (Fluentd, Logstash, OTel Collector) podem expor campos de contexto não mascarados — `userId`, `traceId`, nomes de entidades — a qualquer observador de rede. O transporte de logs deve usar **SSL/TLS** em todos os segmentos do pipeline.

---

## Níveis de Severidade

A escolha do nível deve ser determinística, baseada no impacto sobre o estado do sistema — não em julgamento subjetivo.

| Nível | Quando usar | Habilitado em produção? |
|---|---|---|
| `TRACE` | Diagnóstico de baixo nível: entradas/saídas de métodos, iterações, valores intermediários detalhados | Nunca — apenas em desenvolvimento local |
| `DEBUG` | Fluxos internos, decisões condicionais, dados intermediários sem alteração de estado | Não por padrão — ativável dinamicamente por pacote |
| `INFO` | Operações que alteram estado: persistência, autenticação, chamadas externas | Sempre |
| `WARN` | Situações anômalas recuperáveis: tentativas de acesso indevido, fallbacks ativados, validações rejeitadas | Sempre |
| `ERROR` | Falhas reais: exceção que impede o cumprimento do contrato da operação | Sempre |
| `FATAL` | Falhas que tornam a aplicação incapaz de continuar — exigem intervenção imediata | Sempre |

**Regra anti-duplicação:** é proibido registrar a mesma exceção em múltiplas camadas sem agregar contexto adicional. Cada camada loga apenas o que sabe a mais sobre o erro.

**Ativação dinâmica de `DEBUG`:** em produção, o nível `DEBUG` pode ser ativado por pacote específico sem reinicialização — via `quarkus-logging-manager` (Quarkus) ou reconfiguração dinâmica do Log4j2 (SLF4J). Isso permite investigar incidentes ativos sem elevar o volume global de logs.

---

## Eventos de Negócio

Eventos relevantes para o negócio devem ser distinguíveis de eventos técnicos nas ferramentas de observabilidade. Isso habilita dashboards de analytics em tempo real e alertas baseados em tipo de evento específico, sem a necessidade de um pipeline de analytics separado.

```java
LogSistematico
    .registrando("Pedido concluído")
    .em(PedidoService.class, "concluir")
    .porque("Pagamento confirmado pelo gateway")
    .comDetalhe("eventType",  "ORDER_COMPLETED")
    .comDetalhe("pedidoId",   pedido.getId())
    .comDetalhe("valorTotal", pedido.getValorTotal())
    .comDetalhe("currency",   "BRL")
    .info();
```

O campo `detalhe_eventType` no JSON torna o evento identificável como categoria distinta em queries como `detalhe_eventType: "ORDER_COMPLETED"` — sem depender de parsear o campo `message`.

---

## Ciclo de Melhoria Contínua

O logging é um componente vivo da arquitetura. Após cada incidente em produção:

1. Revisar os logs gerados durante o incidente.
2. Identificar quais informações estavam ausentes e atrasaram o diagnóstico.
3. Atualizar a biblioteca ou o padrão para que a lacuna seja preenchida automaticamente no futuro.
4. Incorporar a melhoria como novo padrão organizacional e atualizar o checklist de Code Review.

Um incidente em que o `userId` estava ausente leva a adicionar `@Logged` na camada de serviço. Um incidente em que o `motivo` estava genérico leva a refinar o `.porque()` naquele fluxo. Cada incidente é uma oportunidade de tornar o próximo diagnóstico mais rápido.

---

## Fora do Escopo

### API fluent direta do SLF4J 2.x como substituto da DSL

O método `logger.atInfo().addKeyValue("chave", valor).log("mensagem")` é válido tecnicamente, mas não valida a sequência 5W1H em tempo de compilação, não aplica mascaramento automático e não integra com o `GerenciadorContextoLog`. Para logs não críticos fora do domínio de negócio (ex: logs internos da própria biblioteca), é aceitável. Para código de aplicação que deve obedecer ao padrão, `LogSistematico` é obrigatório.

### Output GELF / Graylog nativo na aplicação

Configurar `quarkus.log.handler.gelf.enabled=true` transmite logs diretamente da aplicação para o Graylog via UDP — acoplando a aplicação à infraestrutura de coleta. A arquitetura correta emite JSON para `stdout`; o escoamento é responsabilidade do coletor externo (OTel Collector, FluentBit). Além disso, o GELF usa um formato JSON aninhado diferente do formato flat produzido por `quarkus-logging-json`, o que cria inconsistências nos campos indexados.

### Log rotation e escrita em arquivo

Lógicas de rotação de arquivo e controle de escrita em disco contradizem o modelo container-native (Kubernetes, Docker). Containers descartáveis não devem gerenciar arquivos — devem emitir para `stdout`. A governança de armazenamento e retenção é responsabilidade da plataforma de orquestração e do coletor.

### `@AroundInvoke` manual pelo desenvolvedor de aplicação

Documentos anteriores apresentavam exemplos onde o desenvolvedor de aplicação criava interceptores CDI com `@AroundInvoke` para inserir campos no MDC manualmente. Isso é desnecessário: o `LoggingInterceptor` / `LogInterceptor` da biblioteca já realiza essa função via `@Logged`. Criar interceptores manuais paralelos duplica responsabilidade, cria risco de vazamento de contexto em ambientes reativos e produz campos fora do contrato canônico.
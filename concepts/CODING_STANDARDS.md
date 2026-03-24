# Padrões de Codificação e Boas Práticas (Coding Standards)

> Diretrizes arquiteturais para consumo consistente da biblioteca de log `logging-quarkus` (e implementações portáveis), bem como obrigações e práticas de *Code Review*.

---

## 1. Linguagem e Plataforma Base

| Requisito | Especificação Oficial do Projeto |
| --- | --- |
| Versão Java | Java 21 (mínimo) |
| Framework Base | Quarkus 3.27 |
| Tipagem e Imutabilidade | Uso de `records` para transferência (ex: `LogEvento`, `LogContexto`) |
| Injeção de Dependências | Nativa CDI (`@ApplicationScoped`, `@Inject`) |
| Concorrência | *Virtual threads* e propagadores de contexto reativo (SmallRye) |

O SDK se apoia nas funcionalidades vitais do Java 21, alavancando as *Sealed Interfaces* para prender o contrato do preenchimento da DSL estritamente às intenções originais, sem extensão acidental (`permits LogSistematico`).

---

## 2. Padrões Proibidos (Anti-Patterns)

Cenários atestados como destrutivos para a infraestrutura de observabilidade. Devem ser barrados sistematicamente por análise em Pull Requests.

### 2.1. Manipulação via Fluxo de Sistema Padrão
Ignora metadados, contexto ThreadLocal e direcionamentos de File Handlers.
```java
// PROIBIDO
System.out.println("Processando nota de id " + notaId);
System.err.println("Falhou: " + e.getMessage());
e.printStackTrace();
```

### 2.2. Concatenação de Textos e Falsificação JSON (`String.format`)
Destrói a formatação indexável pelas ferramentas como o Elasticsearch, criando complexidade frágil no parser e strings sem identidade analítica.
```java
// PROIBIDO
log.info("Processo da nota " + notaId + " feito por " + userId);
log.info(String.format("{\"notaOrigem\":\"%s\"}", notaId));

// CORRETO — Via DSL Guiada da aplicação
LogSistematico
    .registrando("Nota processada")
    .em(NotaService.class, "processar")
    .comDetalhe("notaOrigem", notaId)
    .info();
```

### 2.3. Omissão e Sequestro da Pilha de Exceções (Stacktrace)
Isola o desenvolvedor de identificar a dependência profunda de onde a causa estourou, exigindo duvidosa adivinhação sobre a classe/linha exata do NullPointerException ou SQLException.
```java
// PROIBIDO — descarta class e causa original (cause chain)
log.error(e.getMessage());

// CORRETO — submete o objeto completo ao invólucro da DSL
LogSistematico
    ...
    .erro(e); // Registra todo o rastreamento junto com o metadado customizado
```

### 2.4. Diagnósticos Genéricos ("Fantasmas")
Logs vagos forçam pesquisas difusas em produção. Todo evento em ambiente de grande escala demanda o identificador local atuando ativamente na frase ou injetado via `comDetalhe()`.
```java
// PROIBIDO
log.error("Erro ao salvar no banco");

// CORRETO
LogSistematico
    // ...
    .comDetalhe("idVenda", vendaId)
    .erro(e);
```

### 2.5. Efeito "Log-and-Throw" (Multiplicação de Ruído)
Logar na camada de dados e relançar, resultando na camada controladora efetuando o mesmo relato do mesmíssimo erro. Retarde o log até as extremidades do *handled scope* (tratamento) ou anexe uma camada distinta e engula o relato base, nunca os dois sem evolução.
```java
// PROIBIDO
catch(VendaException e) {
    LogSistematico.registrando("Erro venda")...erro(e);
    throw e; // A triagem lá em cima repetirá o erro no Kibana.
}

// CORRETO
catch(VendaException e) {
    LogSistematico.registrando("Falha tratada...")...erro(e);
    return Response.status(500).build();
}
```

### 2.6. Criação Ingênua de `traceId` Falsos
Identificadores rastreados (Traces) jamais devem ser falsificados (Ex: via `UUID.randomUUID()`) localmente. Se não gerado via Jaeger/OTel pelas frentes de captura, não há concatenação interserviços. Confie na inserção passiva da plataforma via `GerenciadorContextoLog`.

### 2.7. Exposição Contínua nos Pools (Ausência de Finally)
Variáveis estagnadas no MDC contaminarão as chamadas subjacentes do Mutiny/Vertx para sempre caso a desinfecção não ocorra no bloco absoluto de encerramento de escopo.
```java
// CORRETO - SEMPRE garanta a extração
try {
    gerenciadorContextoLog.inicializar(usuario);
    processarNegocio();
} finally {
    gerenciadorContextoLog.limpar();
}
```

### 2.8. Computação Custosa sem Guarda de Nível
Serializações pesadas (ex: converter objetos inteiros para JSON) devem ser protegidas por verificação prévia do nível ativo para evitar overhead em produção.
```java
// PROIBIDO — serializa o objeto mesmo com DEBUG desabilitado
log.debug("Estado do pedido: {}", objectMapper.writeValueAsString(pedido));

// CORRETO — custo pago apenas se o nível estiver habilitado
if (log.isDebugEnabled()) {
    log.debug("Estado do pedido: {}", objectMapper.writeValueAsString(pedido));
}
```

---

## 3. Gestão de Níveis de Severidade

A escolha do nível de log deve ser determinística, não subjetiva:

| Nível | Quando usar | Em produção? |
|---|---|---|
| `TRACE` | Diagnóstico de baixo nível (entradas/saídas de métodos, iterações) | Nunca — apenas local |
| `DEBUG` | Fluxos internos, decisões condicionais, dados intermediários | Não por padrão — ativável dinamicamente |
| `INFO` | Operações que alteram estado: persistência, autenticação, chamadas externas | Sempre |
| `WARN` | Situações anômalas recuperáveis: *fallbacks*, validações rejeitadas | Sempre |
| `ERROR` | Falhas reais que impedem o cumprimento do contrato da operação | Sempre |
| `FATAL` | Falhas que tornam a aplicação incapaz de continuar operando | Sempre |

---

## 4. Padrões Obrigatórios

1. **Abordagem Orientada a Objeto Imutável**:
   Ao arquitetar fluxos de log e dados subjacentes, todos os artefatos base da biblioteca transitam passivamente via `record` intocável (`LogContexto`, `LogEvento`).
2. **Máscara de Dados**:
   Sensibilidade (`SanitizadorDados`) assume a vanguarda e omite automaticamente informações regulatórias caso um programador inexperiente faça `LogSistematico.comDetalhe("cpf", valor);`. Sempre valide os *sets* internos de mascaramento quando integrar frentes de segurança novas (PCI/DSS/LGPD).
3. **Falhas Silenciosas na Infraestrutura de Observabilidade**:
   Falhas de backends de observabilidade (OTel, Sentry, métricas) **nunca devem propagar como exceções de negócio**. Registre a falha localmente e continue a execução do sistema.

---

## Conceitos Não Aplicáveis ou Fora do Escopo do Projeto

Parte dos manuais e rascunhos de base prévia da infraestrutura descrevia módulos genéricos cujas práticas não vigoram na DSL em questão ou competem arquiteturalmente com as features inerentes construídas pelo Quarkus.

### 1. Inserções Diretas na API Desacoplada ("Add Key Value / SLF4J 2.x")
Em iterações estagnadas, ensinava-se que invocar `logger.atInfo().addKeyValue("ordem", 1).log()` supriria a demanda estruturada. No escopo primário do Padrão Quarkus da plataforma, é obrigação absoluta utilizar as classes protetoras geradas em `LogSistematico`. O fluxo `.registrando().em().info()` substitui e inutiliza as montagens artesanais baseadas avulsamente nas interfaces do SLF4J.

### 2. Rastreamento Assíncrono Desconectado de Exceptions (`ExceptionReporter / Tracking Backend`)
Regras instruindo o uso de chamadas *hard-coded* e tratamentos como `trackingBackend.report(ex)` atrelavam a entrega a um bean (`observa4j-exceptions`) de report local que tentava engolir erros de comunicação e agir como despachante do Sentry. Atualmente, os relatórios para ecossistemas de Exceptions (como Sentry ou Elastic APM) delegam às suas próprias extensões (Ex: `quarkus-sentry`) ou OTel Collectors o trabalho de ouvir e transportar exceções disparadas nativamente na Thread via `LogSistematico.erro(ex)`, removendo esse acoplamento impuro das responsabilidades diretas de uso diário. 

### 3. Chamadas de Identificação em Lote (`structuredLogger.businessEvent`)
Sugestões como transacionar *Business Events* e mapas completos através de `structuredLogger.businessEvent("NOME_EVENTO", mapaDeVariaveis)` foram defasadas pela expressividade dos sufixos da interface atual da DSL. Essa ação corresponde organicamente a: `.registrando("NOME_EVENTO").porque("Razão de Fluxo").comDetalhe(x,y).info()`. A unificação e eliminação de canais dúbios de relato confere consistência plena em 100% da base.

### 4. Classes Fixas de Auditoria Comuns (`AuditRecord`)
Outrora acoplado, o preenchimento de matrizes como `observa4j-audit` (`AuditRecord` instanciados em código com parâmetros maciços `stateBefore/stateAfter`) não é parte rotineira da fundação basal documentada da library de estruturação; em seu lugar a biblioteca delega à geração de `Eventos/JSON` tipados a inserção das tags, permitindo que a agregação cuide da rastreabilidade legal separadamente do código vital de injeção da aplicação.

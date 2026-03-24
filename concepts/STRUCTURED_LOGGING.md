# Logging Estruturado

> Logging não é apenas um mecanismo de debug — é um **componente estrutural da engenharia de software** e a base primária para a observabilidade.

O registro estruturado significa que cada evento de log é uma estrutura de dados legível por máquina (JSON) com campos consolidados, tipados e hierárquicos, diferentemente de uma linha de texto livre arbitrária. Isso transforma os logs de um repositório passivo de falhas em uma fonte ativa e consultável.

No ecossistema atual, o projeto utiliza nativamente a extensão `quarkus-logging-json` para a padronização das saídas de log orientadas a objetos, e integra-se nativamente com tecnologias de correlação como OpenTelemetry.

---

## Por que JSON?

A utilização de texto puro exige regras complexas de expressões regulares para buscar e categorizar falhas, frequentemente apresentando inconsistências ("silenciosas") dependendo do desenvolvedor que criou o log.

Logs estruturados JSON resolvem esse problema de maneira direta e inequívoca, permitindo pesquisas analíticas complexas e baseadas em metadados:

| Abordagem técnica | Consultável? | Parseável de forma nativa? | Consistência de indexação? |
|---|---|---|---|
| `System.out.println("Erro processamento id " + id)` | Não | Não | Não |
| `log.info("Processado pedido: {}", id)` | Não | Não | Não |
| **Padrão LogSistematico (JSON Estruturado)** | **Sim** | **Sim** | **Sim** |

Para ativação do log estruturado na saída do Quarkus, a propriedade obrigatória é:
```properties
quarkus.log.console.format=json
```

---

## O Framework 5W1H e Consistência de Campos

A adoção do logging estruturado pressupõe obediência ao modelo 5W1H (Who, What, When, Where, Why, How). O objetivo é garantir que cada entrada traga contexto máximo investigativo. As nomenclaturas de campos seguem a convenção nativa da plataforma (camelCase para identificadores como `userId`, `traceId`; prefixo `log_` para campos da DSL; prefixo `detalhe_` para campos de negócio).

Todo log deve responder, implícita ou explicitamente:
- **Who:** Identificador do ator (ex. `userId`)
- **What:** O evento e nível sintético
- **When:** Timestamp referenciado em UTC
- **Where:** Serviço, componente, `requestId` ou `traceId`
- **Why e How:** O motivador de negócio e canal condutor da informação.

---

## DSL e O Padrão do Projeto

Para afastar a subjetividade sobre *quem*, *como* ou *quando* escrever os logs, a arquitetura substitui as bibliotecas tradicionais diretas por uma *Domain Specific Language (DSL)* através da Fluent Interface `LogEtapas` suportada por contratos rígidos de compilação.

### Como aplicar:
Desenvolvedores devem usar o modelo `LogSistematico` que, guiado pelo compilador (`registrando -> em -> [porque|como|comDetalhe] -> nivel`), encadeia a declaração estrutural:

```java
// Estruturação encadeada via LogSistematico (Padrão Exigido no Código)
LogSistematico
    .registrando("Pedido processado no terminal")
    .em(PedidoService.class, "processar")
    .porque("Rotina de baixa de compras do parceiro")
    .como("Job Assíncrono / Scheduller")
    .comDetalhe("pedido_id", pedido.getId())
    .info(log);
```

---

## Gerenciamento de Contexto Automático (MDC + OpenTelemetry)

A estrutura delega o preenchimento da dimensão *Where* (Contexto) ao `GerenciadorContextoLog`. Por meio de abstração da camada estrutural de MDC (*Mapped Diagnostic Context*) e captura inteligente das threads:

1. A plataforma Quarkus interliga e preenche automaticamente `traceId` e `spanId` das transações mapeadas pelo OpenTelemetry (`quarkus-opentelemetry`).
2. Modificações ou injetores que lidam concorrentemente com *ThreadPools* requerem atenção massiva baseadas no `quarkus-smallrye-context-propagation` para que não haja perdas em ambientes reativos.
3. **Limpeza Mandatória:** Ao lidar explicitamente com o ciclo de vida do contexto, a chamada do método `limpar()` no bloco `finally` é indispensável para combater vazamento de estados (context leaks) persistentes nas threads.

---

## Níveis e Governança

### Sensibilidade de Dados e Mascaramento
Sob qualquer circunstância (LGPD/GDPR), dados sensíveis (Pass, Tokens, CC, CPF) devem ser retidos ou encobertos. A estrutura apoia o comportamento usando `SanitizadorDados.sanitizar(chave, valor)`, comissionado transversalmente de forma passiva para reescrever ocorrências de `****` e mascarar ameaças latentes.

### Gestão de Níveis de Severidade
A escolha do nível de log deve ser determinística, não subjetiva:

| Nível | Quando usar | Em produção? |
|---|---|---|
| `TRACE` | Diagnóstico de baixo nível (entradas/saídas de métodos) | Nunca |
| `DEBUG` | Fluxos internos, decisões condicionais | Não por padrão — ativável dinamicamente por pacote |
| `INFO` | Operações que alteram estado (persistência, autenticação, chamadas externas) | Sempre |
| `WARN` | Situações anômalas recuperáveis (*fallbacks*, validações rejeitadas) | Sempre |
| `ERROR` | Falhas reais que impedem o cumprimento do contrato da operação | Sempre |
| `FATAL` | Falhas que tornam a aplicação incapaz de continuar | Sempre |

### Anti-Padrões Obrigatórios
- **Evitar o Log-and-Throw (*Double Logging*):** Registre sua exceção localmente OU repasse, mas jamais os dois sem agregar contexto adicional.
- **Trace Integridade Integral:** Ao invocar instâncias como `.erro(log, excecao)`, repasse todo o objeto e não omita partes através de `ex.getMessage()`. Identificar o traço (stacktrace) consolidado no JSON é essencial.
- **Guarda de Nível para Computações Custosas:** Proteja serializações pesadas com `if (log.isDebugEnabled())` para evitar overhead com nível desabilitado em produção.

---

## Conceitos Fora do Escopo do Projeto

A listagem a seguir isola e desqualifica abordagens comumente documentadas em manuais de observabilidade, mas que não se alinham ao momento arquitetural ou tecnológico do sub-projeto (`logging-quarkus`) da Studio Observability.

### 1. APIs Nativas e Fluent SLF4J 2.x Isoladas
A manipulação de mapas log-json com o método manual `logger.atInfo().addKeyValue("chave", "valor")` reflete as premissas da biblioteca SLF4J, no entanto o projeto adotou categoricamente a DSL validada por compilação `LogSistematico / LogEtapas`. A persistência no padrão SLF4J quebra o contrato da sintaxe preestabelecida de etapas guiadas e a consolidação de log via `LogEvento`.

### 2. Output com Formato GELF / Graylog Nativo
Transmissões focadas em configuração explícita de `quarkus.log.handler.gelf.enabled` não refletem o formato de observabilidade desacoplada deste SDK. Essa infraestrutura emite anomalias ao serializar JSONs aninhados e transfere a responsabilidade da extração (sidecar, Fluentd, OpenTelemetry Collector) para o serviço embarcado, aumentando o acoplamento do sistema de envio interno. A premissa do projeto é gerar `stdout` JSON.

### 3. Log Rotation em Arquivos (File Output Storage)
Manter lógicas de "Log Rotation" ou controle configuracional da JVM para escrita simultânea de discos vai contra os modelos imutáveis de orquestradores como Kubernetes (e abordagens Container-Native). Operadores de arquivos estão em desuso: aplicações devem exportar logs contínuos via sys-out (`JBoss LogManager JsonFormatter`), e a governança da deleção e armazenamento em buckets/volumes não é escopo dessa biblioteca ou de seu arquivo `application.properties`.

### 4. Construção Artesanal de CDI de Logging (`@AroundInvoke`)
Documentos anteriores possuíam exemplos em que o desenvolvedor modelava interceptores para demarcar metadados de classe via `MDC.put()`. Embora didático, o modelo atual abrange entidades coesas e automáticas do ecossistema — com suporte a extração pela API de correlação nativa e OTel. Implementar interceptores manuais de Logging abre margem de falha grave de propagação em métodos reativos. No padrão do projeto, o fluxo do `GerenciadorContextoLog` e extensões de instrumentação cumprem a coleta, sem acoplar regras operacionais artesanais.

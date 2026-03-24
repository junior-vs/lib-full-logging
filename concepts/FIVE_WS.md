# O Framework 5W1H

> _"Quando você aplica o 5W1H, seus logs deixam de ser uma parede de texto e se tornam um banco de dados consultável."_

---

## Visão Geral

O framework investigativo 5W1H (emprestado do jornalismo e adaptado para a engenharia de precisão) é o modelo fundacional de observabilidade neste projeto (`logging-quarkus`). Ele estabelece que toda entrada de log deve ser capaz de responder a seis dimensões para evitar que um log seja apenas "uma pista sem um caso":

| Dimensão | Pergunta | Implementação via DSL | Exemplos de Campos Resultantes |
| --- | --- | --- | --- |
| **Who** (Quem) | Quem desencadeou a ação? | Injetado automaticamente | `userId`, `hostname`, IP |
| **What** (O quê) | O que exatamente ocorreu? | `.registrando("evento")` | `message`, `level` |
| **When** (Quando) | Quando ocorreu a ação? | Injetado automaticamente | `@timestamp` (UTC, ms precisão) |
| **Where** (Onde) | Em que serviço e fluxo? | `.em(Classe.class, "metodo")` | `traceId`, `requestId`, `spanId` |
| **Why** (Por quê) | Qual a motivação da ação? | `.porque("motivo de negócio")` | `log_motivo` |
| **How** (Como) | Por qual canal/meio chegou? | `.como("canal")` | `log_canal` |

Sem uma abordagem que englobe essas dimensões, os sistemas falham silenciosamente em produção.

---

## 1. Who — Identidade

A dimensão _Who_ responde: **quem está envolvido neste evento e em qual máquina?**

O preenchimento adequado distingue uma falha sistêmica sistêmica de um erro pontual isolado a um único cliente.

### Identidade do Usuário
Capturado automaticamente pela aplicação (`GerenciadorContextoLog`), evita o repasse explícito em cada etapa:
```json
{
  "userId": "usr_99812A"
}
```

### Identidade da Infraestrutura
Em arquiteturas distribuídas e nuvens baseadas em microserviços, o _Who_ também compreende o nó operacional:
```json
{
  "hostname": "k8s-pod-auth-b7d8",
  "pid": 1
}
```

---

## 2. What — Descrição do Evento

Responde a: **o que especificamente aconteceu?**

Textos vagos são os maiores inimigos da recuperação acelerada (MTTR). Logs em texto estáticos ocultam os identificadores da entidade tratada.

**O que evitar:** `log.error("Erro no processamento");`
**A Abordagem Correta (via pacote `log/dsl`):**
```java
LogSistematico
    .registrando("Falha durante liquidação de nota") // ← What
    // ...
```

A ação também abarca eventos de uso e negócio (ex: `ORDER_COMPLETED`), garantindo uma visão holística entre incidentes e fluxo de comportamento. E, finalmente, inclui a integridade estrutural da entidade em si (como as exceções não suprimidas passadas em `.erro(log, e)`).

---

## 3. When — A Linha do Tempo

Responde a: **exatamente quando este evento tomou forma?**

Regras cruciais:
- **Precisão de milissegundos:** Eventos distribuídos concorrentes não podem ser enfileirados apenas em segundos.
- **Fuso horário UTC:** Fusos horários relativos distorcem investigações multi-regionais de rastreamento de causa e efeito.

O container Quarkus e o wrapper de JSON (`quarkus-logging-json`) realizam a inserção deste campo (`@timestamp`) passivamente mediante sincronizada injeção do OpenTelemetry. É um requisito subjacente que os servidores possuam relógios sincronizados via protocolo NTP (Network Time Protocol).

---

## 4. Where — Topologia e Localização

A dimensão _Where_ revela: **onde na cadeia lógica do código e na malha da infraestrutura esse evento surgiu?**

Essa resposta cruza múltiplas escalas de abstração:

### Na Origem Concreta do Código
Sustentado via `.em(MinhaClasse.class, "meuMetodo_xyz")`, previne elucidações estúpidas e demoradas rastreando fluxos de classe por inspeção textual. E quando uma `Exception` se eleva, a própria trilha (*stacktrace*) e causas base entram no invólucro para determinar as dependências culposas.

### Na Vida Útil da Interação (Traces e IDs)
Em sistemas concorrentes que roteiam paralelamente solicitações no mesmo contêiner:
```json
{ 
  "requestId": "a3f9c2d1...",
  "traceId": "7d2c8e4f1a3b9c2d4bf92f3577b34da6",
  "spanId": "a3ce929d0e0e4736"
}
```
Isolando os marcadores via **OpenTelemetry** combinados ao Servlet Filter para solicitações isoladas, rastreamos todas as passagens da rota lógica do _Where_.

---

## 5. Why e How — Motivação e Canal

Essas são as narrativas emergentes que justificam o registro de um estado mutável e indicam a procedência arquitetural. Elas diferem a arquitetura madura da superficial:

Ao declarar o log com `.porque("...")`, os operadores e o corpo diretivo entendem qual o processo negocial motivou a chamada sem recorrer à varredura complexa de DB, enquanto com `.como("...")` rastreiam a via de entrada lógica (Filas mensageiras, rotas REST, processamentos de Lote cron).

---

## 6. Além da Correção de Erros (Debugging)

Um registro com conformidade rígida dos preceitos do 5W1H destranca casos de usos como:

- **Estatísticas em Tempo Real e KPIs (Analytics)**: Identificação ágil de taxas de abandono atrelando tipos de transação às regiões do locatário (Tenants).
- **Trilha Auditável e Probatória (Conformidade)**: Aferição precisa perante auditoria jurídica do rastreio de um repasse mal sucedido a um GateWay de parceiro externo via Timestamp idêntico e Carga original (Payload).
- **Mitigação Ativa via Anomalias (Alerting)**: Detectores ML observam anomalias abruptas em picos sem precedentes por exemplo do status `LOGIN_FAILED` para mitigar ataques Brute-Force.

---

## Conceitos Fora do Escopo do Projeto

A listagem a seguir isola alusões históricas da documentação técnica primária que não se inserem mais no *guideline* de produção deste repositório da Studio Observability.

### 1. Nomenclatura "OBSERVA4J"
Iterações anteriores desta documentação referiam-se a uma sigla de pacote ou framework chamado "OBSERVA4J". Esta conotação não se aplica, visto que a arquitetura corporativa está consolidada unicamente em torno dos submódulos do `logging-quarkus`.

### 2. Injeção de Contexto por CDI `@RequestScoped ObservabilityContext`
Práticas descritas sugeriam que a dimensão do `Who` e `Where` seria garantida pela injeção manual de um escopo JAX-RS anotado mediante `@RequestScoped ObservabilityContext`. Embora correta para ecossistemas legados, essa estrutura foi suprimida e trocada definitivamente por `LogContexto` e a orquestração via ThreadLocal subjacente operada por `GerenciadorContextoLog` nativamente via filtro de Quarkus e propagações do OpenTelemetry.

### 3. Log de Workers por "Queue / Background Jobs" Mapeados Automaticamente
Modelos prévios forçavam a dependência intrínseca de variáveis como `queue_name` ou `worker_class` como pilares essenciais no formato JSON de background. Considerando os módulos correntes assíncronos via SmallRye Context Propagation, as injeções de informações secundárias ocorrem via DSL de rastreio genérico (`LogSistematico.comDetalhe()`), evitando prender formalmente instâncias do core a metadados específicos de agendadores isolados (tipo Quartz ou RabbitMQ).

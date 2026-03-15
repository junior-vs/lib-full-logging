# Design

## Bugfix: `LogSistematico.erro(Throwable)`

### Contexto
`LogSistematico` já centraliza a emissão via `emitir(Logger.Level, Throwable)`. O defeito estava no terminador `erro(Throwable)`, que descartava a causa ao chamar `emitir(..., null)`.

### Decisão
Aplicar correção mínima: fazer `erro(Throwable)` encaminhar a `Throwable` recebida para `emitir(Logger.Level.ERROR, causa)`.

### Impacto
- Preserva stack trace e causa na saída do logger JBoss/Quarkus.
- Não altera a DSL pública.
- Mantém o fluxo atual baseado em MDC e `Logger.log(...)`.

### Validação
Adicionar teste unitário que captura o `ExtLogRecord` do `org.jboss.logmanager.Logger` e verifica `record.getThrown()`.


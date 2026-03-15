# Requirements

## Bugfix: preservar exceção em `LogSistematico.erro(Throwable)`

- WHEN `LogSistematico.erro(Throwable)` for chamado com uma exceção, THE SYSTEM SHALL emitir o log em nível `ERROR` anexando a mesma `Throwable` ao runtime de logging.
- IF `LogSistematico.erro(Throwable)` receber `null`, THEN THE SYSTEM SHALL continuar emitindo o log sem lançar exceção adicional.
- WHEN a correção for aplicada, THE SYSTEM SHALL manter inalterado o comportamento dos demais terminadores da DSL (`info`, `debug`, `warn`, `erroERelanca`).


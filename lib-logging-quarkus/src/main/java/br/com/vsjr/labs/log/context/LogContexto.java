package br.com.vsjr.labs.log.context;

/**
 * Snapshot imutável do contexto de correlação de uma requisição.
 *
 * <p>Produzido pelo {@link GerenciadorContextoLog} a partir do OpenTelemetry
 * e da identidade autenticada. Pode ser inspecionado em testes sem dependência
 * de infraestrutura de MDC.</p>
 *
 * <p>Usa {@code record} do Java 21: imutável, thread-safe e com
 * {@code equals/hashCode/toString} gerados sem boilerplate.</p>
 *
 * @param traceId identificador único de rastreamento distribuído (OTel)
 * @param spanId  identificador do span atual
 * @param userId  identificador do usuário autenticado, ou {@code "anonimo"}
 * @param servico nome do microsserviço
 */
public record LogContexto(
        String traceId,
        String spanId,
        String userId,
        String servico
) {

    /**
     * Contexto vazio: usado quando nenhuma requisição está ativa (ex: testes, jobs).
     */
    public static final LogContexto VAZIO = new LogContexto(null, null, "anonimo", "desconhecido");


    /**
     * Retorna {@code true} se este contexto possui rastreamento OTel válido.
     */
    public boolean temTrace() {
        return traceId != null && !traceId.isBlank();
    }
}

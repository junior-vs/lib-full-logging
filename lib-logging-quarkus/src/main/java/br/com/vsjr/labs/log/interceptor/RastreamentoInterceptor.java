package br.com.vsjr.labs.log.interceptor;

import br.com.vsjr.labs.log.annotations.Rastreado;
import br.com.vsjr.labs.log.dsl.LogSistematico;
import br.com.vsjr.labs.log.tracing.GerenciadorRastreamento;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.jboss.logging.MDC;

/**
 * CDI Interceptor ativado por {@link Rastreado}.
 *
 * <p>Para cada método interceptado:</p>
 * <ol>
 *   <li>Captura o {@code spanId} do span pai do MDC</li>
 *   <li>Cria um Child Span OTel e atualiza o MDC com o novo {@code spanId}</li>
 *   <li>Executa o método — o {@link LogInterceptor} registra a localização neste ponto</li>
 *   <li>Em caso de exceção, marca o span como {@code ERROR}</li>
 *   <li>No {@code finally}: encerra o span e restaura o {@code spanId} do pai no MDC</li>
 * </ol>
 *
 * <p><b>Sobre a prioridade:</b> {@code APPLICATION - 10} garante execução antes do
 * {@link LogInterceptor} ({@code APPLICATION}). Isso faz com que o {@code spanId}
 * do filho já esteja no MDC quando o {@link LogInterceptor} registrar a localização
 * técnica do método.</p>
 *
 * <p><b>Sobre o beans.xml:</b> não necessário — o ArC descobre e ativa este interceptor
 * automaticamente via índice Jandex durante o build do Quarkus.</p>
 */
@Rastreado
@Interceptor
@Priority(Interceptor.Priority.APPLICATION - 10)
public class RastreamentoInterceptor {


    GerenciadorRastreamento gerenciador;

    public RastreamentoInterceptor(GerenciadorRastreamento gerenciador) {
        this.gerenciador = gerenciador;
    }


    /**
     * Intercepta o método anotado com {@link Rastreado} e gerencia o ciclo de vida do span.
     *
     * @param contexto contexto CDI da invocação
     * @return resultado do método interceptado
     * @throws Exception qualquer exceção lançada pelo método interceptado
     */
    @AroundInvoke
    public Object rastrear(InvocationContext contexto) throws Exception {
        var metodo = contexto.getMethod();
        var classe = metodo.getDeclaringClass().getSimpleName();
        var nomeMetodo = metodo.getName();
        var nomeSpan = classe + "." + nomeMetodo;

        // Salva o spanId do pai antes de criar o Child Span para restaurar no finally
        var spanIdPai = (String) MDC.get("spanId");

        var contextoSpan = gerenciador.iniciar(nomeSpan, contexto);
        try {
            return contexto.proceed();
        } catch (Exception e) {
            gerenciador.marcarErro(contextoSpan, e);
            throw e;
        } finally {
            try {
                gerenciador.encerrar(contextoSpan, spanIdPai);
            } catch (Exception otelEx) {
                LogSistematico.registrando("Falha ao encerrar span OTel")
                        .em(RastreamentoInterceptor.class, "rastrear")
                        .porque("Exceção durante encerramento de span OTel")
                        .como("Interceptor de rastreamento")
                        .erro(otelEx);
            }
        }
    }
}

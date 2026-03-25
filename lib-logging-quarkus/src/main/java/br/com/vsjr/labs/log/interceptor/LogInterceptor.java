package br.com.vsjr.labs.log.interceptor;


import br.com.vsjr.labs.log.annotations.Logged;
import br.com.vsjr.labs.log.context.GerenciadorContextoLog;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;



/**
 * CDI Interceptor ativado por {@link Logged}.
 *
 * <p>Para cada método interceptado:</p>
 * <ol>
 *   <li>Registra classe e método no MDC ({@code Where} automático)</li>
 *   <li>Executa o método de negócio</li>
 *   <li>Registra duração em nanosegundos via Micrometer Timer</li>
 *   <li>Limpa os campos de localização do MDC no {@code finally}</li>
 * </ol>
 *
 * <p><b>Sobre a limpeza do MDC:</b> o interceptor remove apenas os campos
 * que ele mesmo inseriu ({@code classe} e {@code metodo}). Os campos de
 * correlação da requisição ({@code traceId}, {@code userId}) são responsabilidade
 * do {@link br.com.vsjr.labs.log.filtro.LogContextoFiltro} e permanecem
 * intactos durante toda a execução da requisição.</p>
 *
 * <p><b>Sobre o Micrometer:</b> o {@link MeterRegistry} é injetado pelo CDI.
 * As métricas são expostas automaticamente em {@code /q/metrics} sem
 * configuração adicional.</p>
 */
@Logged
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
public class LogInterceptor {

    
    MeterRegistry meterRegistry;

    GerenciadorContextoLog gerenciador;

    public LogInterceptor(MeterRegistry meterRegistry, GerenciadorContextoLog gerenciador) {
        this.meterRegistry = meterRegistry;
        this.gerenciador = gerenciador;
    }

    @AroundInvoke
    public Object interceptar(InvocationContext contexto) throws Exception {
        var metodo = contexto.getMethod();
        var classe = metodo.getDeclaringClass().getSimpleName();
        var nomeMetodo = metodo.getName();

        // Registra localização técnica no MDC (dimensão Where)
        gerenciador.registrarLocalizacao(classe, nomeMetodo);

        var cronometro = Timer.start(meterRegistry);

        try {
            return contexto.proceed();

        } catch (Exception e) {
            // Registra falha como counter separado para alertas no Prometheus
            meterRegistry.counter("metodo.falha",
                    "classe", classe,
                    "metodo", nomeMetodo,
                    "excecao", e.getClass().getSimpleName()
            ).increment();
            throw e;

        } finally {
            // Registra duração — disponível em /q/metrics como histogram
            cronometro.stop(
                    Timer.builder("metodo.execucao")
                            .tag("classe", classe)
                            .tag("metodo", nomeMetodo)
                            .publishPercentileHistogram()        // p50, p95, p99 automáticos
                            .register(meterRegistry)
            );

            // Remove apenas os campos de localização — não toca no contexto da requisição
            org.jboss.logging.MDC.remove("classe");
            org.jboss.logging.MDC.remove("metodo");
        }
    }
}
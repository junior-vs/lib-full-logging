package br.com.vsjr.labs.observability.context.enriquecedor.logs;

import br.com.vsjr.labs.observability.utils.LocalizacaoMetodo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.interceptor.InvocationContext;
import org.jboss.logging.MDC;

import java.util.Set;

/**
 * Enriquecedor obrigatório — localização técnica da invocação interceptada.
 *
 * <p>Prioridade {@code 10}: executa primeiro na cadeia, garantindo que os campos
 * de localização estejam no MDC antes de qualquer enriquecedor de negócio.</p>
 *
 * <p>Campos adicionados:</p>
 * <ul>
 *   <li>{@code classe} — nome simples da classe interceptada</li>
 *   <li>{@code metodo} — nome do método interceptado</li>
 * </ul>
 */
@ApplicationScoped
public class MetadadosEnriquecedorContexto implements EnriquecedorContexto {

    @Override
    public void enriquecer(InvocationContext contexto) {
        var localizacao = LocalizacaoMetodo.extrair(contexto);
        MDC.put("classe", localizacao.classeSimples());
        MDC.put("metodo", localizacao.metodo());
    }

    @Override
    public Set<String> chavesMdc() {
        return Set.of("classe", "metodo");
    }

    @Override
    public int prioridade() {
        return 10;
    }
}

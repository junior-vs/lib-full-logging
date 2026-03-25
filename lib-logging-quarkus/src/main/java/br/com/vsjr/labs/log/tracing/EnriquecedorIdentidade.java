package br.com.vsjr.labs.log.tracing;

import io.opentelemetry.api.trace.Span;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.interceptor.InvocationContext;

/**
 * Enriquecedor opcional — identidade do usuário autenticado.
 *
 * <p>Prioridade {@code 20}: executa após {@link EnriquecedorMetadados}.</p>
 *
 * <p>Atributos adicionados (OTel semantic conventions):</p>
 * <ul>
 *   <li>{@code enduser.id} — nome do principal autenticado;
 *       omitido quando a requisição é anônima.</li>
 * </ul>
 *
 * <p>A injeção de {@link SecurityIdentity} é segura mesmo sem extensão de segurança
 * configurada — o Quarkus provê uma identidade anônima nesse caso, nunca {@code null}.</p>
 */
@ApplicationScoped
public class EnriquecedorIdentidade implements EnriquecedorSpan {

    @Inject
    Instance<SecurityIdentity> identidade;

    public void enriquecer(Span span, InvocationContext contexto) {
        if (identidade.isResolvable() && !identidade.get().isAnonymous()) {
            span.setAttribute("enduser.id", identidade.get().getPrincipal().getName());
        }
    }

    @Override
    public int prioridade() {
        return 20;
    }
}

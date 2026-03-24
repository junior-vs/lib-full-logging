package br.com.vsjr.labs.exemple.rest;

import br.com.vsjr.labs.log.annotations.Logged;
import br.com.vsjr.labs.log.dsl.LogSistematico;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;

@Path("/hello")
@Logged
public class HellowrdResource {

    @Inject
    HelloService helloService;

    @Path("/world")
    @GET
    public String hello() {
        LogSistematico
                .registrando("Recurso de hello")
                .em(HellowrdResource.class, "hello")
                .porque("Solicitação do recurso de hello")
                .como("API REST - GET /hello/world")
                .info();
        return helloService.sayHello();
    }

    @POST
    public Double divide(@QueryParam("va") double a, @QueryParam("vb") double b) {
        try {
            return helloService.divide(a, b);
        } catch (Throwable e) {
            LogSistematico
                    .registrando("Erro na divisão do recurso de hello")
                    .em(HellowrdResource.class, "divide")
                    .porque("Erro durante a divisão no recurso de hello")
                    .como("API REST - POST /hello")
                    .erro(e);
            return 0d;
        }
    }

}

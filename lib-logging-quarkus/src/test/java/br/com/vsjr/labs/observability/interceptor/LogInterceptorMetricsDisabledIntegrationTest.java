package br.com.vsjr.labs.observability.interceptor;

import br.com.vsjr.labs.example.rest.HelloService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class LogInterceptorMetricsDisabledIntegrationTest {

    @Inject
    HelloService helloService;

    @Test
    void deveExecutarFluxoSemErroQuandoMetricasEstaoDesabilitadas() {
        var resultado = helloService.sayHello();

        assertEquals("Hello World!", resultado);
    }
}

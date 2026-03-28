package br.com.vsjr.labs.observability.interceptor;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class MetricsEnabledTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.micrometer.enabled", "true",
                "quarkus.micrometer.export.prometheus.enabled", "true",
                "quarkus.micrometer.binder.jvm", "false",
                "quarkus.micrometer.binder.system", "false",
                "quarkus.micrometer.binder.http-server.enabled", "false"
        );
    }
}

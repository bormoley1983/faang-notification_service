package observability;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PrometheusConfigurationTest {

    @Test
    void prometheusRegistryAndRestrictedManagementEndpointAreConfigured() throws IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        boolean registryPresent = isPresent(
                classLoader,
                "io.micrometer.prometheusmetrics.PrometheusMeterRegistry"
        ) || isPresent(
                classLoader,
                "io.micrometer.prometheus.PrometheusMeterRegistry"
        );
        assertTrue(registryPresent, "Prometheus MeterRegistry must be on the runtime classpath");

        try (InputStream stream = classLoader.getResourceAsStream("application.yaml")) {
            assertNotNull(stream, "application.yaml must be available");
            String yaml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(yaml.contains("port: ${MANAGEMENT_SERVER_PORT:9090}"));
            assertTrue(yaml.contains("include: health,info,prometheus"));
            assertTrue(yaml.contains("show-details: never"));
            assertTrue(yaml.contains("METRICS_ENVIRONMENT"));
            assertTrue(yaml.contains("METRICS_CLUSTER"));
        }
    }

    private boolean isPresent(ClassLoader classLoader, String className) {
        try {
            Class.forName(className, false, classLoader);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}


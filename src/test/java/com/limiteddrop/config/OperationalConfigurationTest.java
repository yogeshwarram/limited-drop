package com.limiteddrop.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalConfigurationTest {
    @Test
    void bindsFailFastDependencyAndRequestDefaults() throws Exception {
        StandardEnvironment environment = environment();

        assertThat(environment.getProperty("spring.datasource.hikari.maximum-pool-size", Integer.class)).isEqualTo(20);
        assertThat(environment.getProperty("spring.datasource.hikari.minimum-idle", Integer.class)).isEqualTo(5);
        assertThat(environment.getProperty("spring.datasource.hikari.connection-timeout", Long.class)).isEqualTo(2_000L);
        assertThat(environment.getProperty("spring.datasource.hikari.validation-timeout", Long.class)).isEqualTo(1_000L);
        assertThat(environment.getProperty("spring.datasource.hikari.data-source-properties.connectTimeout", Integer.class)).isEqualTo(1_000);
        assertThat(environment.getProperty("spring.datasource.hikari.data-source-properties.socketTimeout", Integer.class)).isEqualTo(3_000);
        assertThat(environment.getProperty("server.tomcat.threads.max", Integer.class)).isEqualTo(50);
        assertThat(environment.getProperty("server.tomcat.accept-count", Integer.class)).isEqualTo(100);
        assertThat(environment.getProperty("spring.data.redis.connect-timeout", Duration.class)).isEqualTo(Duration.ofMillis(500));
        assertThat(environment.getProperty("spring.data.redis.timeout", Duration.class)).isEqualTo(Duration.ofMillis(250));
        assertThat(environment.getProperty("spring.rabbitmq.connection-timeout", Duration.class)).isEqualTo(Duration.ofSeconds(1));
        assertThat(environment.getProperty("server.shutdown")).isEqualTo("graceful");
        assertThat(environment.getProperty("spring.lifecycle.timeout-per-shutdown-phase", Duration.class)).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void readinessDependsOnMysqlButLivenessDoesNotDependOnExternalServices() throws Exception {
        StandardEnvironment environment = environment();

        assertThat(environment.getProperty("management.endpoint.health.group.liveness.include"))
                .isEqualTo("livenessState");
        assertThat(environment.getProperty("management.endpoint.health.group.readiness.include"))
                .isEqualTo("readinessState,db");
    }

    private StandardEnvironment environment() throws Exception {
        var source = new YamlPropertySourceLoader().load("application",
                new FileSystemResource("src/main/resources/application.yml")).getFirst();
        StandardEnvironment environment = new StandardEnvironment();
        environment.setConversionService(new ApplicationConversionService());
        environment.getPropertySources().addFirst(source);
        return environment;
    }
}

package com.limiteddrop.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class InfrastructureConfigurationTest {
    @Test
    void declaresDurableEventTopology() {
        ReservationProperties properties = new ReservationProperties();
        properties.getOutbox().setExchange("events.test");
        InfrastructureConfiguration configuration = new InfrastructureConfiguration();
        TopicExchange exchange = configuration.dropEventsExchange(properties);
        Queue queue = configuration.auditQueue();
        Binding binding = configuration.auditBinding(queue, exchange);

        assertThat(exchange.getName()).isEqualTo("events.test");
        assertThat(exchange.isDurable()).isTrue();
        assertThat(queue.getName()).isEqualTo("drop.events.audit");
        assertThat(queue.isDurable()).isTrue();
        assertThat(binding.getRoutingKey()).isEqualTo("hold.#");
    }

    @Test
    void cacheErrorHandlerSwallowsAllCacheOperations() {
        var handler = new InfrastructureConfiguration().errorHandler();
        var cache = mock(org.springframework.cache.Cache.class);
        RuntimeException error = new RuntimeException("redis down");

        handler.handleCacheGetError(error, cache, "key");
        handler.handleCachePutError(error, cache, "key", "value");
        handler.handleCacheEvictError(error, cache, "key");
        handler.handleCacheClearError(error, cache);
    }

    @Test
    void createsCacheManagerAndExposesOptionalConfigurerHooks() {
        InfrastructureConfiguration configuration = new InfrastructureConfiguration();
        assertThat(configuration.clock()).isNotNull();
        assertThat(configuration.cacheManager(mock(org.springframework.data.redis.connection.RedisConnectionFactory.class)))
                .isNotNull();
        assertThat(configuration.cacheManager()).isNull();
        assertThat(configuration.cacheResolver()).isNull();
        assertThat(configuration.keyGenerator()).isNull();
    }
}

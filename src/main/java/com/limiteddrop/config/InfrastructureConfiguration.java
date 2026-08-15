package com.limiteddrop.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class InfrastructureConfiguration implements CachingConfigurer {
    @Bean
    Clock clock() { return Clock.systemUTC(); }

    @Bean
    CacheManager cacheManager(org.springframework.data.redis.connection.RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new JdkSerializationRedisSerializer()));
        return RedisCacheManager.builder(connectionFactory).cacheDefaults(config).build();
    }

    /** A cache outage must degrade to MySQL, never make reservations or reads unavailable. */
    @Override
    public CacheErrorHandler errorHandler() {
        return new SimpleCacheErrorHandler() {
            @Override public void handleCacheGetError(RuntimeException exception, org.springframework.cache.Cache cache, Object key) { }
            @Override public void handleCachePutError(RuntimeException exception, org.springframework.cache.Cache cache, Object key, Object value) { }
            @Override public void handleCacheEvictError(RuntimeException exception, org.springframework.cache.Cache cache, Object key) { }
            @Override public void handleCacheClearError(RuntimeException exception, org.springframework.cache.Cache cache) { }
        };
    }

    @Override public CacheManager cacheManager() { return null; }
    @Override public CacheResolver cacheResolver() { return null; }
    @Override public KeyGenerator keyGenerator() { return null; }

    @Bean
    TopicExchange dropEventsExchange(ReservationProperties properties) { return new TopicExchange(properties.getOutbox().getExchange(), true, false); }
    @Bean
    Queue auditQueue() { return new Queue("drop.events.audit", true); }
    @Bean
    Binding auditBinding(Queue auditQueue, TopicExchange dropEventsExchange) { return BindingBuilder.bind(auditQueue).to(dropEventsExchange).with("hold.#"); }
}

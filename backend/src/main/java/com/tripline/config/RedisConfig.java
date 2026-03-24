package com.tripline.config;

import com.tripline.listeners.SeatLockExpiryListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import java.time.Duration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.URI;

import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.Cache;

/**
 * Redis configuration with:
 * 1. Keyspace notifications enabled for key-expiry events (Ex pattern)
 * 2. A MessageListenerContainer wired to SeatLockExpiryListener
 * 3. @EnableScheduling for the SeatLockSweeper fallback
 */
@Configuration
@EnableScheduling
@EnableCaching
public class RedisConfig implements CachingConfigurer {

    @Value("${spring.data.redis.url:}")
    private String redisUrl;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();

        if (redisUrl != null && !redisUrl.isEmpty()) {
            URI uri = URI.create(redisUrl);
            config.setHostName(uri.getHost());
            config.setPort(uri.getPort() == -1 ? 6379 : uri.getPort());
            String userInfo = uri.getUserInfo();
            if (userInfo != null && userInfo.contains(":")) {
                config.setPassword(userInfo.split(":")[1]);
            }
        } else {
            // Fallback for local development if UPSTASH_REDIS_URL is not set
            config.setHostName("localhost");
            config.setPort(6379);
        }

        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.setUseSsl(redisUrl != null && redisUrl.startsWith("rediss://"));
        return factory;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        GenericJackson2JsonRedisSerializer serializer = jsonSerializer();
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }

    private GenericJackson2JsonRedisSerializer jsonSerializer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        // Required for polymorphic type handling (saving the class name in JSON)
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        GenericJackson2JsonRedisSerializer serializer = jsonSerializer();

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }

    /**
     * Wire SeatLockExpiryListener to receive Redis keyspace expiry events.
     *
     * The topic pattern "__keyevent@*__:expired" fires whenever ANY key expires
     * across all Redis databases. The listener filters on the "seat_lock:" prefix.
     *
     * IMPORTANT: Redis must have keyspace notifications enabled. This is done
     * either:
     * - Via redis.conf: notify-keyspace-events Ex
     * - Or programmatically at runtime (see afterPropertiesSet below)
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            SeatLockExpiryListener seatLockExpiryListener) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // Listen for key-expiry events on all databases
        MessageListenerAdapter adapter = new MessageListenerAdapter(seatLockExpiryListener, "onMessage");
        container.addMessageListener(adapter, new PatternTopic("__keyevent@*__:expired"));

        return container;
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                // Ignore Redis read errors and continue to method execution (e.g. timeout)
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                // Ignore Redis write errors
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                // Ignore Redis evict errors
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                // Ignore Redis clear errors
            }
        };
    }
}

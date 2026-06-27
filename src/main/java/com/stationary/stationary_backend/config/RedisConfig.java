package com.stationary.stationary_backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * RedisConfig — Configures Redis connection, serialization, and cache TTLs.
 *
 * ──────────────────────────────────────────────────────────
 * WHY configure serialization manually?
 * ──────────────────────────────────────────────────────────
 * By default, Spring serializes cache values using Java's
 * built-in serialization (JdkSerializationRedisSerializer).
 * This produces binary data that:
 *   1. Is unreadable in Redis CLI (hard to debug)
 *   2. Breaks if you rename a class or add/remove a field
 *   3. Is tied to the Java version
 *
 * JSON serialization (GenericJackson2JsonRedisSerializer):
 *   1. Human-readable in Redis CLI: SET product:slug:blue-pen {"name":"Blue Pen",...}
 *   2. Survives field additions (new fields just appear/disappear)
 *   3. Language-agnostic (a future Go service could read it too)
 *
 * ──────────────────────────────────────────────────────────
 * WHY two beans (RedisTemplate + RedisCacheManager)?
 * ──────────────────────────────────────────────────────────
 * RedisTemplate → manual Redis operations (GET, SET, DEL, SCAN)
 *   Used when you need fine-grained control over cache keys
 *   Example: invalidating all "products:page:*" keys on product update
 *
 * RedisCacheManager → powers @Cacheable, @CacheEvict annotations
 *   Used for simple put/get/evict by cache name + key
 *   Example: @Cacheable("categories") on CategoryService.getAll()
 *
 * TRADEOFF: Two beans = more config upfront, but you have full
 * control over both simple and complex caching patterns.
 *
 * ──────────────────────────────────────────────────────────
 * FUTURE:
 * ──────────────────────────────────────────────────────────
 * v2 could cache customer sessions here too.
 * Just add a new cache name + TTL to the cacheConfigurations map.
 */
@Configuration
public class RedisConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    @Value("${spring.data.redis.url}")
    private String redisUrl;

    /**
     * RedisTemplate<String, Object> — for manual cache operations.
     *
     * Key serializer:   StringRedisSerializer → keys are plain strings
     *                   "product:slug:blue-pen" not binary garbage
     * Value serializer: GenericJackson2JsonRedisSerializer → JSON values
     *
     * WHY String keys? Readable in Redis CLI, easy to pattern-match
     * with SCAN "product:*" for bulk invalidation.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule()); // handles Java 8+ date types
        // activateDefaultTyping tells Jackson to embed the class name in JSON.
        // WHY? So on deserialization, Jackson knows what class to instantiate.
        // Without this, you'd get a LinkedHashMap instead of your DTO object.
        objectMapper.activateDefaultTyping(
                objectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL
        );

        var jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);
        var stringSerializer = new StringRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();

        return template;
    }

    /**
     * RedisCacheManager — powers @Cacheable / @CacheEvict annotations.
     *
     * Cache names and their TTLs match the architecture plan:
     *   "products"   — paginated list results   — 10 minutes
     *   "product"    — single product by slug   — 30 minutes
     *   "categories" — full category list       — 60 minutes
     *
     * WHY different TTLs?
     * Categories change rarely → long TTL (60 min) = fewer DB hits.
     * Product lists change when any product changes → shorter TTL (10 min).
     * Single product detail is requested often but changes occasionally → 30 min.
     *
     * The default config applies to any @Cacheable not listed here.
     * We set it to 10 minutes as a safe fallback.
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.activateDefaultTyping(
                objectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL
        );

        var jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
                .disableCachingNullValues(); // WHY? Caching null means cache misses stay cached.
                                             // If a product doesn't exist, we don't want Redis
                                             // to cache that "null" and serve it for 30 min.

        // Per-cache TTL overrides
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        cacheConfigurations.put("product",
                defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigurations.put("products",
                defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigurations.put("categories",
                defaultConfig.entryTtl(Duration.ofMinutes(60)));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis GET error for key '{}' in cache '{}': {}", key, cache.getName(), exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Redis PUT error for key '{}' in cache '{}': {}", key, cache.getName(), exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis EVICT error for key '{}' in cache '{}': {}", key, cache.getName(), exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Redis CLEAR error in cache '{}': {}", cache.getName(), exception.getMessage());
            }
        };
    }
}

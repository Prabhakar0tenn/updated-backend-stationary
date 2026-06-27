package com.stationary.stationary_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import com.stationary.stationary_backend.config.AppProperties;

/**
 * StationaryBackendApplication — The entry point.
 *
 * @SpringBootApplication is a shortcut for three annotations:
 *   @SpringBootConfiguration — marks this as the config root
 *   @EnableAutoConfiguration — lets Spring Boot auto-wire beans from classpath
 *   @ComponentScan           — scans this package and all sub-packages for @Component beans
 *
 * @EnableCaching — REQUIRED to activate @Cacheable, @CacheEvict, @CachePut.
 *   Without this annotation, those annotations on your service methods
 *   DO NOTHING — a very common fresher mistake.
 *   It's like enabling a feature switch. The feature exists in the
 *   dependency but is off by default.
 *
 * @EnableConfigurationProperties — Activates @ConfigurationProperties beans.
 *   Tells Spring to bind AppProperties to the app.* YAML section.
 *   Without this, AppProperties fields would all be null.
 */
@SpringBootApplication
@EnableCaching
@EnableConfigurationProperties(AppProperties.class)
public class StationaryBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(StationaryBackendApplication.class, args);
    }
}

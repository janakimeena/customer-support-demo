package com.example.support.config;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Java-based bean configuration. Use {@code @Bean} methods for objects you don't own (JDK or library
 * classes) and therefore can't annotate with {@code @Component}.
 */
@Configuration(proxyBeanMethods = false)
public class CustomerConfig {

    /**
     * Injected into the services instead of calling Instant.now(), so tests can pin the time. Ticks in whole
     * microseconds because that is what PostgreSQL stores: otherwise a freshly created row would report
     * nanoseconds that change the next time it is read back.
     */
    @Bean
    public Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.of(1, ChronoUnit.MICROS));
    }
}

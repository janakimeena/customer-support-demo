package com.example.support.customer.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Java-based bean configuration. Use {@code @Bean} methods for objects you don't own (JDK or library
 * classes) and therefore can't annotate with {@code @Component}.
 */
@Configuration(proxyBeanMethods = false)
public class CustomerConfig {

    /** Injected into CustomerService instead of calling Instant.now(), so tests can pin the time. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

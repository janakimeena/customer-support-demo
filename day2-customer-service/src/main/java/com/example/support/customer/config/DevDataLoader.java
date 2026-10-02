package com.example.support.customer.config;

import com.example.support.customer.domain.Customer;
import com.example.support.customer.service.CustomerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Seeds the customers referenced by the Day 1 ticket CSV files (C-100 .. C-103). This bean only exists when
 * the {@code dev} profile is active; in {@code prod} the store starts empty.
 */
@Component
@Profile("dev")
public class DevDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataLoader.class);

    private final CustomerService customerService;

    public DevDataLoader(CustomerService customerService) {
        this.customerService = customerService;
    }

    @Override
    public void run(ApplicationArguments args) {
        customerService.create("Asha Rao", "asha.rao@example.com", Customer.Tier.PREMIUM);
        customerService.create("Ben Carter", "ben.carter@example.com", null);
        customerService.create("Chen Wei", "chen.wei@example.com", null);
        customerService.create("Dana Lopez", "dana.lopez@example.com", Customer.Tier.PREMIUM);
        log.info("dev profile: seeded {} sample customers", customerService.findAll().size());
    }
}

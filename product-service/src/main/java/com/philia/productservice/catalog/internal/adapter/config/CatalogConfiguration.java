package com.philia.productservice.catalog.internal.adapter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class CatalogConfiguration {

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }
}

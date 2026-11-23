package com.example.ecommerce.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Enables @Async methods (e.g. sending emails), run on Spring Boot's applicationTaskExecutor. */
@Configuration
@EnableAsync
public class AsyncConfig {
}

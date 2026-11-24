package com.example.ecommerce.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables @Scheduled jobs, such as cancelling unpaid orders that expired. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}

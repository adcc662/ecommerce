package com.example.ecommerce.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Limits on unpaid orders, which hold stock from checkout until they are paid or cancelled.
 *
 * @param pendingTtl        how long an unpaid order keeps its stock before it is cancelled (ORDER_PENDING_TTL)
 * @param maxPendingPerUser unpaid orders a customer may have open at once (ORDER_MAX_PENDING_PER_USER)
 */
@Validated
@ConfigurationProperties("app.orders")
public record OrderProperties(
        @NotNull Duration pendingTtl,
        @Min(1) int maxPendingPerUser) {
}

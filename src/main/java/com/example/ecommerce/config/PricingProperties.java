package com.example.ecommerce.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/**
 * Flat pricing rules applied at checkout.
 *
 * @param taxRate     fraction of the subtotal, e.g. 0.0825 for 8.25% (TAX_RATE)
 * @param shippingFee flat fee per order (SHIPPING_FEE)
 */
@Validated
@ConfigurationProperties("app.pricing")
public record PricingProperties(
        @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal taxRate,
        @NotNull @DecimalMin("0") BigDecimal shippingFee) {
}

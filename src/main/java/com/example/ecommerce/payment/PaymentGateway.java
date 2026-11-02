package com.example.ecommerce.payment;

import com.example.ecommerce.models.entity.Order;
import com.example.ecommerce.models.entity.PaymentMethod;

import java.math.BigDecimal;

/**
 * Starts a payment with a provider. Implement this (for example for Stripe) and
 * register it as a {@code @Primary} bean to take precedence over {@link ManualPaymentGateway}.
 */
public interface PaymentGateway {

    PaymentInitiation initiate(Order order, PaymentMethod method, BigDecimal amount);
}

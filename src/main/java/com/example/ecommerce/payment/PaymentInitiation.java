package com.example.ecommerce.payment;

import com.example.ecommerce.models.enums.PaymentStatus;

/** What a payment provider reports when a payment is started. */
public record PaymentInitiation(String transactionId, PaymentStatus status) {
}

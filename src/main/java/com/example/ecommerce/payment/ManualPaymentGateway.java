package com.example.ecommerce.payment;

import com.example.ecommerce.models.entity.Order;
import com.example.ecommerce.models.entity.PaymentMethod;
import com.example.ecommerce.models.enums.PaymentStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Records the payment as pending without contacting a provider; an admin then
 * marks it completed, failed or refunded. A provider gateway marked {@code @Primary}
 * takes precedence over this one.
 */
@Component
public class ManualPaymentGateway implements PaymentGateway {

    @Override
    public PaymentInitiation initiate(Order order, PaymentMethod method, BigDecimal amount) {
        return new PaymentInitiation("MANUAL-" + UUID.randomUUID(), PaymentStatus.PENDING);
    }
}

package com.example.ecommerce.models.dto.response;

import com.example.ecommerce.models.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private Long id;
    private Long orderId;
    private String paymentMethod;
    private String transactionId;
    private PaymentStatus status;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime paymentDate;
}

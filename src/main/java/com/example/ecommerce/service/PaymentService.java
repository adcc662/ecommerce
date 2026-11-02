package com.example.ecommerce.service;

import com.example.ecommerce.models.dto.request.PaymentRequest;
import com.example.ecommerce.models.dto.response.PaymentMethodResponse;
import com.example.ecommerce.models.dto.response.PaymentResponse;

import java.util.List;

public interface PaymentService {
    List<PaymentMethodResponse> getPaymentMethods();

    PaymentResponse createPayment(String userEmail, Long orderId, PaymentRequest request);

    List<PaymentResponse> getPayments(String userEmail, Long orderId);

    PaymentResponse updatePaymentStatus(Long paymentId, String status);
}

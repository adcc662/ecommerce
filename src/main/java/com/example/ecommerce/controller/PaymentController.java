package com.example.ecommerce.controller;

import com.example.ecommerce.models.dto.request.PaymentRequest;
import com.example.ecommerce.models.dto.response.PaymentMethodResponse;
import com.example.ecommerce.models.dto.response.PaymentResponse;
import com.example.ecommerce.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/payment-methods")
    public ResponseEntity<List<PaymentMethodResponse>> getPaymentMethods() {
        return ResponseEntity.ok(paymentService.getPaymentMethods());
    }

    @PostMapping("/orders/{orderId}/payments")
    public ResponseEntity<PaymentResponse> createPayment(
            @PathVariable Long orderId,
            @Valid @RequestBody PaymentRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.createPayment(authentication.getName(), orderId, request));
    }

    @GetMapping("/orders/{orderId}/payments")
    public ResponseEntity<List<PaymentResponse>> getPayments(@PathVariable Long orderId, Authentication authentication) {
        return ResponseEntity.ok(paymentService.getPayments(authentication.getName(), orderId));
    }

    @PatchMapping("/payments/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaymentResponse> updatePaymentStatus(@PathVariable Long id, @RequestParam String status) {
        return ResponseEntity.ok(paymentService.updatePaymentStatus(id, status));
    }
}

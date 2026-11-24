package com.example.ecommerce.service;

import com.example.ecommerce.models.dto.request.OrderRequest;
import com.example.ecommerce.models.dto.response.OrderResponse;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderService {
    OrderResponse createOrder(OrderRequest request, String userEmail);
    OrderResponse getOrderById(Long id, String userEmail);
    List<OrderResponse> getUserOrders(String userEmail);
    OrderResponse updateOrderStatus(Long id, String status);

    /** Cancels the caller's own order while it is still unpaid (PENDING) and returns its stock. */
    OrderResponse cancelMyOrder(Long id, String userEmail);

    /** Cancels the order if it is still unpaid and was created before the cutoff; true when cancelled. */
    boolean expireUnpaidOrder(Long id, LocalDateTime cutoff);
}

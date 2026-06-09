package com.example.ecommerce.service;

import com.example.ecommerce.models.dto.request.OrderRequest;
import com.example.ecommerce.models.dto.response.OrderResponse;

import java.util.List;

public interface OrderService {
    OrderResponse createOrder(OrderRequest request, String userEmail);
    OrderResponse getOrderById(Long id, String userEmail);
    List<OrderResponse> getUserOrders(String userEmail);
    OrderResponse updateOrderStatus(Long id, String status);
}

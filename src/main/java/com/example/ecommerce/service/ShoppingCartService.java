package com.example.ecommerce.service;

import com.example.ecommerce.models.dto.response.CartItemResponse;

import java.util.List;

public interface ShoppingCartService {
    CartItemResponse addToCart(String userEmail, Long productId, Integer quantity);
    CartItemResponse updateCartItem(String userEmail, Long productId, Integer quantity);
    void removeFromCart(String userEmail, Long productId);
    List<CartItemResponse> getUserCart(String userEmail);
    void clearCart(String userEmail);
}

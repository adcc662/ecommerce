package com.example.ecommerce.service;

import com.example.ecommerce.models.entity.ShoppingCart;

import java.util.List;

public interface ShoppingCartService {
    ShoppingCart addToCart(String userEmail, Long productId, Integer quantity);
    ShoppingCart updateCartItem(String userEmail, Long productId, Integer quantity);
    void removeFromCart(String userEmail, Long productId);
    List<ShoppingCart> getUserCart(String userEmail);
    void clearCart(String userEmail);
}

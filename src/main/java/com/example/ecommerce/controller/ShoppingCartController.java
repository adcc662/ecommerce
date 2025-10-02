package com.example.ecommerce.controller;

import com.example.ecommerce.models.entity.ShoppingCart;
import com.example.ecommerce.service.ShoppingCartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class ShoppingCartController {

    private final ShoppingCartService shoppingCartService;

    @PostMapping("/add")
    public ResponseEntity<ShoppingCart> addToCart(
            @RequestParam Long productId,
            @RequestParam Integer quantity,
            Authentication authentication) {
        String userEmail = authentication.getName();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(shoppingCartService.addToCart(userEmail, productId, quantity));
    }

    @PutMapping("/update")
    public ResponseEntity<ShoppingCart> updateCartItem(
            @RequestParam Long productId,
            @RequestParam Integer quantity,
            Authentication authentication) {
        String userEmail = authentication.getName();
        return ResponseEntity.ok(shoppingCartService.updateCartItem(userEmail, productId, quantity));
    }

    @DeleteMapping("/remove")
    public ResponseEntity<Void> removeFromCart(
            @RequestParam Long productId,
            Authentication authentication) {
        String userEmail = authentication.getName();
        shoppingCartService.removeFromCart(userEmail, productId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<ShoppingCart>> getCart(Authentication authentication) {
        String userEmail = authentication.getName();
        return ResponseEntity.ok(shoppingCartService.getUserCart(userEmail));
    }

    @DeleteMapping("/clear")
    public ResponseEntity<Void> clearCart(Authentication authentication) {
        String userEmail = authentication.getName();
        shoppingCartService.clearCart(userEmail);
        return ResponseEntity.noContent().build();
    }
}

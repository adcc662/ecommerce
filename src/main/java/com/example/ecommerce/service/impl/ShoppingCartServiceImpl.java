package com.example.ecommerce.service.impl;

import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.models.dto.response.CartItemResponse;
import com.example.ecommerce.models.entity.Product;
import com.example.ecommerce.models.entity.ShoppingCart;
import com.example.ecommerce.models.entity.User;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.ShoppingCartRepository;
import com.example.ecommerce.repository.UserRepository;
import com.example.ecommerce.service.ShoppingCartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShoppingCartServiceImpl implements ShoppingCartService {

    private final ShoppingCartRepository shoppingCartRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public CartItemResponse addToCart(String userEmail, Long productId, Integer quantity) {
        if (quantity == null || quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        ShoppingCart existingCart = shoppingCartRepository.findByUserIdAndProductId(user.getId(), productId)
                .orElse(null);

        if (existingCart != null) {
            existingCart.setQuantity(existingCart.getQuantity() + quantity);
            return mapToResponse(shoppingCartRepository.save(existingCart));
        }

        ShoppingCart cart = ShoppingCart.builder()
                .user(user)
                .product(product)
                .quantity(quantity)
                .build();

        return mapToResponse(shoppingCartRepository.save(cart));
    }

    @Override
    @Transactional
    public CartItemResponse updateCartItem(String userEmail, Long productId, Integer quantity) {
        if (quantity == null || quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        ShoppingCart cart = shoppingCartRepository.findByUserIdAndProductId(user.getId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));

        cart.setQuantity(quantity);
        return mapToResponse(shoppingCartRepository.save(cart));
    }

    @Override
    @Transactional
    public void removeFromCart(String userEmail, Long productId) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        ShoppingCart cart = shoppingCartRepository.findByUserIdAndProductId(user.getId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));

        shoppingCartRepository.delete(cart);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CartItemResponse> getUserCart(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return shoppingCartRepository.findByUserId(user.getId()).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void clearCart(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        shoppingCartRepository.deleteByUserId(user.getId());
    }

    private CartItemResponse mapToResponse(ShoppingCart cart) {
        Product product = cart.getProduct();
        BigDecimal price = product.getDiscountPrice() != null ? product.getDiscountPrice() : product.getPrice();
        String image = product.getImages().stream()
                .findFirst()
                .map(productImage -> productImage.getImageUrl())
                .orElse(null);

        return CartItemResponse.builder()
                .cartId(cart.getId())
                .productId(product.getId())
                .productName(product.getName())
                .productPrice(price)
                .productImage(image)
                .productSlug(product.getSlug())
                .quantity(cart.getQuantity())
                .subtotal(price.multiply(BigDecimal.valueOf(cart.getQuantity())))
                .createdAt(cart.getCreatedAt())
                .updatedAt(cart.getUpdatedAt())
                .build();
    }
}

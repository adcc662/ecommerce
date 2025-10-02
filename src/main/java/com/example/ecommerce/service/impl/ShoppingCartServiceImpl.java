package com.example.ecommerce.service.impl;

import com.example.ecommerce.exception.ResourceNotFoundException;
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

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShoppingCartServiceImpl implements ShoppingCartService {

    private final ShoppingCartRepository shoppingCartRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    private static volatile ShoppingCartServiceImpl instance;

    public static ShoppingCartServiceImpl getInstance(ShoppingCartRepository shoppingCartRepository,
                                                       UserRepository userRepository,
                                                       ProductRepository productRepository) {
        if (instance == null) {
            synchronized (ShoppingCartServiceImpl.class) {
                if (instance == null) {
                    instance = new ShoppingCartServiceImpl(shoppingCartRepository, userRepository, productRepository);
                }
            }
        }
        return instance;
    }

    @Override
    @Transactional
    public ShoppingCart addToCart(String userEmail, Long productId, Integer quantity) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        ShoppingCart existingCart = shoppingCartRepository.findByUserIdAndProductId(user.getId(), productId)
                .orElse(null);

        if (existingCart != null) {
            existingCart.setQuantity(existingCart.getQuantity() + quantity);
            return shoppingCartRepository.save(existingCart);
        }

        ShoppingCart cart = ShoppingCart.builder()
                .user(user)
                .product(product)
                .quantity(quantity)
                .build();

        return shoppingCartRepository.save(cart);
    }

    @Override
    @Transactional
    public ShoppingCart updateCartItem(String userEmail, Long productId, Integer quantity) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        ShoppingCart cart = shoppingCartRepository.findByUserIdAndProductId(user.getId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));

        cart.setQuantity(quantity);
        return shoppingCartRepository.save(cart);
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
    public List<ShoppingCart> getUserCart(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return shoppingCartRepository.findByUserId(user.getId());
    }

    @Override
    @Transactional
    public void clearCart(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        shoppingCartRepository.deleteByUserId(user.getId());
    }
}

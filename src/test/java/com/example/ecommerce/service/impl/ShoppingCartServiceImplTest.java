package com.example.ecommerce.service.impl;

import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.models.entity.Product;
import com.example.ecommerce.models.entity.ShoppingCart;
import com.example.ecommerce.models.entity.User;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.ShoppingCartRepository;
import com.example.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShoppingCartServiceImplTest {

    private static final String EMAIL = "user@example.com";

    @Mock
    private ShoppingCartRepository shoppingCartRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ShoppingCartServiceImpl cartService;

    private final User user = User.builder().id(10L).email(EMAIL).build();

    @BeforeEach
    void setUp() {
        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        lenient().when(shoppingCartRepository.save(any(ShoppingCart.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Product product(boolean active, int stock) {
        return Product.builder().id(3L).name("Laptop").price(BigDecimal.TEN).isActive(active).stockQuantity(stock).build();
    }

    @Test
    void addRejectsInactiveProduct() {
        when(productRepository.findById(3L)).thenReturn(Optional.of(product(false, 10)));

        assertThatThrownBy(() -> cartService.addToCart(EMAIL, 3L, 1))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product not found");
        verify(shoppingCartRepository, never()).save(any());
    }

    @Test
    void addRejectsQuantityAboveStock() {
        when(productRepository.findById(3L)).thenReturn(Optional.of(product(true, 2)));

        assertThatThrownBy(() -> cartService.addToCart(EMAIL, 3L, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stock");
    }

    @Test
    void addCountsQuantityAlreadyInCartAgainstStock() {
        Product product = product(true, 5);
        when(productRepository.findById(3L)).thenReturn(Optional.of(product));
        when(shoppingCartRepository.findByUserIdAndProductId(10L, 3L)).thenReturn(Optional.of(
                ShoppingCart.builder().user(user).product(product).quantity(4).build()));

        assertThatThrownBy(() -> cartService.addToCart(EMAIL, 3L, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addRejectsQuantityAboveLimit() {
        assertThatThrownBy(() -> cartService.addToCart(EMAIL, 3L, 1001))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addStillWorksWithinStock() {
        when(productRepository.findById(3L)).thenReturn(Optional.of(product(true, 5)));
        when(shoppingCartRepository.findByUserIdAndProductId(10L, 3L)).thenReturn(Optional.empty());

        assertThat(cartService.addToCart(EMAIL, 3L, 5).getQuantity()).isEqualTo(5);
    }

    @Test
    void updateRejectsQuantityAboveStock() {
        Product product = product(true, 2);
        when(shoppingCartRepository.findByUserIdAndProductId(10L, 3L)).thenReturn(Optional.of(
                ShoppingCart.builder().user(user).product(product).quantity(1).build()));

        assertThatThrownBy(() -> cartService.updateCartItem(EMAIL, 3L, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateStillWorksWithinStock() {
        Product product = product(true, 5);
        when(shoppingCartRepository.findByUserIdAndProductId(10L, 3L)).thenReturn(Optional.of(
                ShoppingCart.builder().user(user).product(product).quantity(1).build()));

        assertThat(cartService.updateCartItem(EMAIL, 3L, 4).getQuantity()).isEqualTo(4);
    }
}

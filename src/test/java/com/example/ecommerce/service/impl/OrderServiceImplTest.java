package com.example.ecommerce.service.impl;

import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.models.dto.request.OrderItemRequest;
import com.example.ecommerce.models.dto.request.OrderRequest;
import com.example.ecommerce.models.entity.Address;
import com.example.ecommerce.models.entity.Order;
import com.example.ecommerce.models.entity.Product;
import com.example.ecommerce.models.entity.User;
import com.example.ecommerce.models.enums.OrderStatus;
import com.example.ecommerce.repository.AddressRepository;
import com.example.ecommerce.repository.OrderItemRepository;
import com.example.ecommerce.repository.OrderRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Test
    void getOrderByIdScopesLookupToAuthenticatedUser() {
        User user = User.builder().id(10L).email("user@example.com").build();
        Order order = Order.builder()
                .id(99L)
                .orderNumber("ORD-123")
                .orderStatus(OrderStatus.PENDING)
                .subtotal(BigDecimal.TEN)
                .taxAmount(BigDecimal.ZERO)
                .shippingCost(BigDecimal.ZERO)
                .totalAmount(BigDecimal.TEN)
                .build();

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(orderRepository.findByIdAndUserId(99L, 10L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(99L)).thenReturn(List.of());

        orderService.getOrderById(99L, "user@example.com");

        verify(orderRepository).findByIdAndUserId(99L, 10L);
    }

    @Test
    void createOrderRejectsAddressThatDoesNotBelongToUser() {
        User user = User.builder().id(10L).email("user@example.com").build();
        OrderRequest request = OrderRequest.builder()
                .shippingAddressId(1L)
                .billingAddressId(2L)
                .items(List.of(OrderItemRequest.builder().productId(3L).quantity(1).build()))
                .build();

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(addressRepository.findByIdAndUserId(1L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrder(request, "user@example.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Shipping address not found");

        verify(productRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void createOrderDecrementsProductStockInsideCheckout() {
        User user = User.builder().id(10L).email("user@example.com").build();
        Address address = Address.builder().id(1L).user(user).build();
        Product product = Product.builder()
                .id(3L)
                .name("Laptop")
                .price(BigDecimal.TEN)
                .stockQuantity(5)
                .isActive(true)
                .build();
        OrderRequest request = OrderRequest.builder()
                .shippingAddressId(1L)
                .billingAddressId(1L)
                .items(List.of(OrderItemRequest.builder().productId(3L).quantity(2).build()))
                .build();

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(addressRepository.findByIdAndUserId(1L, 10L)).thenReturn(Optional.of(address));
        when(productRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.findByOrderId(any())).thenReturn(List.of());

        orderService.createOrder(request, "user@example.com");

        assertThat(product.getStockQuantity()).isEqualTo(3);
        verify(productRepository).save(product);
    }

    @Test
    void createOrderRejectsNullQuantityBeforeStockLookup() {
        assertCreateOrderRejectsInvalidQuantity(null);
    }

    @Test
    void createOrderRejectsZeroQuantityBeforeStockLookup() {
        assertCreateOrderRejectsInvalidQuantity(0);
    }

    @Test
    void createOrderRejectsNegativeQuantityBeforeStockLookup() {
        assertCreateOrderRejectsInvalidQuantity(-1);
    }

    private void assertCreateOrderRejectsInvalidQuantity(Integer quantity) {
        User user = User.builder().id(10L).email("user@example.com").build();
        Address address = Address.builder().id(1L).user(user).build();
        OrderRequest request = OrderRequest.builder()
                .shippingAddressId(1L)
                .billingAddressId(1L)
                .items(List.of(OrderItemRequest.builder().productId(3L).quantity(quantity).build()))
                .build();

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(addressRepository.findByIdAndUserId(1L, 10L)).thenReturn(Optional.of(address));

        assertThatThrownBy(() -> orderService.createOrder(request, "user@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Quantity must be at least 1");

        verify(orderRepository, never()).save(any(Order.class));
        verify(productRepository, never()).findByIdForUpdate(any());
    }
}

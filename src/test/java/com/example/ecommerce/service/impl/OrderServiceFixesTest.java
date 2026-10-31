package com.example.ecommerce.service.impl;

import com.example.ecommerce.models.dto.request.OrderItemRequest;
import com.example.ecommerce.models.dto.request.OrderRequest;
import com.example.ecommerce.models.entity.Address;
import com.example.ecommerce.models.entity.Order;
import com.example.ecommerce.models.entity.OrderItem;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.annotation.CacheEvict;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceFixesTest {

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

    private static Product product(long id, int stock) {
        return Product.builder().id(id).name("P" + id).price(BigDecimal.TEN).stockQuantity(stock).isActive(true).build();
    }

    private static OrderItemRequest line(long productId, int quantity) {
        return OrderItemRequest.builder().productId(productId).quantity(quantity).build();
    }

    // S9: cache stays consistent with stock

    @Test
    void stockChangingOperationsEvictProductCache() throws NoSuchMethodException {
        CacheEvict onCreate = OrderServiceImpl.class.getMethod("createOrder", OrderRequest.class, String.class)
                .getAnnotation(CacheEvict.class);
        CacheEvict onStatus = OrderServiceImpl.class.getMethod("updateOrderStatus", Long.class, String.class)
                .getAnnotation(CacheEvict.class);

        assertThat(onCreate).isNotNull();
        assertThat(onCreate.value()).containsExactly("products");
        assertThat(onCreate.allEntries()).isTrue();
        assertThat(onStatus).isNotNull();
        assertThat(onStatus.allEntries()).isTrue();
    }

    @Test
    void cancellingOrderRestoresStock() {
        Product product = product(3L, 2);
        Order order = Order.builder().id(99L).orderStatus(OrderStatus.PENDING).build();
        OrderItem item = OrderItem.builder().order(order).product(product).quantity(3).unitPrice(BigDecimal.TEN)
                .subtotal(BigDecimal.TEN).discountAmount(BigDecimal.ZERO).build();
        when(orderRepository.findById(99L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(99L)).thenReturn(List.of(item));
        when(productRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(product));

        orderService.updateOrderStatus(99L, "CANCELLED");

        assertThat(product.getStockQuantity()).isEqualTo(5);
        verify(productRepository).save(product);
    }

    @Test
    void otherTransitionsDoNotTouchStock() {
        Order order = Order.builder().id(99L).orderStatus(OrderStatus.DELIVERED).build();
        when(orderRepository.findById(99L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(99L)).thenReturn(List.of());

        orderService.updateOrderStatus(99L, "REFUNDED");

        verify(productRepository, never()).findByIdForUpdate(anyLong());
        verify(productRepository, never()).save(any());
    }

    // Concurrency: duplicate lines merge, locks are taken in a stable order

    @Test
    void createOrderMergesDuplicateLinesAndLocksProductsInIdOrder() {
        User user = User.builder().id(10L).email("user@example.com").build();
        Address address = Address.builder().id(1L).user(user).build();
        Product p3 = product(3L, 10);
        Product p5 = product(5L, 10);
        OrderRequest request = OrderRequest.builder().shippingAddressId(1L).billingAddressId(1L)
                .items(List.of(line(5L, 1), line(3L, 2), line(5L, 2))).build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(addressRepository.findByIdAndUserId(1L, 10L)).thenReturn(Optional.of(address));
        when(productRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(p3));
        when(productRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(p5));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.findByOrderId(any())).thenReturn(List.of());

        orderService.createOrder(request, "user@example.com");

        InOrder locks = inOrder(productRepository);
        locks.verify(productRepository).findByIdForUpdate(3L);
        locks.verify(productRepository).findByIdForUpdate(5L);
        verify(productRepository, times(1)).findByIdForUpdate(5L);
        assertThat(p5.getStockQuantity()).isEqualTo(7);
        assertThat(p3.getStockQuantity()).isEqualTo(8);
        ArgumentCaptor<OrderItem> items = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemRepository, times(2)).save(items.capture());
        assertThat(items.getAllValues()).extracting(OrderItem::getQuantity).containsExactly(2, 3);
    }

    // S14 (#23): order list loads items in one query

    @Test
    void userOrdersLoadItemsInOneBatch() {
        User user = User.builder().id(10L).email("user@example.com").build();
        Order first = Order.builder().id(1L).orderStatus(OrderStatus.PENDING).build();
        Order second = Order.builder().id(2L).orderStatus(OrderStatus.PENDING).build();
        Product product = product(3L, 1);
        OrderItem item = OrderItem.builder().order(second).product(product).quantity(1).unitPrice(BigDecimal.TEN)
                .subtotal(BigDecimal.TEN).discountAmount(BigDecimal.ZERO).build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(orderRepository.findByUserIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(first, second));
        when(orderItemRepository.findWithProductByOrderIdIn(List.of(1L, 2L))).thenReturn(List.of(item));

        var responses = orderService.getUserOrders("user@example.com");

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getItems()).isEmpty();
        assertThat(responses.get(1).getItems()).hasSize(1);
        verify(orderItemRepository, never()).findByOrderId(anyLong());
    }
}

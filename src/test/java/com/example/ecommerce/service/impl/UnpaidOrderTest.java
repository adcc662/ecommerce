package com.example.ecommerce.service.impl;

import com.example.ecommerce.config.OrderProperties;
import com.example.ecommerce.config.PricingProperties;
import com.example.ecommerce.exception.ConflictException;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.models.dto.request.OrderItemRequest;
import com.example.ecommerce.models.dto.request.OrderRequest;
import com.example.ecommerce.models.entity.Order;
import com.example.ecommerce.models.entity.OrderItem;
import com.example.ecommerce.models.entity.Payment;
import com.example.ecommerce.models.entity.Product;
import com.example.ecommerce.models.entity.User;
import com.example.ecommerce.models.enums.OrderStatus;
import com.example.ecommerce.models.enums.PaymentStatus;
import com.example.ecommerce.repository.AddressRepository;
import com.example.ecommerce.repository.OrderItemRepository;
import com.example.ecommerce.repository.OrderRepository;
import com.example.ecommerce.repository.PaymentRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.annotation.CacheEvict;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnpaidOrderTest {

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
    @Mock
    private PaymentRepository paymentRepository;
    @Spy
    private PricingProperties pricing = new PricingProperties(BigDecimal.ZERO, BigDecimal.ZERO);
    @Spy
    private OrderProperties orderProperties = new OrderProperties(Duration.ofMinutes(30), 3);

    @InjectMocks
    private OrderServiceImpl orderService;

    private static final User USER = User.builder().id(1L).email("a@example.com").build();
    private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 11, 24, 12, 0);

    private Product product;
    private Payment pendingPayment;

    /** A PENDING order of 3 units whose product has 2 left in stock, with one payment in progress. */
    private Order pendingOrder(LocalDateTime createdAt) {
        Order order = Order.builder().id(99L).user(USER).orderStatus(OrderStatus.PENDING).createdAt(createdAt).build();
        product = Product.builder().id(5L).name("P5").price(BigDecimal.TEN).stockQuantity(2).isActive(true).build();
        OrderItem item = OrderItem.builder().order(order).product(product).quantity(3)
                .unitPrice(BigDecimal.TEN).subtotal(BigDecimal.TEN).discountAmount(BigDecimal.ZERO).build();
        pendingPayment = Payment.builder().id(1L).order(order).paymentStatus(PaymentStatus.PENDING).amount(BigDecimal.TEN).build();
        when(orderItemRepository.findByOrderId(99L)).thenReturn(List.of(item));
        when(productRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(product));
        when(paymentRepository.findByOrderId(99L)).thenReturn(List.of(pendingPayment));
        return order;
    }

    private void assertCancelledAndStockRestored(Order order) {
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(product.getStockQuantity()).isEqualTo(5);
        assertThat(pendingPayment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(orderRepository).save(order);
    }

    // Expiry of unpaid orders

    @Test
    void unpaidOrderOlderThanTheCutoffIsCancelledAndItsStockReturned() {
        Order order = pendingOrder(CUTOFF.minusMinutes(1));
        when(orderRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(order));

        assertThat(orderService.expireUnpaidOrder(99L, CUTOFF)).isTrue();

        assertCancelledAndStockRestored(order);
    }

    @Test
    void orderPaidMeanwhileIsNotExpired() {
        Order order = Order.builder().id(99L).orderStatus(OrderStatus.PROCESSING).createdAt(CUTOFF.minusHours(1)).build();
        when(orderRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(order));

        assertThat(orderService.expireUnpaidOrder(99L, CUTOFF)).isFalse();

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PROCESSING);
        verify(productRepository, never()).findByIdForUpdate(anyLong());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void orderNewerThanTheCutoffIsNotExpired() {
        Order order = Order.builder().id(99L).orderStatus(OrderStatus.PENDING).createdAt(CUTOFF.plusSeconds(1)).build();
        when(orderRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(order));

        assertThat(orderService.expireUnpaidOrder(99L, CUTOFF)).isFalse();

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void deletedOrderIsSkipped() {
        when(orderRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThat(orderService.expireUnpaidOrder(99L, CUTOFF)).isFalse();
    }

    // Customer cancels their own unpaid order

    @Test
    void customerCancelsTheirUnpaidOrder() {
        Order order = pendingOrder(CUTOFF);
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(USER));
        when(orderRepository.findByIdAndUserIdForUpdate(99L, 1L)).thenReturn(Optional.of(order));

        assertThat(orderService.cancelMyOrder(99L, "a@example.com").getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);

        assertCancelledAndStockRestored(order);
    }

    @Test
    void customerCannotCancelAPaidOrder() {
        Order order = Order.builder().id(99L).user(USER).orderStatus(OrderStatus.PROCESSING).build();
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(USER));
        when(orderRepository.findByIdAndUserIdForUpdate(99L, 1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelMyOrder(99L, "a@example.com"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Only unpaid (PENDING) orders can be cancelled");
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PROCESSING);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void customerCannotCancelSomeoneElsesOrder() {
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(USER));
        when(orderRepository.findByIdAndUserIdForUpdate(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancelMyOrder(99L, "a@example.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Order not found");
    }

    // Cap on unpaid orders per customer

    @Test
    void checkoutIsRefusedWhenTheCustomerHasTooManyUnpaidOrders() {
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(USER));
        when(orderRepository.countByUserIdAndOrderStatus(1L, OrderStatus.PENDING)).thenReturn(3L);
        OrderRequest request = OrderRequest.builder().shippingAddressId(1L).billingAddressId(1L)
                .items(List.of(OrderItemRequest.builder().productId(5L).quantity(1).build())).build();

        assertThatThrownBy(() -> orderService.createOrder(request, "a@example.com"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("You have too many unpaid orders. Pay or cancel one before placing another.");

        // The user row is locked before counting, so parallel checkouts cannot all pass the check
        InOrder lockThenCount = inOrder(userRepository, orderRepository);
        lockThenCount.verify(userRepository).lockForCheckout(1L);
        lockThenCount.verify(orderRepository).countByUserIdAndOrderStatus(1L, OrderStatus.PENDING);
        verify(productRepository, never()).findByIdForUpdate(anyLong());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void stockReturningOperationsEvictTheProductCache() throws NoSuchMethodException {
        for (var method : List.of(
                OrderServiceImpl.class.getMethod("cancelMyOrder", Long.class, String.class),
                OrderServiceImpl.class.getMethod("expireUnpaidOrder", Long.class, LocalDateTime.class))) {
            CacheEvict evict = method.getAnnotation(CacheEvict.class);
            assertThat(evict).as(method.getName()).isNotNull();
            assertThat(evict.value()).containsExactly("products");
            assertThat(evict.allEntries()).isTrue();
        }
    }
}

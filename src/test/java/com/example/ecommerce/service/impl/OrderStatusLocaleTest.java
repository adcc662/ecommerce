package com.example.ecommerce.service.impl;

import com.example.ecommerce.config.PricingProperties;
import com.example.ecommerce.models.entity.Order;
import com.example.ecommerce.models.enums.OrderStatus;
import com.example.ecommerce.repository.AddressRepository;
import com.example.ecommerce.repository.OrderItemRepository;
import com.example.ecommerce.repository.OrderRepository;
import com.example.ecommerce.repository.PaymentRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** S2: status parsing must not depend on the server's default locale. */
class OrderStatusLocaleTest {

    private final Locale original = Locale.getDefault();
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(original);
    }

    @Test
    void lowercaseStatusWithAnIParsesUnderTurkishLocale() {
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        OrderServiceImpl service = new OrderServiceImpl(orderRepository, mock(UserRepository.class),
                mock(ProductRepository.class), mock(AddressRepository.class), orderItemRepository,
                mock(PaymentRepository.class), new PricingProperties(BigDecimal.ZERO, BigDecimal.ZERO));
        Order order = Order.builder().id(99L).orderStatus(OrderStatus.PROCESSING).build();
        when(orderRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(99L)).thenReturn(List.of());

        service.updateOrderStatus(99L, "shipped");

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.SHIPPED);
    }
}

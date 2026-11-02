package com.example.ecommerce.service.impl;

import com.example.ecommerce.config.PricingProperties;
import com.example.ecommerce.models.dto.request.OrderItemRequest;
import com.example.ecommerce.models.dto.request.OrderRequest;
import com.example.ecommerce.models.entity.Address;
import com.example.ecommerce.models.entity.Order;
import com.example.ecommerce.models.entity.Product;
import com.example.ecommerce.models.entity.User;
import com.example.ecommerce.repository.AddressRepository;
import com.example.ecommerce.repository.OrderItemRepository;
import com.example.ecommerce.repository.OrderRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.UserRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderPricingTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final AddressRepository addressRepository = mock(AddressRepository.class);
    private final OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);

    private Order placeOrder(String taxRate, String shippingFee, String unitPrice, int quantity) {
        OrderServiceImpl service = new OrderServiceImpl(orderRepository, userRepository, productRepository,
                addressRepository, orderItemRepository,
                new PricingProperties(new BigDecimal(taxRate), new BigDecimal(shippingFee)));
        User user = User.builder().id(10L).email("u@example.com").build();
        Address address = Address.builder().id(1L).user(user).build();
        Product product = Product.builder().id(3L).name("P").price(new BigDecimal(unitPrice))
                .stockQuantity(100).isActive(true).build();
        when(userRepository.findByEmail("u@example.com")).thenReturn(Optional.of(user));
        when(addressRepository.findByIdAndUserId(1L, 10L)).thenReturn(Optional.of(address));
        when(productRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.findByOrderId(any())).thenReturn(List.of());
        var captured = new Order[1];
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> captured[0] = inv.getArgument(0));

        service.createOrder(OrderRequest.builder().shippingAddressId(1L).billingAddressId(1L)
                .items(List.of(OrderItemRequest.builder().productId(3L).quantity(quantity).build())).build(),
                "u@example.com");
        return captured[0];
    }

    @Test
    void appliesTaxRateAndShippingFee() {
        Order order = placeOrder("0.0825", "5.99", "10.00", 2);

        assertThat(order.getSubtotal()).isEqualByComparingTo("20.00");
        assertThat(order.getTaxAmount()).isEqualByComparingTo("1.65");
        assertThat(order.getShippingCost()).isEqualByComparingTo("5.99");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("27.64");
    }

    @Test
    void roundsTaxHalfUpToCents() {
        Order order = placeOrder("0.075", "0", "10.05", 1);

        assertThat(order.getTaxAmount()).isEqualByComparingTo("0.75");
        assertThat(order.getTaxAmount().scale()).isEqualTo(2);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("10.80");
    }

    @Test
    void defaultsKeepTotalEqualToSubtotal() {
        Order order = placeOrder("0", "0", "10.00", 3);

        assertThat(order.getTaxAmount()).isEqualByComparingTo("0");
        assertThat(order.getShippingCost()).isEqualByComparingTo("0");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("30.00");
    }

    @Test
    void rejectsOutOfRangeSettings() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        assertThat(validator.validate(new PricingProperties(new BigDecimal("1.5"), BigDecimal.ZERO))).isNotEmpty();
        assertThat(validator.validate(new PricingProperties(new BigDecimal("-0.1"), BigDecimal.ZERO))).isNotEmpty();
        assertThat(validator.validate(new PricingProperties(BigDecimal.ZERO, new BigDecimal("-1")))).isNotEmpty();
        assertThat(validator.validate(new PricingProperties(new BigDecimal("0.21"), new BigDecimal("4.99")))).isEmpty();
    }
}

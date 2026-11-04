package com.example.ecommerce.service.impl;

import com.example.ecommerce.config.PricingProperties;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.models.dto.request.OrderRequest;
import com.example.ecommerce.models.dto.response.OrderItemResponse;
import com.example.ecommerce.models.dto.response.OrderResponse;
import com.example.ecommerce.models.entity.*;
import com.example.ecommerce.models.enums.OrderStatus;
import com.example.ecommerce.models.enums.PaymentStatus;
import com.example.ecommerce.repository.*;
import com.example.ecommerce.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final AddressRepository addressRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final PricingProperties pricing;

    @Override
    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public OrderResponse createOrder(OrderRequest request, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Address shippingAddress = addressRepository.findByIdAndUserId(request.getShippingAddressId(), user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Shipping address not found"));

        Address billingAddress = addressRepository.findByIdAndUserId(request.getBillingAddressId(), user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Billing address not found"));

        for (var itemRequest : request.getItems()) {
            if (itemRequest == null || itemRequest.getQuantity() == null || itemRequest.getQuantity() < 1) {
                throw new IllegalArgumentException("Quantity must be at least 1");
            }
        }

        // One line per product, locked in ascending id order so concurrent
        // checkouts of the same products cannot deadlock.
        Map<Long, Integer> quantities = new TreeMap<>();
        for (var itemRequest : request.getItems()) {
            quantities.merge(itemRequest.getProductId(), itemRequest.getQuantity(), Math::addExact);
        }

        BigDecimal subtotal = BigDecimal.ZERO;

        Order order = Order.builder()
                .user(user)
                .orderNumber(generateOrderNumber())
                .orderStatus(OrderStatus.PENDING)
                .shippingAddress(shippingAddress)
                .billingAddress(billingAddress)
                .subtotal(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .shippingCost(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .build();

        order = orderRepository.save(order);

        for (Map.Entry<Long, Integer> line : quantities.entrySet()) {
            int quantity = line.getValue();
            Product product = productRepository.findByIdForUpdate(line.getKey())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

            if (!Boolean.TRUE.equals(product.getIsActive())) {
                throw new ResourceNotFoundException("Product not found");
            }

            if (product.getStockQuantity() < quantity) {
                throw new IllegalArgumentException("Insufficient stock for product: " + product.getName());
            }

            product.setStockQuantity(product.getStockQuantity() - quantity);
            productRepository.save(product);

            BigDecimal unitPrice = product.getDiscountPrice() != null ? product.getDiscountPrice() : product.getPrice();
            BigDecimal itemSubtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
            subtotal = subtotal.add(itemSubtotal);

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(quantity)
                    .unitPrice(unitPrice)
                    .discountAmount(BigDecimal.ZERO)
                    .subtotal(itemSubtotal)
                    .build();

            orderItemRepository.save(orderItem);
        }

        BigDecimal tax = subtotal.multiply(pricing.taxRate()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal shipping = pricing.shippingFee().setScale(2, RoundingMode.HALF_UP);
        order.setSubtotal(subtotal);
        order.setTaxAmount(tax);
        order.setShippingCost(shipping);
        order.setTotalAmount(subtotal.add(tax).add(shipping));
        orderRepository.save(order);

        return mapToResponse(order);
    }

    @Override
    public OrderResponse getOrderById(Long id, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Order order = orderRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        return mapToResponse(order);
    }

    @Override
    public List<OrderResponse> getUserOrders(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        if (orders.isEmpty()) {
            return List.of();
        }
        Map<Long, List<OrderItem>> itemsByOrder = orderItemRepository
                .findWithProductByOrderIdIn(orders.stream().map(Order::getId).toList()).stream()
                .collect(Collectors.groupingBy(item -> item.getOrder().getId()));
        return orders.stream()
                .map(order -> mapToResponse(order, itemsByOrder.getOrDefault(order.getId(), List.of())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public OrderResponse updateOrderStatus(Long id, String status) {
        // Locked so a concurrent payment update cannot complete a payment of an order being cancelled
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        OrderStatus newStatus;
        try {
            newStatus = OrderStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid order status: " + status);
        }

        OrderStatus currentStatus = order.getOrderStatus();
        if (!currentStatus.canTransitionTo(newStatus)) {
            throw new IllegalArgumentException(
                    "Invalid status transition from " + currentStatus + " to " + newStatus);
        }

        if (newStatus == OrderStatus.CANCELLED) {
            restoreStock(order);
        }
        if (newStatus == OrderStatus.CANCELLED || newStatus == OrderStatus.REFUNDED) {
            settlePayments(order);
        }

        order.setOrderStatus(newStatus);
        orderRepository.save(order);

        return mapToResponse(order);
    }

    /** Puts the ordered quantities back; goods of a cancelled order never shipped. */
    private void restoreStock(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId()).stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getId()))
                .toList();
        for (OrderItem item : items) {
            Product product = productRepository.findByIdForUpdate(item.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
            product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
            productRepository.save(product);
        }
    }

    /** A closed order keeps no open money: pending payments fail, completed ones are refunded. */
    private void settlePayments(Order order) {
        List<Payment> changed = new ArrayList<>();
        for (Payment payment : paymentRepository.findByOrderId(order.getId())) {
            PaymentStatus settled = switch (payment.getPaymentStatus()) {
                case PENDING -> PaymentStatus.FAILED;
                case COMPLETED -> PaymentStatus.REFUNDED;
                case FAILED, REFUNDED -> null;
            };
            if (settled != null) {
                payment.setPaymentStatus(settled);
                changed.add(payment);
            }
        }
        if (!changed.isEmpty()) {
            paymentRepository.saveAll(changed);
        }
    }

    private String generateOrderNumber() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private OrderResponse mapToResponse(Order order) {
        return mapToResponse(order, orderItemRepository.findByOrderId(order.getId()));
    }

    private OrderResponse mapToResponse(Order order, List<OrderItem> orderItems) {
        List<OrderItemResponse> items = orderItems.stream()
                .map(item -> OrderItemResponse.builder()
                        .id(item.getId())
                        .productName(item.getProduct().getName())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .discountAmount(item.getDiscountAmount())
                        .subtotal(item.getSubtotal())
                        .build())
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .orderStatus(order.getOrderStatus())
                .subtotal(order.getSubtotal())
                .taxAmount(order.getTaxAmount())
                .shippingCost(order.getShippingCost())
                .totalAmount(order.getTotalAmount())
                .items(items)
                .createdAt(order.getCreatedAt())
                .build();
    }
}

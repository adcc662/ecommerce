package com.example.ecommerce.service.impl;

import com.example.ecommerce.exception.ConflictException;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.models.dto.request.PaymentRequest;
import com.example.ecommerce.models.dto.response.PaymentMethodResponse;
import com.example.ecommerce.models.dto.response.PaymentResponse;
import com.example.ecommerce.models.entity.Order;
import com.example.ecommerce.models.entity.Payment;
import com.example.ecommerce.models.entity.PaymentMethod;
import com.example.ecommerce.models.entity.User;
import com.example.ecommerce.models.enums.OrderStatus;
import com.example.ecommerce.models.enums.PaymentStatus;
import com.example.ecommerce.payment.PaymentGateway;
import com.example.ecommerce.payment.PaymentInitiation;
import com.example.ecommerce.repository.OrderRepository;
import com.example.ecommerce.repository.PaymentMethodRepository;
import com.example.ecommerce.repository.PaymentRepository;
import com.example.ecommerce.repository.UserRepository;
import com.example.ecommerce.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentServiceImpl implements PaymentService {

    private static final Set<PaymentStatus> ACTIVE = EnumSet.of(PaymentStatus.PENDING, PaymentStatus.COMPLETED);
    private static final Set<OrderStatus> CLOSED_ORDERS = EnumSet.of(OrderStatus.CANCELLED, OrderStatus.REFUNDED);

    private final PaymentRepository paymentRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final PaymentGateway paymentGateway;

    @Override
    public List<PaymentMethodResponse> getPaymentMethods() {
        return paymentMethodRepository.findByIsActiveTrue().stream()
                .map(method -> new PaymentMethodResponse(method.getId(), method.getMethodName()))
                .toList();
    }

    /**
     * Starts a payment for the full order total. The order row is locked so two
     * concurrent requests cannot both create an active payment.
     */
    @Override
    @Transactional
    public PaymentResponse createPayment(String userEmail, Long orderId, PaymentRequest request) {
        User user = findUser(userEmail);
        Order order = orderRepository.findByIdAndUserIdForUpdate(orderId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (order.getOrderStatus() != OrderStatus.PENDING) {
            throw new ConflictException("Order is not awaiting payment");
        }
        PaymentMethod method = paymentMethodRepository.findById(request.getPaymentMethodId())
                .filter(m -> Boolean.TRUE.equals(m.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Payment method not found"));
        if (paymentRepository.existsByOrderIdAndPaymentStatusIn(order.getId(), ACTIVE)) {
            throw new ConflictException("Order already has an active payment");
        }

        PaymentInitiation initiation = paymentGateway.initiate(order, method, order.getTotalAmount());
        Payment payment = Payment.builder()
                .order(order)
                .paymentMethod(method)
                .transactionId(initiation.transactionId())
                .paymentStatus(initiation.status())
                .amount(order.getTotalAmount())
                .build();
        payment = paymentRepository.save(payment);
        if (payment.getPaymentStatus() == PaymentStatus.COMPLETED) {
            advanceOrder(order);
        }
        return mapToResponse(payment);
    }

    @Override
    public List<PaymentResponse> getPayments(String userEmail, Long orderId) {
        User user = findUser(userEmail);
        Order order = orderRepository.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        return paymentRepository.findByOrderId(order.getId()).stream()
                .map(PaymentServiceImpl::mapToResponse)
                .toList();
    }

    /**
     * Locks the order before loading the payment, so a status change cannot race
     * an order cancel or another payment update, and keeps the order in step.
     */
    @Override
    @Transactional
    public PaymentResponse updatePaymentStatus(Long paymentId, String status) {
        Long orderId = paymentRepository.findOrderIdById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        PaymentStatus newStatus;
        try {
            newStatus = PaymentStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid payment status: " + status);
        }
        // Checked before the transition: cancelling already failed the payment, and the caller needs the 409
        OrderStatus orderStatus = order.getOrderStatus();
        if (newStatus == PaymentStatus.COMPLETED && CLOSED_ORDERS.contains(orderStatus)) {
            throw new ConflictException("Order is " + orderStatus + "; its payment cannot be completed");
        }
        PaymentStatus current = payment.getPaymentStatus();
        if (current == null || !current.canTransitionTo(newStatus)) {
            throw new IllegalArgumentException("Invalid payment status transition from " + current + " to " + newStatus);
        }
        if (newStatus == PaymentStatus.REFUNDED && orderStatus != OrderStatus.DELIVERED) {
            throw new ConflictException("Only payments of delivered orders can be refunded; cancel the order instead");
        }

        payment.setPaymentStatus(newStatus);
        if (newStatus == PaymentStatus.COMPLETED) {
            advanceOrder(order);
        } else if (newStatus == PaymentStatus.REFUNDED) {
            order.setOrderStatus(OrderStatus.REFUNDED);
            orderRepository.save(order);
        }
        return mapToResponse(paymentRepository.save(payment));
    }

    /** A paid order that is still pending moves on to processing. */
    private void advanceOrder(Order order) {
        if (order.getOrderStatus() == OrderStatus.PENDING) {
            order.setOrderStatus(OrderStatus.PROCESSING);
            orderRepository.save(order);
        }
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private static PaymentResponse mapToResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrder().getId())
                .paymentMethod(payment.getPaymentMethod().getMethodName())
                .transactionId(payment.getTransactionId())
                .status(payment.getPaymentStatus())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paymentDate(payment.getPaymentDate())
                .build();
    }
}

package com.example.ecommerce.service.impl;

import com.example.ecommerce.exception.ConflictException;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.models.dto.request.PaymentRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    private static final String EMAIL = "user@example.com";

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentMethodRepository paymentMethodRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PaymentGateway paymentGateway;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private final User user = User.builder().id(10L).email(EMAIL).build();
    private final PaymentMethod card = PaymentMethod.builder().id(1L).methodName("credit_card").isActive(true).build();
    private Order order;

    @BeforeEach
    void setUp() {
        order = Order.builder().id(99L).user(user).orderStatus(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("27.64")).build();
        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        lenient().when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static PaymentRequest request() {
        return PaymentRequest.builder().paymentMethodId(1L).build();
    }

    // Create

    @Test
    void createsPendingPaymentForOrderTotalThroughTheGateway() {
        when(orderRepository.findByIdAndUserIdForUpdate(99L, 10L)).thenReturn(Optional.of(order));
        when(paymentMethodRepository.findById(1L)).thenReturn(Optional.of(card));
        when(paymentGateway.initiate(order, card, new BigDecimal("27.64")))
                .thenReturn(new PaymentInitiation("MANUAL-123", PaymentStatus.PENDING));

        var response = paymentService.createPayment(EMAIL, 99L, request());

        assertThat(response.getAmount()).isEqualByComparingTo("27.64");
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.getTransactionId()).isEqualTo("MANUAL-123");
        assertThat(response.getPaymentMethod()).isEqualTo("credit_card");
        assertThat(response.getOrderId()).isEqualTo(99L);
    }

    @Test
    void cannotPayAnotherUsersOrder() {
        when(orderRepository.findByIdAndUserIdForUpdate(99L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createPayment(EMAIL, 99L, request()))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Order not found");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void onlyPendingOrdersCanBePaid() {
        order.setOrderStatus(OrderStatus.CANCELLED);
        when(orderRepository.findByIdAndUserIdForUpdate(99L, 10L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.createPayment(EMAIL, 99L, request()))
                .isInstanceOf(ConflictException.class);
        verify(paymentGateway, never()).initiate(any(), any(), any());
    }

    @Test
    void inactivePaymentMethodIsRejected() {
        card.setIsActive(false);
        when(orderRepository.findByIdAndUserIdForUpdate(99L, 10L)).thenReturn(Optional.of(order));
        when(paymentMethodRepository.findById(1L)).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> paymentService.createPayment(EMAIL, 99L, request()))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Payment method not found");
    }

    @Test
    void secondActivePaymentIsRejected() {
        when(orderRepository.findByIdAndUserIdForUpdate(99L, 10L)).thenReturn(Optional.of(order));
        when(paymentMethodRepository.findById(1L)).thenReturn(Optional.of(card));
        when(paymentRepository.existsByOrderIdAndPaymentStatusIn(anyLong(), anyCollection())).thenReturn(true);

        assertThatThrownBy(() -> paymentService.createPayment(EMAIL, 99L, request()))
                .isInstanceOf(ConflictException.class);
        verify(paymentGateway, never()).initiate(any(), any(), any());
    }

    // Status updates

    private Payment payment(PaymentStatus status) {
        return Payment.builder().id(5L).order(order).paymentMethod(card).paymentStatus(status)
                .amount(order.getTotalAmount()).build();
    }

    /** Stubs the lock-then-load path: payment id -> order id, locked order, then the payment. */
    private void stub(Payment payment) {
        lenient().when(paymentRepository.findOrderIdById(5L)).thenReturn(Optional.of(99L));
        lenient().when(orderRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(order));
        lenient().when(paymentRepository.findById(5L)).thenReturn(Optional.of(payment));
    }

    @Test
    void completedPaymentMovesPendingOrderToProcessing() {
        Payment payment = payment(PaymentStatus.PENDING);
        stub(payment);

        paymentService.updatePaymentStatus(5L, "completed");

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PROCESSING);
    }

    @Test
    void completedPaymentLeavesAnAlreadyAdvancedOrderAlone() {
        order.setOrderStatus(OrderStatus.SHIPPED);
        Payment payment = payment(PaymentStatus.PENDING);
        stub(payment);

        paymentService.updatePaymentStatus(5L, "COMPLETED");

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void statusChangeLocksTheOrderBeforeLoadingThePayment() {
        stub(payment(PaymentStatus.PENDING));

        paymentService.updatePaymentStatus(5L, "FAILED");

        InOrder lockFirst = inOrder(orderRepository, paymentRepository);
        lockFirst.verify(orderRepository).findByIdForUpdate(99L);
        lockFirst.verify(paymentRepository).findById(5L);
    }

    // S1: a cancelled or refunded order cannot be paid

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"CANCELLED", "REFUNDED"})
    void paymentOfClosedOrderCannotComplete(OrderStatus closed) {
        order.setOrderStatus(closed);
        Payment payment = payment(PaymentStatus.PENDING);
        stub(payment);

        assertThatThrownBy(() -> paymentService.updatePaymentStatus(5L, "COMPLETED"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Order is " + closed + "; its payment cannot be completed");
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(order.getOrderStatus()).isEqualTo(closed);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void completingPaymentAfterOrderWasCancelledIsAConflict() {
        // State left by cancelling the order: the pending payment was failed
        order.setOrderStatus(OrderStatus.CANCELLED);
        Payment payment = payment(PaymentStatus.FAILED);
        stub(payment);

        assertThatThrownBy(() -> paymentService.updatePaymentStatus(5L, "COMPLETED"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Order is CANCELLED; its payment cannot be completed");
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void unknownPaymentIsNotFound() {
        when(paymentRepository.findOrderIdById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.updatePaymentStatus(5L, "COMPLETED"))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Payment not found");
    }

    // S2: refunding a payment refunds its delivered order

    @Test
    void refundingPaymentOfDeliveredOrderRefundsTheOrder() {
        order.setOrderStatus(OrderStatus.DELIVERED);
        Payment payment = payment(PaymentStatus.COMPLETED);
        stub(payment);

        paymentService.updatePaymentStatus(5L, "REFUNDED");

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.REFUNDED);
        verify(orderRepository).save(order);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PENDING", "PROCESSING", "SHIPPED"})
    void refundingPaymentOfUndeliveredOrderIsRejected(OrderStatus status) {
        order.setOrderStatus(status);
        Payment payment = payment(PaymentStatus.COMPLETED);
        stub(payment);

        assertThatThrownBy(() -> paymentService.updatePaymentStatus(5L, "REFUNDED"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Only payments of delivered orders can be refunded; cancel the order instead");
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(order.getOrderStatus()).isEqualTo(status);
    }

    @Test
    void failedPaymentLeavesOrderPending() {
        Payment payment = payment(PaymentStatus.PENDING);
        stub(payment);

        paymentService.updatePaymentStatus(5L, "FAILED");

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @ParameterizedTest
    @CsvSource({"PENDING,REFUNDED", "FAILED,COMPLETED", "REFUNDED,COMPLETED", "COMPLETED,PENDING", "PENDING,PENDING"})
    void rejectedTransitions(PaymentStatus from, PaymentStatus to) {
        Payment payment = payment(from);
        stub(payment);

        assertThatThrownBy(() -> paymentService.updatePaymentStatus(5L, to.name()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(payment.getPaymentStatus()).isEqualTo(from);
    }

    @Test
    void unknownStatusIsRejectedWithCleanMessage() {
        stub(payment(PaymentStatus.PENDING));

        assertThatThrownBy(() -> paymentService.updatePaymentStatus(5L, "PAID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid payment status: PAID");
    }
}

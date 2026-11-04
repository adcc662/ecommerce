package com.example.ecommerce.repository;

import com.example.ecommerce.models.entity.Payment;
import com.example.ecommerce.models.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByOrderId(Long orderId);

    /** The order id alone, so callers can lock the order before loading the payment. */
    @Query("SELECT p.order.id FROM Payment p WHERE p.id = :id")
    Optional<Long> findOrderIdById(@Param("id") Long id);

    boolean existsByOrderIdAndPaymentStatusIn(Long orderId, Collection<PaymentStatus> statuses);
    Optional<Payment> findByTransactionId(String transactionId);
    List<Payment> findByPaymentStatus(PaymentStatus paymentStatus);
}

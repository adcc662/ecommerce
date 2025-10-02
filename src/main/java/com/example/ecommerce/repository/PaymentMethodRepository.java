package com.example.ecommerce.repository;

import com.example.ecommerce.models.entity.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {
    Optional<PaymentMethod> findByMethodName(String methodName);
    List<PaymentMethod> findByIsActiveTrue();
}

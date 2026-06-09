package com.example.ecommerce.repository;

import com.example.ecommerce.models.entity.Order;
import com.example.ecommerce.models.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderNumber(String orderNumber);
    Optional<Order> findByIdAndUserId(Long id, Long userId);
    List<Order> findByUserId(Long userId);
    List<Order> findByOrderStatus(OrderStatus orderStatus);
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
}

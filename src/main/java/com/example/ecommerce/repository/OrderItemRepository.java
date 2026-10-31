package com.example.ecommerce.repository;

import com.example.ecommerce.models.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderId(Long orderId);

    @Query("SELECT i FROM OrderItem i JOIN FETCH i.product WHERE i.order.id IN :orderIds")
    List<OrderItem> findWithProductByOrderIdIn(@Param("orderIds") Collection<Long> orderIds);
    List<OrderItem> findByProductId(Long productId);
}

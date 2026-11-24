package com.example.ecommerce.repository;

import com.example.ecommerce.models.entity.Order;
import com.example.ecommerce.models.enums.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderNumber(String orderNumber);
    Optional<Order> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id AND o.user.id = :userId")
    Optional<Order> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);
    List<Order> findByUserId(Long userId);
    List<Order> findByOrderStatus(OrderStatus orderStatus);
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndOrderStatus(Long userId, OrderStatus orderStatus);

    /** Ids of unpaid orders created before the cutoff, in id order after afterId (keyset paging). */
    @Query("SELECT o.id FROM Order o WHERE o.orderStatus = com.example.ecommerce.models.enums.OrderStatus.PENDING "
            + "AND o.createdAt < :cutoff AND o.id > :afterId ORDER BY o.id")
    List<Long> findPendingIdsCreatedBefore(@Param("cutoff") LocalDateTime cutoff, @Param("afterId") long afterId,
                                           Pageable pageable);
}

package com.example.ecommerce.service;

import com.example.ecommerce.config.OrderProperties;
import com.example.ecommerce.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cancels unpaid orders older than app.orders.pending-ttl so the stock they hold returns to sale.
 * Each order is cancelled in its own transaction under a row lock, so the job is safe to run on
 * several instances at once and a payment completing at the same moment is never lost.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnpaidOrderExpiryJob {

    static final int PAGE_SIZE = 100;

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final OrderProperties orderProperties;

    @Scheduled(fixedDelayString = "${app.orders.expiry-check-interval-ms:60000}",
            initialDelayString = "${app.orders.expiry-check-interval-ms:60000}")
    public void run() {
        int expired = expireUnpaidOrders();
        if (expired > 0) {
            log.info("Cancelled {} unpaid orders older than {}", expired, orderProperties.pendingTtl());
        }
    }

    /** Returns how many orders were cancelled. */
    public int expireUnpaidOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minus(orderProperties.pendingTtl());
        int expired = 0;
        long afterId = 0;
        List<Long> ids;
        do {
            // Keyset paging by id: a failing order is skipped instead of being fetched again forever
            ids = orderRepository.findPendingIdsCreatedBefore(cutoff, afterId, PageRequest.ofSize(PAGE_SIZE));
            for (Long id : ids) {
                try {
                    if (orderService.expireUnpaidOrder(id, cutoff)) {
                        expired++;
                    }
                } catch (RuntimeException ex) {
                    log.error("Could not cancel expired order {}: {}", id, ex.getMessage());
                }
            }
            if (!ids.isEmpty()) {
                afterId = ids.getLast();
            }
        } while (ids.size() == PAGE_SIZE);
        return expired;
    }
}

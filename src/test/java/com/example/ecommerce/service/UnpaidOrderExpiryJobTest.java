package com.example.ecommerce.service;

import com.example.ecommerce.config.OrderProperties;
import com.example.ecommerce.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UnpaidOrderExpiryJobTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OrderService orderService = mock(OrderService.class);
    private final UnpaidOrderExpiryJob job = new UnpaidOrderExpiryJob(
            orderRepository, orderService, new OrderProperties(Duration.ofMinutes(30), 3));

    private static List<Long> ids(long fromInclusive, long toInclusive) {
        return LongStream.rangeClosed(fromInclusive, toInclusive).boxed().toList();
    }

    @Test
    void expiresEveryPendingOrderOlderThanTheTtlPageByPage() {
        when(orderRepository.findPendingIdsCreatedBefore(any(), eq(0L), any(Pageable.class))).thenReturn(ids(1, 100));
        when(orderRepository.findPendingIdsCreatedBefore(any(), eq(100L), any(Pageable.class))).thenReturn(ids(101, 102));

        LocalDateTime before = LocalDateTime.now().minusMinutes(30);
        int expired = job.expireUnpaidOrders();
        LocalDateTime after = LocalDateTime.now().minusMinutes(30);

        assertThat(expired).isZero(); // the mocked service reports nothing actually cancelled
        for (long id = 1; id <= 102; id++) {
            verify(orderService).expireUnpaidOrder(eq(id), any());
        }
        var cutoff = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderRepository).findPendingIdsCreatedBefore(cutoff.capture(), eq(0L), any(Pageable.class));
        assertThat(cutoff.getValue()).isBetween(before, after);
    }

    @Test
    void oneFailingOrderDoesNotStopTheOthers() {
        when(orderRepository.findPendingIdsCreatedBefore(any(), eq(0L), any(Pageable.class))).thenReturn(List.of(1L, 2L));
        doThrow(new RuntimeException("boom")).when(orderService).expireUnpaidOrder(eq(1L), any());
        when(orderService.expireUnpaidOrder(eq(2L), any())).thenReturn(true);

        assertThat(job.expireUnpaidOrders()).isEqualTo(1);
        verify(orderService).expireUnpaidOrder(eq(2L), any());
    }
}

package com.example.backend.order.application.command;
import com.example.backend.order.application.OrderQuoteService;
import com.example.backend.order.application.port.OrderCatalog;
import com.example.backend.order.application.port.OrderRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class PlaceOrderServiceTest {
    @Test
    void creationTimeUsesInjectedClockAndStoresTheCalculatedSnapshot() {
        var catalog = mock(OrderCatalog.class);
        when(catalog.memberExists(1)).thenReturn(true);
        when(catalog.product(2)).thenReturn(Optional.of(new OrderCatalog.ProductSnapshot(2, "Item", new BigDecimal("3.25"), "USD")));
        var repository = mock(OrderRepository.class);
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        var clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        var order = new PlaceOrderService(new OrderQuoteService(catalog), repository, clock).place(1, 2, 3, "alice");
        assertThat(order.createdAt()).isEqualTo(clock.instant());
        assertThat(order.quote().totalPrice()).isEqualByComparingTo("9.75");
        assertThat(order.ownerSubject()).isEqualTo("alice");
    }
}

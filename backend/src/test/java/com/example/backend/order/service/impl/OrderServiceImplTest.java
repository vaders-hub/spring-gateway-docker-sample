package com.example.backend.order.service.impl;

import com.example.backend.member.service.MemberService;
import com.example.backend.product.service.ProductService;
import com.example.backend.product.service.ProductSnapshot;
import com.example.backend.order.error.OrderErrorCode;
import com.example.backend.order.repository.OrderRepository;
import com.example.platform.code.CommonErrorCode;
import com.example.platform.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// 기존 견적·생성·기능 간 adapter 테스트의 행동 계약을 통합한 서비스에서 검증한다.
class OrderServiceImplTest {
    private final MemberService members = mock(MemberService.class);
    private final ProductService products = mock(ProductService.class);
    private final OrderRepository repository = mock(OrderRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final OrderServiceImpl service = new OrderServiceImpl(members, products, repository, clock);

    @Test
    void missingMemberIsAReferenceErrorAndStopsBeforeProductLookup() {
        assertThatThrownBy(() -> service.preview(99, 1, 2))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(OrderErrorCode.INVALID_MEMBER_REFERENCE));
        verify(members).exists(99);
        verifyNoInteractions(products, repository);
    }

    @Test
    void missingProductIsAReferenceError() {
        when(members.exists(1)).thenReturn(true);
        when(products.findProduct(99)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.preview(1, 99, 1))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(OrderErrorCode.INVALID_PRODUCT_REFERENCE));
        verify(products, never()).get(anyLong());
        verifyNoInteractions(repository);
    }

    @Test
    void previewUsesPublishedSnapshotAndPreservesMoneyAndCurrencyWithoutSaving() {
        when(members.exists(3)).thenReturn(true);
        when(products.findProduct(7)).thenReturn(Optional.of(new ProductSnapshot(7, "Sample", new BigDecimal("0.10"), "USD")));
        var quote = service.preview(3, 7, 3);
        assertThat(quote.productId()).isEqualTo(7);
        assertThat(quote.productName()).isEqualTo("Sample");
        assertThat(quote.currency()).isEqualTo("USD");
        assertThat(quote.totalPrice()).isEqualByComparingTo("0.30");
        verify(members).exists(3);
        verifyNoInteractions(repository);
    }

    @Test
    void creationTimeUsesInjectedClockAndStoresTheCalculatedSnapshot() {
        when(members.exists(1)).thenReturn(true);
        when(products.findProduct(2)).thenReturn(Optional.of(new ProductSnapshot(2, "Item", new BigDecimal("3.25"), "USD")));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        var order = service.place(1, 2, 3, "alice");
        assertThat(order.createdAt()).isEqualTo(clock.instant());
        assertThat(order.quote().totalPrice()).isEqualByComparingTo("9.75");
        assertThat(order.ownerSubject()).isEqualTo("alice");
        assertThat(order.id()).isNotNull();
        verify(repository).save(order);
    }

    @Test
    void invalidOwnerStopsBeforeLookingUpCatalogOrSaving() {
        for (String owner : new String[] {null, "", " ", "a".repeat(256)}) {
            assertThatThrownBy(() -> service.place(1, 2, 3, owner))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.code()).isEqualTo(CommonErrorCode.INVALID_REQUEST));
        }
        verifyNoInteractions(members, products, repository);
    }

    @Test
    void lookupAlwaysScopesToOwnerAndHidesMissingOrOtherOwnersOrders() {
        var id = UUID.randomUUID();
        assertThatThrownBy(() -> service.get(id, "bob"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(OrderErrorCode.ORDER_NOT_FOUND));
        verify(repository).findByIdAndOwnerSubject(id, "bob");
        verifyNoInteractions(members, products);
    }
}

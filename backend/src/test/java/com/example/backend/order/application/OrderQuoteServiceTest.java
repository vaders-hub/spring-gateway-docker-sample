package com.example.backend.order.application;
import com.example.backend.order.application.port.OrderCatalog;
import com.example.backend.order.application.error.OrderErrorCode;
import com.example.platform.exception.BusinessException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class OrderQuoteServiceTest {
    @Test
    void missingMemberIsAReferenceErrorAndStopsBeforeProductLookup() {
        var catalog = mock(OrderCatalog.class);
        assertThatThrownBy(() -> new OrderQuoteService(catalog).preview(99, 1, 2))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(OrderErrorCode.INVALID_MEMBER_REFERENCE));
        verify(catalog, never()).product(anyLong());
    }
    @Test
    void missingProductIsAReferenceError() {
        var catalog = mock(OrderCatalog.class);
        when(catalog.memberExists(1)).thenReturn(true);
        when(catalog.product(99)).thenReturn(java.util.Optional.empty());
        assertThatThrownBy(() -> new OrderQuoteService(catalog).preview(1, 99, 1))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(OrderErrorCode.INVALID_PRODUCT_REFERENCE));
    }
}

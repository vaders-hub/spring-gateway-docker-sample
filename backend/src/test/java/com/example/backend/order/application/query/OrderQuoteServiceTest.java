package com.example.backend.order.application.query;

import com.example.backend.order.application.port.OrderCatalog;
import com.example.backend.common.code.ErrorCode;
import com.example.backend.common.exception.BusinessException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderQuoteServiceTest {
    @Test
    void missingMemberStopsBeforeProductLookup() {
        var catalog = mock(OrderCatalog.class);
        doThrow(new BusinessException(ErrorCode.NOT_FOUND)).when(catalog).requireMember(99);
        assertThatThrownBy(() -> new OrderQuoteService(catalog).preview(99, 1, 2))
                .isInstanceOf(BusinessException.class);
        verify(catalog, never()).product(anyLong());
    }
    @Test
    void applicationRejectsInvalidInputWithoutAnyLookup() {
        var catalog = mock(OrderCatalog.class);
        assertThatThrownBy(() -> new OrderQuoteService(catalog).preview(1, 1, 0))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(catalog);
    }
}

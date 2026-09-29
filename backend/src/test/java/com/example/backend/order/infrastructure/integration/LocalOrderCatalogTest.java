package com.example.backend.order.infrastructure.integration;

import com.example.backend.common.code.ErrorCode;
import com.example.backend.common.exception.BusinessException;
import com.example.backend.member.contract.MemberLookup;
import com.example.backend.product.contract.ProductLookup;
import com.example.backend.product.contract.ProductSnapshot;
import com.example.backend.order.application.query.OrderQuoteService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalOrderCatalogTest {
    @Test
    void quoteUsesPublishedContractsAndPreservesMoneyAndCurrency() {
        var members = mock(MemberLookup.class);
        var products = mock(ProductLookup.class);
        when(products.getProduct(7)).thenReturn(new ProductSnapshot(7, "Sample", new BigDecimal("0.10"), "USD"));
        var service = new OrderQuoteService(new LocalOrderCatalog(members, products));
        var quote = service.preview(3, 7, 3);
        verify(members).requireExists(3);
        assertThat(quote.productId()).isEqualTo(7);
        assertThat(quote.productName()).isEqualTo("Sample");
        assertThat(quote.currency()).isEqualTo("USD");
        assertThat(quote.totalPrice()).isEqualByComparingTo("0.30");
    }
    @Test
    void failedMemberContractStopsTheCrossFeatureWorkflow() {
        var members = mock(MemberLookup.class);
        var products = mock(ProductLookup.class);
        doThrow(new BusinessException(ErrorCode.NOT_FOUND)).when(members).requireExists(99);
        var service = new OrderQuoteService(new LocalOrderCatalog(members, products));
        assertThatThrownBy(() -> service.preview(99, 7, 1)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(products);
    }
}

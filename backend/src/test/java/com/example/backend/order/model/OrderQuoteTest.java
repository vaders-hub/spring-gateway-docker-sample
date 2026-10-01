package com.example.backend.order.model;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OrderQuoteTest {
    @Test
    void moneyCalculationIsExactAndQuantityIsBounded() {
        var quote = new OrderQuote(1, 1, "Item", 3, new BigDecimal("0.10"), "USD");
        assertThat(quote.totalPrice()).isEqualByComparingTo("0.30");
        for (int quantity : new int[]{0, -1, 101}) {
            assertThatIllegalArgumentException().isThrownBy(
                    () -> new OrderQuote(1, 1, "Item", quantity, BigDecimal.ONE, "KRW"));
        }
    }
}

package com.example.backend.order.model;

import java.util.List;

public record OrderSearchResult(List<OrderSummary> items, long total, int page, int size) {
    public OrderSearchResult { items = List.copyOf(items); }
}

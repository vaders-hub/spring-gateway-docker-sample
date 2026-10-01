package com.example.backend.order.repository;

import com.example.backend.order.model.OrderSearchCriteria;
import com.example.backend.order.model.OrderSummary;
import java.util.List;

public interface OrderSearchRepository {
    List<OrderSummary> search(OrderSearchCriteria criteria, String ownerSubject);
    long count(OrderSearchCriteria criteria, String ownerSubject);
}

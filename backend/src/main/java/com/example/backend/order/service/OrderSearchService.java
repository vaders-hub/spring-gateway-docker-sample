package com.example.backend.order.service;

import com.example.backend.order.model.OrderSearchCriteria;
import com.example.backend.order.model.OrderSearchResult;

public interface OrderSearchService {
    OrderSearchResult search(OrderSearchCriteria criteria, String ownerSubject);
}

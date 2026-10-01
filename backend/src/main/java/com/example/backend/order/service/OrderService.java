package com.example.backend.order.service;

import com.example.backend.order.model.OrderQuote;
import com.example.backend.order.model.StoredOrder;
import java.util.UUID;

public interface OrderService {
    OrderQuote preview(long memberId, long productId, int quantity);
    StoredOrder place(long memberId, long productId, int quantity, String ownerSubject);
    StoredOrder get(UUID id, String ownerSubject);
}

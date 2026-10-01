package com.example.backend.order.web.dto.response;

import java.time.Instant;
import java.util.UUID;

public record OrderResponse(UUID id, OrderPreviewResponse quote, Instant createdAt) {}

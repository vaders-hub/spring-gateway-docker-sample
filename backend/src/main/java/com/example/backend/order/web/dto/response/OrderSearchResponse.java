package com.example.backend.order.web.dto.response;

import java.util.List;

public record OrderSearchResponse(List<OrderSearchItemResponse> items, long total, int page, int size) {}

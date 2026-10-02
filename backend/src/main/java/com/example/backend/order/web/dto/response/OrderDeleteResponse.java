package com.example.backend.order.web.dto.response;

import java.util.UUID;

public record OrderDeleteResponse(UUID id, boolean deleted) {}

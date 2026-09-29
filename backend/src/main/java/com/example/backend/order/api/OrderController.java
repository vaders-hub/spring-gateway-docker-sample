package com.example.backend.order.api;

import com.example.backend.common.code.SuccessCode;
import com.example.backend.common.config.ConditionalOnLearningMock;
import com.example.backend.common.response.*;
import com.example.backend.common.web.RequestContext;
import com.example.backend.order.api.dto.*;
import com.example.backend.order.application.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnLearningMock
@RequestMapping("/orders")
public class OrderController {
    private final OrderService service;
    private final OrderMapper mapper;
    public OrderController(OrderService service, OrderMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }
    @PostMapping("/preview")
    @Operation(summary = "주문 견적 계산 (저장·결제 없음)")
    public ResponseEntity<ApiResponse<OrderPreviewResponse>> preview(@Valid @RequestBody OrderPreviewRequest request,
            @RequestAttribute(RequestContext.REQUEST_ID_ATTRIBUTE) String requestId) {
        var result = service.preview(request.memberId(), request.productId(), request.quantity());
        return ApiResponses.success(SuccessCode.OK, mapper.toResponse(result), requestId);
    }
}

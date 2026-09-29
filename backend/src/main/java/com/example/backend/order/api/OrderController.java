package com.example.backend.order.api;

import com.example.backend.common.security.permission.RequireWrite;
import com.example.backend.common.code.SuccessCode;
import com.example.backend.common.config.ConditionalOnLearningFeature;
import com.example.backend.common.response.*;
import com.example.backend.common.web.RequestContext;
import com.example.backend.order.api.dto.request.*;
import com.example.backend.order.api.dto.response.*;
import com.example.backend.order.application.query.OrderQuoteService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnLearningFeature
@RequestMapping("/orders")
public class OrderController {
    private final OrderQuoteService service;
    private final OrderMapper mapper;
    public OrderController(OrderQuoteService service, OrderMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }
    // 견적은 부작용 없는 계산이므로 200이다. 실제 저장 POST /orders의 201과 구분한다.
    @RequireWrite
    @PostMapping("/preview")
    @Operation(summary = "주문 견적 계산 (저장·결제 없음)")
    public ResponseEntity<ApiResponse<OrderPreviewResponse>> preview(@Valid @RequestBody OrderPreviewRequest request,
            @RequestAttribute(RequestContext.REQUEST_ID_ATTRIBUTE) String requestId) {
        var result = service.preview(request.memberId(), request.productId(), request.quantity());
        return ApiResponses.success(SuccessCode.OK, mapper.toResponse(result), requestId);
    }
}

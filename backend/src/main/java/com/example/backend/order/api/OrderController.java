package com.example.backend.order.api;

import com.example.backend.common.security.permission.RequireWrite;
import com.example.backend.common.security.permission.RequireRead;
import com.example.backend.order.application.command.PlaceOrderService;
import com.example.backend.order.application.query.OrderQueryService;
import java.security.Principal;
import java.util.UUID;
import com.example.platform.code.SuccessCode;
import com.example.platform.response.*;
import com.example.backend.common.web.RequestContext;
import com.example.backend.order.api.dto.request.*;
import com.example.backend.order.api.dto.response.*;
import com.example.backend.order.application.OrderQuoteService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private final OrderQuoteService service;
    private final OrderMapper mapper;
    private final PlaceOrderService commands;
    private final OrderQueryService queries;
    public OrderController(OrderQuoteService service, OrderMapper mapper, PlaceOrderService commands, OrderQueryService queries) {
        this.commands = commands;
        this.queries = queries;
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
    @RequireWrite
    @PostMapping
    @Operation(summary = "주문 저장 (결제·재고 차감 없음)")
    public ResponseEntity<ApiResponse<OrderResponse>> create(@Valid @RequestBody OrderCreateRequest request,
            Principal principal, @RequestAttribute(RequestContext.REQUEST_ID_ATTRIBUTE) String requestId) {
        // RequestId는 HTTP 응답 추적용이다. 업무 서비스에는 JWT 소유자와 업무 값만 전달한다.
        var order = commands.place(request.memberId(), request.productId(), request.quantity(), principal.getName());
        return ApiResponses.success(SuccessCode.CREATED, mapper.toResponse(order), requestId);
    }
    @RequireRead
    @GetMapping("/{id}")
    @Operation(summary = "본인 주문 조회")
    public ResponseEntity<ApiResponse<OrderResponse>> get(@PathVariable UUID id, Principal principal,
            @RequestAttribute(RequestContext.REQUEST_ID_ATTRIBUTE) String requestId) {
        return ApiResponses.success(SuccessCode.OK, mapper.toResponse(queries.get(id, principal.getName())), requestId);
    }
}

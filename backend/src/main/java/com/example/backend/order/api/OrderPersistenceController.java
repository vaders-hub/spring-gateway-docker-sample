package com.example.backend.order.api;

import com.example.backend.common.code.SuccessCode;
import com.example.backend.common.config.ConditionalOnLearningPersistence;
import com.example.backend.common.response.*;
import com.example.backend.common.web.RequestContext;
import com.example.backend.order.api.dto.request.*;
import com.example.backend.order.api.dto.response.*;
import com.example.backend.order.application.command.PlaceOrderService;
import com.example.backend.order.application.query.OrderQueryService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnLearningPersistence
@RequestMapping("/orders")
public class OrderPersistenceController {
    private final PlaceOrderService service;
    private final OrderMapper mapper;
    private final OrderQueryService queries;
    public OrderPersistenceController(PlaceOrderService service, OrderQueryService queries, OrderMapper mapper) {
        this.queries = queries;
        this.service = service;
        this.mapper = mapper;
    }
    @PostMapping
    @Operation(summary = "주문 저장 (결제·재고 차감 없음)")
    public ResponseEntity<ApiResponse<OrderResponse>> create(@Valid @RequestBody OrderCreateRequest request,
            Principal principal, @RequestAttribute(RequestContext.REQUEST_ID_ATTRIBUTE) String requestId) {
        // RequestId는 HTTP 응답 추적용이다. 업무 서비스에는 JWT 소유자와 업무 값만 전달한다.
        var order = service.place(request.memberId(), request.productId(), request.quantity(), principal.getName());
        return ApiResponses.success(SuccessCode.CREATED, mapper.toResponse(order), requestId);
    }
    @GetMapping("/{id}")
    @Operation(summary = "본인 주문 조회")
    public ResponseEntity<ApiResponse<OrderResponse>> get(@PathVariable UUID id, Principal principal,
            @RequestAttribute(RequestContext.REQUEST_ID_ATTRIBUTE) String requestId) {
        return ApiResponses.success(SuccessCode.OK, mapper.toResponse(queries.get(id, principal.getName())), requestId);
    }
}

package com.example.backend.order.web;

import com.example.backend.common.security.permission.RequireRead;
import com.example.backend.common.web.RequestContext;
import com.example.backend.order.service.OrderSearchService;
import com.example.backend.order.web.dto.request.OrderSearchRequest;
import com.example.backend.order.web.dto.response.OrderSearchResponse;
import com.example.platform.code.SuccessCode;
import com.example.platform.response.ApiResponse;
import com.example.platform.response.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("mybatis")
@RequestMapping("/orders")
class OrderSearchController {
    private final OrderSearchService service;
    private final OrderDtoConverter converter;
    OrderSearchController(OrderSearchService service, OrderDtoConverter converter) {
        this.service = service;
        this.converter = converter;
    }
    @RequireRead
    @GetMapping
    @Operation(summary = "본인 주문 조건 검색 (MyBatis JOIN·페이징 예제)")
    public ResponseEntity<ApiResponse<OrderSearchResponse>> search(@Valid @ModelAttribute OrderSearchRequest request,
            Principal principal, @RequestAttribute(RequestContext.REQUEST_ID_ATTRIBUTE) String requestId) {
        return ApiResponses.success(SuccessCode.OK, converter.toResponse(service.search(request.toCriteria(), principal.getName())), requestId);
    }
}

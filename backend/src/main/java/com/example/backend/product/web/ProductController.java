package com.example.backend.product.web;

import com.example.backend.common.security.permission.RequireRead;
import com.example.platform.code.SuccessCode;
import com.example.platform.response.*;
import com.example.backend.common.web.RequestContext;
import com.example.backend.product.service.ProductService;
import com.example.backend.product.web.dto.ProductResponse;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/products")
public class ProductController {
    private final ProductService service;
    private final ProductDtoConverter mapper;
    public ProductController(ProductService service, ProductDtoConverter mapper) {
        this.service = service;
        this.mapper = mapper;
    }
    // HTTP 입력/권한은 웹 계층, 업무 조회는 Service, 응답 형태 변환은 MapStruct가 담당한다.
    @RequireRead
    @GetMapping
    @Operation(summary = "학습용 product 목록 조회")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> list(
            @RequestAttribute(RequestContext.REQUEST_ID_ATTRIBUTE) String requestId) {
        return ApiResponses.success(SuccessCode.OK, mapper.toResponses(service.list()), requestId);
    }
    @RequireRead
    @GetMapping("/{id}")
    @Operation(summary = "학습용 product 단건 조회")
    public ResponseEntity<ApiResponse<ProductResponse>> get(@PathVariable long id,
            @RequestAttribute(RequestContext.REQUEST_ID_ATTRIBUTE) String requestId) {
        return ApiResponses.success(SuccessCode.OK, mapper.toResponse(service.get(id)), requestId);
    }
}

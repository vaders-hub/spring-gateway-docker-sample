package com.example.backend.product.api;

import com.example.backend.common.config.ConditionalOnLearningMock;
import com.example.backend.common.code.SuccessCode;
import com.example.backend.common.response.*;
import com.example.backend.common.web.RequestContext;
import com.example.backend.product.application.ProductService;
import com.example.backend.product.api.dto.ProductResponse;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnLearningMock
@RequestMapping("/products")
public class ProductController {
    private final ProductService service;
    private final ProductMapper mapper;
    public ProductController(ProductService service, ProductMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }
    @GetMapping
    @Operation(summary = "학습용 product 목록 조회")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> list(
            @RequestAttribute(RequestContext.REQUEST_ID_ATTRIBUTE) String requestId) {
        return ApiResponses.success(SuccessCode.OK, mapper.toResponses(service.list()), requestId);
    }
    @GetMapping("/{id}")
    @Operation(summary = "학습용 product 단건 조회")
    public ResponseEntity<ApiResponse<ProductResponse>> get(@PathVariable long id,
            @RequestAttribute(RequestContext.REQUEST_ID_ATTRIBUTE) String requestId) {
        return ApiResponses.success(SuccessCode.OK, mapper.toResponse(service.get(id)), requestId);
    }
}

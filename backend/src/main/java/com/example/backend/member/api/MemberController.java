package com.example.backend.member.api;

import com.example.backend.common.config.ConditionalOnLearningFeature;
import com.example.backend.common.code.SuccessCode;
import com.example.backend.common.response.*;
import com.example.backend.common.web.RequestContext;
import com.example.backend.member.application.MemberService;
import com.example.backend.member.api.dto.MemberResponse;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnLearningFeature
@RequestMapping("/members")
public class MemberController {
    private final MemberService service;
    private final MemberMapper mapper;
    public MemberController(MemberService service, MemberMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }
    // HTTP 입력/권한은 웹 계층, 업무 조회는 Service, 응답 형태 변환은 MapStruct가 담당한다.
    @GetMapping
    @Operation(summary = "학습용 member 목록 조회")
    public ResponseEntity<ApiResponse<List<MemberResponse>>> list(
            @RequestAttribute(RequestContext.REQUEST_ID_ATTRIBUTE) String requestId) {
        return ApiResponses.success(SuccessCode.OK, mapper.toResponses(service.list()), requestId);
    }
    @GetMapping("/{id}")
    @Operation(summary = "학습용 member 단건 조회")
    public ResponseEntity<ApiResponse<MemberResponse>> get(@PathVariable long id,
            @RequestAttribute(RequestContext.REQUEST_ID_ATTRIBUTE) String requestId) {
        return ApiResponses.success(SuccessCode.OK, mapper.toResponse(service.get(id)), requestId);
    }
}

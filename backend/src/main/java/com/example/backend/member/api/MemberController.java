package com.example.backend.member.api;

import com.example.backend.common.config.ConditionalOnLearningFeature;
import com.example.backend.common.code.SuccessCode;
import com.example.backend.common.response.*;
import com.example.backend.common.web.RequestContext;
import com.example.backend.member.application.MemberService;
import com.example.backend.member.api.dto.MemberResponse;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import jakarta.validation.constraints.Positive;
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
    // 같은 조회 결과를 재사용한다. /admin은 SecurityConfig에서 더 높은 권한을 요구한다.
    // 관리자 전용 반환 항목/업무가 생기면 별도 DTO와 application 유스케이스로 분리한다.
    @GetMapping({"", "/admin"})
    @Operation(summary = "회원 목록 조회 (관리 경로는 api.read와 member.admin 모두 필요)")
    public ResponseEntity<ApiResponse<List<MemberResponse>>> list(
            @RequestAttribute(RequestContext.REQUEST_ID_ATTRIBUTE) String requestId) {
        return ApiResponses.success(SuccessCode.OK, mapper.toResponses(service.list()), requestId);
    }
    @GetMapping("/{id}")
    @Operation(summary = "학습용 member 단건 조회")
    public ResponseEntity<ApiResponse<MemberResponse>> get(@PathVariable @Positive long id,
            @RequestAttribute(RequestContext.REQUEST_ID_ATTRIBUTE) String requestId) {
        return ApiResponses.success(SuccessCode.OK, mapper.toResponse(service.get(id)), requestId);
    }
}

package com.example.backend.member.api;

import com.example.backend.common.security.permission.RequireRead;
import com.example.platform.code.SuccessCode;
import com.example.platform.response.*;
import com.example.backend.common.web.RequestContext;
import com.example.backend.member.application.MemberService;
import com.example.backend.member.api.RequireMemberAdmin;
import com.example.backend.member.api.dto.MemberResponse;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/members")
public class MemberController {
    private final MemberService service;
    private final MemberMapper mapper;
    public MemberController(MemberService service, MemberMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }
    // HTTP 입력/권한은 웹 계층, 업무 조회는 Service, 응답 형태 변환은 MapStruct가 담당한다.
    // 일반/관리 진입점은 나누고 아래 응답 매핑과 Service를 공유한다.
    // 관리자 전용 반환 항목/업무가 생기면 별도 DTO와 application 유스케이스로 분리한다.
    @RequireRead
    @GetMapping
    @Operation(summary = "회원 목록 조회 (관리 경로는 api.read와 member.admin 모두 필요)")
    public ResponseEntity<ApiResponse<List<MemberResponse>>> list(
            @RequestAttribute(RequestContext.REQUEST_ID_ATTRIBUTE) String requestId) {
        return memberList(requestId);
    }
    @RequireMemberAdmin
    @GetMapping("/admin")
    @Operation(summary = "관리자 회원 목록 (api.read + member.admin)")
    public ResponseEntity<ApiResponse<List<MemberResponse>>> admin(
            @RequestAttribute(RequestContext.REQUEST_ID_ATTRIBUTE) String requestId) {
        return memberList(requestId);
    }
    private ResponseEntity<ApiResponse<List<MemberResponse>>> memberList(String requestId) {
        return ApiResponses.success(SuccessCode.OK, mapper.toResponses(service.list()), requestId);
    }
    @RequireRead
    @GetMapping("/{id}")
    @Operation(summary = "학습용 member 단건 조회")
    public ResponseEntity<ApiResponse<MemberResponse>> get(@PathVariable @Positive long id,
            @RequestAttribute(RequestContext.REQUEST_ID_ATTRIBUTE) String requestId) {
        return ApiResponses.success(SuccessCode.OK, mapper.toResponse(service.get(id)), requestId);
    }
}

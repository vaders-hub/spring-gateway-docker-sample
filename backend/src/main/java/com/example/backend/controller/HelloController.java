package com.example.backend.controller;

import com.example.backend.common.api.ApiResponse;
import com.example.backend.common.api.ApiResponses;
import com.example.backend.common.api.SuccessCode;
import com.example.backend.common.web.RequestContext;
import com.example.backend.dto.EchoRequest;
import com.example.backend.dto.EchoResponse;
import com.example.backend.dto.HelloResponse;
import com.example.backend.service.HelloService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    private final HelloService helloService;

    public HelloController(HelloService helloService) {
        this.helloService = helloService;
    }

    // 1단계: Gateway의 /api/hello가 StripPrefix 이후 /hello로 도달한다.
    // 사용자는 X-Gateway-User가 아니라 Backend Security가 검증한 Principal에서 얻는다.
    @GetMapping("/hello")
    public ResponseEntity<ApiResponse<HelloResponse>> hello(
            @RequestAttribute(RequestContext.REQUEST_ID_ATTRIBUTE) String requestId,
            Principal principal) {
        var result = helloService.hello(principal.getName());
        return ApiResponses.success(SuccessCode.OK,
                new HelloResponse(result.service(), result.message(), result.time(), result.username()), requestId);
    }

    // @Valid가 EchoRequest 제약을 검사한다. 실패하면 Service 호출 전 400 오류로 처리된다.
    @PostMapping("/echo")
    public ResponseEntity<ApiResponse<EchoResponse>> echo(
            @Valid @RequestBody EchoRequest request,
            @RequestAttribute(RequestContext.REQUEST_ID_ATTRIBUTE) String requestId,
            Principal principal) {
        var result = helloService.echo(request.name(), request.value(), principal.getName());
        return ApiResponses.success(SuccessCode.OK,
                new EchoResponse(result.service(), new EchoResponse.Received(result.name(), result.value()),
                        result.username()), requestId);
    }
}

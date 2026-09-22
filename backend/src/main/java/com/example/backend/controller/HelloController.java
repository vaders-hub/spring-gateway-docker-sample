package com.example.backend.controller;

import com.example.backend.common.api.ApiResponse;
import com.example.backend.common.web.RequestContext;
import com.example.backend.dto.EchoRequest;
import com.example.backend.dto.EchoResponse;
import com.example.backend.dto.HelloResponse;
import com.example.backend.service.HelloService;
import jakarta.validation.Valid;
import java.security.Principal;
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

    @GetMapping("/hello")
    public ApiResponse<HelloResponse> hello(
            @RequestAttribute(RequestContext.REQUEST_ID) String requestId,
            Principal principal) {
        return ApiResponse.success(
                helloService.hello(principal.getName(), requestId),
                requestId);
    }

    @PostMapping("/echo")
    public ApiResponse<EchoResponse> echo(
            @Valid @RequestBody EchoRequest request,
            @RequestAttribute(RequestContext.REQUEST_ID) String requestId,
            Principal principal) {
        return ApiResponse.success(
                helloService.echo(request, principal.getName(), requestId),
                requestId);
    }
}

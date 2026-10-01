package com.example.backend.hello.service.impl;

import com.example.backend.hello.service.HelloService;

import java.time.Clock;
import org.springframework.stereotype.Service;

// 업무 결과만 반환한다. HTTP DTO 변환과 requestId/envelope 처리는 Controller 책임이다.
@Service
public class HelloServiceImpl implements HelloService {
    private final Clock clock;

    public HelloServiceImpl(Clock clock) {
        this.clock = clock;
    }

    public HelloResult hello(String username) {
        return new HelloResult("backend", "Hello through Spring Cloud Gateway", clock.instant(), username);
    }

    public EchoResult echo(String name, Integer value, String username) {
        return new EchoResult("backend", name, value, username);
    }

}

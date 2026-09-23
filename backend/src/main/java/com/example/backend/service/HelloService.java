package com.example.backend.service;

import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

// 업무 결과만 반환한다. HTTP DTO 변환과 requestId/envelope 처리는 Controller 책임이다.
@Service
public class HelloService {
    private final Clock clock;

    public HelloService(Clock clock) {
        this.clock = clock;
    }

    public HelloResult hello(String username) {
        return new HelloResult("backend", "Hello through Spring Cloud Gateway", clock.instant(), username);
    }

    public EchoResult echo(String name, Integer value, String username) {
        return new EchoResult("backend", name, value, username);
    }

    public record HelloResult(String service, String message, Instant time, String username) {}
    public record EchoResult(String service, String name, Integer value, String username) {}
}

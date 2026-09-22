package com.example.backend.service;

import com.example.backend.dto.EchoRequest;
import com.example.backend.dto.EchoResponse;
import com.example.backend.dto.HelloResponse;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;

@Service
public class HelloService {

    public HelloResponse hello(String username, String requestId) {
        return new HelloResponse(
                "backend",
                "Hello through Spring Cloud Gateway",
                OffsetDateTime.now(),
                requestId,
                username);
    }

    public EchoResponse echo(EchoRequest request, String username, String requestId) {
        return new EchoResponse(
                "backend",
                request,
                requestId,
                username);
    }
}

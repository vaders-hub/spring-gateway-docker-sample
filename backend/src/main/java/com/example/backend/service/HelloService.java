package com.example.backend.service;

import com.example.backend.dto.EchoRequest;
import com.example.backend.dto.EchoResponse;
import com.example.backend.dto.HelloResponse;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;

// 1·6단계: HTTP 입출력과 분리된 샘플 업무 계층. Controller가 넘긴 인증 사용자와 요청 ID를 응답에 반영한다.
// 현재 DB/트랜잭션은 없으며 업무 확장 시 이 계층에서 저장소 호출과 트랜잭션 경계를 설계한다.
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

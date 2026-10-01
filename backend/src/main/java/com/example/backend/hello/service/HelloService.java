package com.example.backend.hello.service;

import java.time.Instant;

public interface HelloService {
    HelloResult hello(String username);
    EchoResult echo(String name, Integer value, String username);

    record HelloResult(String service, String message, Instant time, String username) {}
    record EchoResult(String service, String name, Integer value, String username) {}
}

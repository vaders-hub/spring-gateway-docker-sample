package com.example.backend.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class HelloServiceTest {
    @Test
    void businessTimeUsesInjectedClockAndEchoHasNoHttpDependency() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        var service = new HelloService(Clock.fixed(now, ZoneOffset.UTC));
        assertThat(service.hello("alice").time()).isEqualTo(now);
        assertThat(service.echo("sample", 7, "alice"))
                .isEqualTo(new HelloService.EchoResult("backend", "sample", 7, "alice"));
    }
}

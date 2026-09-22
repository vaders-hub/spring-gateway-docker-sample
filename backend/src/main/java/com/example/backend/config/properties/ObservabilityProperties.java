package com.example.backend.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.observability")
public record ObservabilityProperties(boolean prometheusPublic) {
}

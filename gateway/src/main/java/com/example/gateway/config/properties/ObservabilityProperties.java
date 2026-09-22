package com.example.gateway.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.observability")
public record ObservabilityProperties(boolean prometheusPublic) {
}

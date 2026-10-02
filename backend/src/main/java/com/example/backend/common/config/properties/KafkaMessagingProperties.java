package com.example.backend.common.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.messaging.kafka")
public record KafkaMessagingProperties(
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9._-]*") @Size(max = 200) String orderTopic,
        @Min(1) int partitions, @Min(1) int replicas) {}

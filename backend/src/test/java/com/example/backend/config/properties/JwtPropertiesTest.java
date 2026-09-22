package com.example.backend.config.properties;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtPropertiesTest {

    private final Validator validator = Validation
            .buildDefaultValidatorFactory()
            .getValidator();

    @Test
    void acceptsValidIssuerAndSecret() {
        JwtProperties properties = new JwtProperties(
                "local-gateway",
                "gateway-sample-api",
                "01234567890123456789012345678901");

        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    void rejectsBlankAudience() {
        var properties = new JwtProperties("issuer", "", "01234567890123456789012345678901");
        assertThat(validator.validate(properties))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("audience"));
    }

    @Test
    void rejectsShortSecret() {
        JwtProperties properties = new JwtProperties("local-gateway", "gateway-sample-api", "short");

        assertThat(validator.validate(properties))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("secret"));
    }
}

package com.example.backend.common.config.properties;

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
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("keyConfigurationValid"));
    }
    @Test
    void acceptsJwksWithoutSharedSecretAndRejectsMixedTrust() {
        assertThat(validator.validate(new JwtProperties("https://issuer.test", "api", "",
                "https://issuer.test/certs"))).isEmpty();
        assertThat(validator.validate(new JwtProperties("https://issuer.test", "api", "01234567890123456789012345678901",
                "https://issuer.test/certs"))).isNotEmpty();
    }
}

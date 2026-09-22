package com.example.gateway.config.properties;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SecurityPropertiesTest {

    @Test
    void rejectsWildcardOriginAndSubsecondTtl() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new SecurityProperties.Cors(List.of("*"), Duration.ofHours(1)));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new SecurityProperties.Jwt("issuer", "gateway-sample-api", "test-key", Duration.ofMillis(500)));
    }

    @Test
    void redactsSecretsInDiagnosticOutput() {
        var jwt = new SecurityProperties.Jwt("issuer", "gateway-sample-api", "sensitive-signing-key", Duration.ofHours(1));
        var user = new SecurityProperties.DemoUser(true, "demo", "sensitive-password");
        assertThat(jwt.toString()).doesNotContain("sensitive-signing-key");
        assertThat(user.toString()).doesNotContain("sensitive-password");
    }

    private final Validator validator = Validation
            .buildDefaultValidatorFactory()
            .getValidator();

    @Test
    void acceptsValidSecuritySettings() {
        SecurityProperties properties = new SecurityProperties(
                new SecurityProperties.Jwt(
                        "local-gateway",
                        "gateway-sample-api",
                        "01234567890123456789012345678901",
                        Duration.ofHours(1)),
                new SecurityProperties.DemoUser(
                        true,
                        "demo",
                        "0123456789ab"),
                new SecurityProperties.Cors(
                        List.of("http://localhost:3000"),
                        Duration.ofHours(1)));

        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    void rejectsShortJwtSecret() {
        SecurityProperties properties = new SecurityProperties(
                new SecurityProperties.Jwt(
                        "local-gateway",
                        "gateway-sample-api",
                        "too-short",
                        Duration.ofHours(1)),
                new SecurityProperties.DemoUser(false, "", ""),
                new SecurityProperties.Cors(
                        List.of("http://localhost:3000"),
                        Duration.ofHours(1)));

        assertThat(validator.validate(properties))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("jwt.secret"));
    }

    @Test
    void rejectsBlankAudience() {
        var jwt = new SecurityProperties.Jwt("issuer", "", "01234567890123456789012345678901",
                Duration.ofHours(1));
        assertThat(validator.validate(jwt))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("audience"));
    }

    @Test
    void permitsMissingDemoCredentialsWhenIssuerIsDisabled() {
        SecurityProperties.DemoUser demoUser =
                new SecurityProperties.DemoUser(false, "", "");

        assertThat(demoUser.enabled()).isFalse();
    }

    @Test
    void rejectsMissingDemoCredentialsWhenIssuerIsEnabled() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SecurityProperties.DemoUser(true, "", "short"));
    }

    @Test
    void rejectsNonPositiveTokenTtl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SecurityProperties.Jwt(
                        "local-gateway",
                        "gateway-sample-api",
                        "01234567890123456789012345678901",
                        Duration.ZERO));
    }

    @Test
    void rejectsEmptyCorsOrigins() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SecurityProperties.Cors(
                        List.of(),
                        Duration.ofHours(1)));
    }
}

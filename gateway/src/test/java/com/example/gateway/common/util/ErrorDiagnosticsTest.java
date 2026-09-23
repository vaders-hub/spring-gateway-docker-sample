package com.example.gateway.common.util;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ErrorDiagnosticsTest {
    @Test
    void diagnosticCausesAreBoundedAndNeverContainExceptionMessages() {
        var root = new IllegalArgumentException("secret-root-message");
        var error = new IllegalStateException("secret-outer-message", root);
        root.initCause(error);
        assertThat(ErrorDiagnostics.causes(error)).hasSize(2);
        assertThat(ErrorDiagnostics.causes(error).toString())
                .contains("IllegalArgumentException", "IllegalStateException", " at ")
                .doesNotContain("secret-root-message", "secret-outer-message");
    }
}

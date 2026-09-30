package com.example.platform.security.token;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TokenKeyTest {
    @Test
    void sharedStorageContractUsesSha256WithoutRawToken() {
        assertThat(TokenKey.of("abc")).isEqualTo(
                "auth:active:v1:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}

package com.example.backend.common.security.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 두 서비스의 저장 계약: 토큰 원문 대신 SHA-256 식별자만 Redis key로 사용한다. */
public final class TokenKey {
    private TokenKey() {}
    public static String of(String token) {
        try {
            return "auth:active:v1:" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}

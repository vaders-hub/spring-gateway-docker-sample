package com.example.gateway.common.security.token;

/** 저장소 장애를 잘못된 인증(401)과 구분한다. 원문 Redis 오류/토큰을 응답에 노출하지 않는다. */
public class TokenStoreUnavailableException extends RuntimeException {
    public TokenStoreUnavailableException() { super("Authentication store unavailable"); }
    public static boolean causedBy(Throwable exception) {
        for (int i = 0; exception != null && i < 10; i++, exception = exception.getCause()) {
            if (exception instanceof TokenStoreUnavailableException) return true;
        }
        return false;
    }
}

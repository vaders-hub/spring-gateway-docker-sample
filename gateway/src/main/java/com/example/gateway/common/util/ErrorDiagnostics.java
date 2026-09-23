package com.example.gateway.common.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

public final class ErrorDiagnostics {
    private ErrorDiagnostics() {}

    // 원문 메시지/인자/전체 stack 대신 각 cause의 타입과 발생 위치만 기록한다.
    // 순환 cause와 과도한 중첩도 제한한다. DEBUG라도 예외 메시지는 비밀값을 포함할 수 있다.
    public static List<String> causes(Throwable error) {
        var result = new ArrayList<String>();
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        for (Throwable cause = error; cause != null && result.size() < 8 && seen.add(cause);
                cause = cause.getCause()) {
            var stack = cause.getStackTrace();
            result.add(cause.getClass().getName() + (stack.length == 0 ? "" : " at " + stack[0]));
        }
        return List.copyOf(result);
    }
}

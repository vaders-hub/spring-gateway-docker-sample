package com.example.backend.common.security.permission;

import java.lang.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

/** 반복 권한 표현식의 단일 정의. model과 repository 인터페이스에는 보안 프레임워크를 넣지 않는다. */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAuthority('SCOPE_api.read')")
public @interface RequireRead { }

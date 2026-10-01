package com.example.backend.member.web;

import java.lang.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

/** 회원 관리 권한은 member feature가 소유하며 기본 읽기 권한도 함께 요구한다. */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAuthority('SCOPE_api.read') and hasAuthority('SCOPE_member.admin')")
public @interface RequireMemberAdmin { }

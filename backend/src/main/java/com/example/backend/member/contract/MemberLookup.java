package com.example.backend.member.contract;
/** 존재 여부만 반환한다. 요청에서 부재가 의미하는 HTTP 상태는 호출자가 결정한다. */
public interface MemberLookup { boolean exists(long memberId); }
